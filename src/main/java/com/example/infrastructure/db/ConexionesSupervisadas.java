package com.example.infrastructure.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envuelve las conexiones que entrega {@link ConnectionPool} para que toda consulta tenga techo de
 * tiempo y para que una tarea cancelada se cancele <b>también en el servidor</b>.
 *
 * <p><b>Por qué acá y no en cada DAO.</b> {@code ConnectionPool.getConnection()} es el único cuello
 * por el que pasa todo el JDBC de la aplicación. Poner {@code setQueryTimeout} y el registro de
 * sentencias en cada DAO son ~70 sitios que se olvidan solos: el DAO nuevo que alguien escriba
 * dentro de un año no va a acordarse. Acá no hay nada que acordarse.
 *
 * <p><b>Dos rutas quedan afuera a propósito:</b> Flyway (usa {@link ConnectionPool#getDataSource()},
 * no {@code getConnection()}) y la creación de la base ({@code DriverManager} directo en el arranque).
 * Que Flyway quede exento es lo correcto: un {@code CREATE INDEX} sobre una tabla grande puede tardar
 * más que el techo de consulta, y matarlo a mitad dejaría la migración envenenada.
 *
 * <p><b>Por qué un {@link Proxy} y no delegación a mano.</b> {@code java.sql.Connection} tiene ~50
 * métodos; escribirlos todos es ruido puro donde se esconden los errores. El proxy despacha
 * <em>por nombre de método</em>, así que cubre de una todos los overloads de
 * {@code prepareStatement} / {@code createStatement} y además {@code prepareCall}.
 *
 * <h2>El techo de consulta sólo va en conexiones con {@code autoCommit = true}</h2>
 *
 * Las guardas de concurrencia del lavadero ({@code CicloLavaderoDAO.SQL_BLOQUEAR_LINEA},
 * {@code SQL_CICLO_ACTIVO_DE_LAVARROPAS}, {@code SalidaLavaderoDAO.bloquearAfectados}) son
 * {@code SELECT … FOR UPDATE} que <b>bloquean y esperan por diseño</b>, y el
 * {@code innodb_lock_wait_timeout} del servidor es 50 s. Un {@code queryTimeout} de 30 s le ganaría
 * <em>siempre</em> y devolvería {@code ER_QUERY_INTERRUPTED} (1317), que
 * {@code ControlConcurrencia.esContencionDeLock} no reconoce (mapea 1213, 1205 y 50200): el choque
 * entre dos operadores dejaría de salir como "alguien se te adelantó" y saldría como error técnico,
 * y la rama del 1205 quedaría muerta en producción.
 *
 * <p>{@link TransactionalConnection} pone {@code autoCommit = false} en su constructor, así que la
 * condición «¿está en autocommit?» es exacta y no hace falta ningún flag nuevo. Las escrituras
 * quedan acotadas por {@code socketTimeout} (60 s), que es <b>mayor</b> que los 50 s del lock wait:
 * el 1205 sigue llegando.
 *
 * <p>⚠️ <b>En H2 2.x {@code setQueryTimeout} es a nivel SESIÓN</b>, no de {@code Statement}: emite
 * un {@code SET QUERY_TIMEOUT} sobre la conexión y sobrevive al cierre de la sentencia. Acá es
 * inocuo porque el valor es siempre el mismo, pero explica dos cosas que si no se depuran dos veces:
 * un test tiene que leer el valor de vuelta con {@code getQueryTimeout()} en lugar de asumirlo, y
 * tiene que hacerlo sobre una conexión nueva, porque una conexión reciclada del pool puede arrastrar
 * el timeout que le puso una lectura anterior. En MySQL el timeout es por sentencia y no se arrastra.
 *
 * <h2>Cancelación</h2>
 *
 * El registro es {@code Map<TokenTarea, Set<Statement>>}. Es por <b>token de tarea</b> y no por hilo
 * — ver {@link TokenTarea} — y el valor es un {@code Set} y no una sentencia sola porque un DAO
 * transaccional tiene varios {@code PreparedStatement} abiertos a la vez sobre la misma conexión, y
 * un hilo puede tener dos conexiones: con un solo slot el segundo pisaría al primero y el
 * {@code close()} del interno desregistraría al externo.
 *
 * <p>{@link #cancelarDe(TokenTarea)} <b>no lanza</b>. Cancelar es best-effort por definición: la
 * sentencia puede haber terminado, haberse cerrado, o el driver puede no soportar {@code cancel()}.
 * Cualquiera de esas tres es el resultado buscado, no un fallo.
 *
 * <p>⚠️ {@code Statement.cancel()} en Connector/J <b>abre una conexión nueva</b> para mandar el
 * {@code KILL QUERY}: es I/O y no puede correr en el hilo de la interfaz. Quien cancela
 * ({@code TareaUI.Handle}) lo despacha al ejecutor {@code cancelador-sql}.
 *
 * <p><b>Lo que {@code cancelarDe} no alcanza lo cubre la marca del token.</b> Una sentencia que
 * todavía no existe —la tarea espera permiso, o está entre dos consultas— no está en el registro.
 * Por eso toda sentencia nueva mira {@link TokenTarea#estaCancelado()} <em>después</em> de
 * registrarse, y toda {@code execute*} la vuelve a mirar antes de ejecutar; si está prendida lanza
 * {@link TareaCanceladaException} sin tocar el servidor. Queda una ventana de microsegundos, entre
 * esa última mirada y que el driver marque la sentencia como en ejecución, que sólo el techo de
 * consulta cubre: cerrarla exigiría la colaboración del driver.
 *
 * <h2>Invariante del que depende el techo de concurrencia</h2>
 *
 * <b>Ninguna operación mantiene dos conexiones abiertas a la vez.</b> Hoy se cumple:
 * {@code HistorialLavaderoDAO.obtenerHistorial()} toma cuatro conexiones, pero
 * <em>secuencialmente</em>. Si alguna vez se anidaran dos, el semáforo de
 * {@link ConnectionPool#getConnection()} dejaría pasar 5 operaciones × 2 conexiones = 10 &gt; 8 y el
 * pool se agotaría <em>con el techo puesto</em>.
 */
public final class ConexionesSupervisadas {

    private static final Logger log = LoggerFactory.getLogger(ConexionesSupervisadas.class);

    /** Sentencias vivas por tarea. Ver el javadoc de la clase: por token, y un {@code Set}. */
    private static final ConcurrentHashMap<TokenTarea, Set<Statement>> REGISTRO = new ConcurrentHashMap<>();

    private ConexionesSupervisadas() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

    /**
     * Devuelve una vista supervisada de {@code real}: pone el techo de consulta a las sentencias de
     * conexiones en autocommit y las registra contra la tarea vigente para poder cancelarlas.
     *
     * @param real     la conexión del pool (o del {@code DataSource} de test)
     * @param alCerrar se ejecuta exactamente una vez, cuando se cierra la conexión envuelta, haya
     *                 fallado o no el cierre real. Es por donde {@link ConnectionPool} devuelve el
     *                 permiso del semáforo
     */
    public static Connection envolver(Connection real, Runnable alCerrar) {
        ManejadorConexion manejador = new ManejadorConexion(real, alCerrar);
        Connection proxy = (Connection) Proxy.newProxyInstance(
            ConexionesSupervisadas.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            manejador);
        manejador.asociarProxy(proxy);
        return proxy;
    }

    /**
     * Cancela en el servidor todas las sentencias vivas de esa tarea. No lanza: ver el javadoc de
     * la clase. Un token sin sentencias —porque la tarea ya terminó, o porque nunca tocó la base—
     * no hace nada, que es justamente lo que impide que cancelar una tarea muerta mate la consulta
     * de otra.
     */
    public static void cancelarDe(TokenTarea token) {
        if (token == null) {
            return;
        }
        Set<Statement> vivas = REGISTRO.get(token);
        if (vivas == null || vivas.isEmpty()) {
            return;
        }
        for (Statement sentencia : vivas) {
            try {
                sentencia.cancel();
            } catch (SQLException | RuntimeException e) {
                log.debug("No se pudo cancelar una sentencia de la tarea '{}' (ya terminó, "
                    + "ya se cerró, o el driver no lo soporta)", token, e);
            }
        }
    }

    /**
     * Olvida la tarea entera. Lo llama {@code TareaUI} al sacar el token del hilo: sin esto el
     * registro retendría tokens muertos, y un {@code Statement} que por algún motivo no se cerró
     * mantendría viva la entrada para siempre.
     */
    public static void olvidar(TokenTarea token) {
        if (token != null) {
            REGISTRO.remove(token);
        }
    }

    /** Cuántas tareas tienen sentencias vivas. Sólo para tests y diagnóstico. */
    static int tareasRegistradas() {
        return REGISTRO.size();
    }

    private static void registrar(TokenTarea token, Statement sentencia) {
        REGISTRO.computeIfAbsent(token, t -> ConcurrentHashMap.newKeySet()).add(sentencia);
    }

    private static void desregistrar(TokenTarea token, Statement sentencia) {
        if (token == null) {
            return;
        }
        REGISTRO.computeIfPresent(token, (t, vivas) -> {
            vivas.remove(sentencia);
            return vivas.isEmpty() ? null : vivas;
        });
    }

    /**
     * Despacha por nombre de método. Los cuatro casos especiales ({@code equals}, {@code hashCode},
     * {@code toString}, {@code unwrap}) están acá porque un proxy que los delega ciegamente se
     * comporta mal: {@code Objects.equals(conn, conn)} llamaría al {@code equals} del real con el
     * proxy como argumento, y {@code unwrap} devolvería la conexión cruda, sin supervisión.
     */
    private static final class ManejadorConexion implements InvocationHandler {

        private final Connection real;
        private final Runnable alCerrar;
        /** Las sentencias que abrió <b>esta</b> conexión, con el token bajo el que se registraron. */
        private final Map<Statement, TokenTarea> propias = new ConcurrentHashMap<>();
        private final AtomicBoolean cerrada = new AtomicBoolean(false);

        private volatile Connection proxy;

        ManejadorConexion(Connection real, Runnable alCerrar) {
            this.real = real;
            this.alCerrar = alCerrar;
        }

        void asociarProxy(Connection proxy) {
            this.proxy = proxy;
        }

        @Override
        public Object invoke(Object instancia, Method metodo, Object[] args) throws Throwable {
            switch (metodo.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "ConexionSupervisada[" + real + "]";
                case "unwrap":
                    return desenvolver((Class<?>) args[0]);
                case "isWrapperFor":
                    return ((Class<?>) args[0]).isInstance(proxy);
                case "close":
                    cerrar();
                    return null;
                case "prepareStatement":
                case "createStatement":
                case "prepareCall":
                    return supervisar((Statement) invocar(metodo, args));
                default:
                    return invocar(metodo, args);
            }
        }

        /**
         * Devuelve el <b>proxy</b>, nunca el real: {@code unwrap} es la puerta trasera por la que se
         * escaparía una conexión sin techo de consulta y sin registro de cancelación.
         */
        private Object desenvolver(Class<?> interfaz) throws SQLException {
            if (interfaz.isInstance(proxy)) {
                return proxy;
            }
            throw new SQLException("Una conexión supervisada no se desenvuelve a " + interfaz.getName()
                + ": eso saltearía el techo de consulta y el registro de cancelación");
        }

        private Statement supervisar(Statement sentencia) throws SQLException {
            // Anti-patrón A12: sólo en autocommit. Ver el javadoc de la clase.
            if (real.getAutoCommit()) {
                sentencia.setQueryTimeout(ConnectionPool.TIMEOUT_CONSULTA_S);
            }
            TokenTarea token = TokenTarea.vigente();
            if (token != null) {
                registrar(token, sentencia);
                propias.put(sentencia, token);
                // Registrar y DESPUÉS mirar la marca: es el orden inverso al de TareaUI.cancelar()
                // (marca y después recorre el registro), y es lo que cierra la carrera. Ver TokenTarea.
                if (token.estaCancelado()) {
                    propias.remove(sentencia);
                    desregistrar(token, sentencia);
                    cerrarEnSilencio(sentencia);
                    token.exigirNoCancelado("antes de abrir una sentencia");
                }
            }
            return envolverSentencia(sentencia, token);
        }

        private void cerrarEnSilencio(Statement sentencia) {
            try {
                sentencia.close();
            } catch (SQLException | RuntimeException e) {
                log.debug("No se pudo cerrar una sentencia de una tarea cancelada", e);
            }
        }

        private Statement envolverSentencia(Statement sentencia, TokenTarea token) {
            Class<?> interfaz = sentencia instanceof CallableStatement ? CallableStatement.class
                              : sentencia instanceof PreparedStatement ? PreparedStatement.class
                              : Statement.class;
            return (Statement) Proxy.newProxyInstance(
                ConexionesSupervisadas.class.getClassLoader(),
                new Class<?>[] { interfaz },
                new ManejadorSentencia(sentencia, token));
        }

        /**
         * Cierra una sola vez ({@code close()} de JDBC es idempotente) y devuelve el permiso del
         * semáforo pase lo que pase con el cierre real: un permiso perdido no se recupera nunca.
         */
        private void cerrar() throws SQLException {
            if (!cerrada.compareAndSet(false, true)) {
                return;
            }
            try {
                desregistrarPropias();
                real.close();
            } finally {
                alCerrar.run();
            }
        }

        /**
         * Sólo las sentencias de esta conexión, no todas las del token: un hilo puede tener dos
         * conexiones abiertas y cerrar una no puede desregistrar las sentencias de la otra.
         */
        private void desregistrarPropias() {
            propias.forEach((sentencia, token) -> desregistrar(token, sentencia));
            propias.clear();
        }

        private Object invocar(Method metodo, Object[] args) throws Throwable {
            try {
                return metodo.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        /** Existe sólo para desregistrar en {@code close()} y para no filtrar la conexión cruda. */
        private final class ManejadorSentencia implements InvocationHandler {

            private final Statement sentencia;
            private final TokenTarea token;

            ManejadorSentencia(Statement sentencia, TokenTarea token) {
                this.sentencia = sentencia;
                this.token = token;
            }

            @Override
            public Object invoke(Object instancia, Method metodo, Object[] args) throws Throwable {
                switch (metodo.getName()) {
                    case "equals":
                        return instancia == args[0];
                    case "hashCode":
                        return System.identityHashCode(instancia);
                    case "toString":
                        return "SentenciaSupervisada[" + sentencia + "]";
                    case "unwrap":
                        return ((Class<?>) args[0]).isInstance(instancia) ? instancia : desenvolverFalla(args[0]);
                    case "isWrapperFor":
                        return ((Class<?>) args[0]).isInstance(instancia);
                    case "getConnection":
                        return proxy;
                    case "close":
                        propias.remove(sentencia);
                        ConexionesSupervisadas.desregistrar(token, sentencia);
                        return invocarSentencia(metodo, args);
                    default:
                        if (token != null && metodo.getName().startsWith("execute")) {
                            // Connector/J ignora cancel() sobre una sentencia preparada que todavía
                            // no se está ejecutando: sin esto, una cancelación entre prepare y
                            // execute dejaría correr la consulta entera.
                            token.exigirNoCancelado("antes de ejecutar una sentencia");
                        }
                        return invocarSentencia(metodo, args);
                }
            }

            private Object desenvolverFalla(Object interfaz) throws SQLException {
                throw new SQLException("Una sentencia supervisada no se desenvuelve a "
                    + ((Class<?>) interfaz).getName());
            }

            private Object invocarSentencia(Method metodo, Object[] args) throws Throwable {
                try {
                    return metodo.invoke(sentencia, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
            }
        }
    }
}
