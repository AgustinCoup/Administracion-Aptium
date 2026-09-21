package com.example.ui.common;

import com.example.common.exception.ValidationException;
import com.example.infrastructure.db.ConexionesSupervisadas;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TokenTarea;

import java.awt.EventQueue;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Único mecanismo de la aplicación para hacer trabajo fuera del hilo de la interfaz.
 *
 * <p>Separa explícitamente las dos mitades de toda operación de fondo: qué se lee
 * (fuera del hilo de UI) y qué se pinta con lo leído (en el hilo de UI). Reemplaza al
 * patrón de {@code new Thread(...)} + {@code invokeLater} copiado por la app, que era
 * fácil de escribir mal y de escribir a medias — sobre todo omitiendo el manejo de error.
 *
 * <pre>{@code
 * TareaUI.<DatosOperativos>nueva()
 *     .nombre("refresco-pantallas")
 *     .leer(lector::leer)                        // fuera del hilo de UI
 *     .pintar(datos -> aplicar(datos))           // en el hilo de UI
 *     .siFalla(e -> panel.mostrarError(e))       // en el hilo de UI
 *     .antes(()  -> panel.mostrarCargando(true))
 *     .despues(() -> panel.mostrarCargando(false))
 *     .lanzar();
 * }</pre>
 *
 * <p><b>Los errores nunca se pierden:</b> cualquier excepción de {@code leer} (o de
 * {@code pintar}) se loguea aunque no se haya declarado {@code siFalla}, y se rutea al
 * manejador en el hilo de UI. {@code pintar} no se ejecuta si hubo fallo. Una
 * {@link ValidationException} va a <b>WARN sin stack</b> (es una regla de negocio, no un fallo);
 * todo lo demás va a ERROR con la traza completa.
 *
 * <p><b>Cancelación:</b> {@link #lanzar()} devuelve una {@link Ejecucion} cancelable.
 * Cancelar hace dos cosas: descarta el resultado —una tarea cancelada no ejecuta
 * {@code pintar}, {@code siFalla} ni {@code despues}, así que quien cancela es responsable
 * de lo que quede en pantalla, típicamente porque lanza otra tarea en su lugar— <b>y además
 * cancela en el servidor las consultas que esa tarea tenga en vuelo</b>
 * ({@link ConexionesSupervisadas#cancelarDe}). Sin lo segundo, mantener F5 apretado dejaba N
 * consultas corriendo en MySQL, cada una reteniendo una conexión del pool hasta terminar. Y como
 * el {@code KILL} sólo alcanza a lo que ya está en vuelo, cancelar prende también la marca del
 * {@link TokenTarea}: la tarea que esperaba permiso, o que iba por la segunda de varias consultas,
 * no ejecuta ninguna más.
 *
 * <p>Lo que sigue sin ser confiable es interrumpir el <i>hilo</i> — por eso el
 * {@code worker.cancel(false)}. Lo confiable es {@code Statement.cancel()}, que en Connector/J
 * manda un {@code KILL QUERY}.
 *
 * <p>⚠️ Ese {@code cancel()} <b>abre una conexión nueva</b> para mandar el {@code KILL}: es I/O, y
 * {@link Ejecucion#cancelar()} se llama desde el hilo de la interfaz
 * ({@code RefrescadorPantallas.refrescarAhora()}). Por eso se despacha al ejecutor daemon
 * {@code cancelador-sql}, que es la <b>segunda</b> excepción de la app a "no hay {@code new Thread()}
 * fuera de {@code TareaUI}" —la primera es el shutdown hook de {@code App}—. Un hilo alcanza:
 * cancelar es raro y rápido.
 *
 * @param <T> tipo del resultado que viaja de {@code leer} a {@code pintar}
 */
public final class TareaUI<T> {

    private static final Logger log = LoggerFactory.getLogger(TareaUI.class);

    private static final String NOMBRE_POR_DEFECTO = "tarea-ui";

    /**
     * Único hilo donde corre {@code Statement.cancel()}. Ver el javadoc de la clase: ese cancel
     * hace I/O y {@code cancelar()} se llama desde el hilo de la interfaz. Daemon para que no
     * impida el cierre de la aplicación.
     */
    private static final ExecutorService CANCELADOR = Executors.newSingleThreadExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "cancelador-sql");
        hilo.setDaemon(true);
        return hilo;
    });

    /** Handle de una tarea lanzada. */
    public interface Ejecucion {
        /** Descarta el resultado de la tarea; nada se pintará. Idempotente. */
        void cancelar();

        boolean estaCancelada();
    }

    private Callable<T>         leer;
    private Consumer<T>         pintar   = resultado -> { };
    private Consumer<Throwable> siFalla;
    private Runnable            antes    = () -> { };
    private Runnable            despues  = () -> { };
    private String              nombre   = NOMBRE_POR_DEFECTO;

    private TareaUI() { }

    public static <T> TareaUI<T> nueva() {
        return new TareaUI<>();
    }

    /** Nombre del hilo mientras corre {@code leer}, para que los logs sean legibles. */
    public TareaUI<T> nombre(String nombre) {
        this.nombre = Objects.requireNonNull(nombre, "nombre no puede ser nulo");
        return this;
    }

    /** Trabajo pesado (BD, archivos). Corre fuera del hilo de UI. Obligatorio. */
    public TareaUI<T> leer(Callable<T> leer) {
        this.leer = Objects.requireNonNull(leer, "leer no puede ser nulo");
        return this;
    }

    /** Vuelca el resultado en la pantalla. Corre en el hilo de UI. */
    public TareaUI<T> pintar(Consumer<T> pintar) {
        this.pintar = Objects.requireNonNull(pintar, "pintar no puede ser nulo");
        return this;
    }

    /** Manejo de error de cara al usuario. Corre en el hilo de UI. */
    public TareaUI<T> siFalla(Consumer<Throwable> siFalla) {
        this.siFalla = Objects.requireNonNull(siFalla, "siFalla no puede ser nulo");
        return this;
    }

    /** Corre en el hilo de UI antes de arrancar (típicamente mostrar el indicador de carga). */
    public TareaUI<T> antes(Runnable antes) {
        this.antes = Objects.requireNonNull(antes, "antes no puede ser nulo");
        return this;
    }

    /** Corre en el hilo de UI al terminar, tanto en éxito como en error. */
    public TareaUI<T> despues(Runnable despues) {
        this.despues = Objects.requireNonNull(despues, "despues no puede ser nulo");
        return this;
    }

    /**
     * Arranca la tarea.
     *
     * @return handle para cancelarla
     * @throws IllegalStateException si no se declaró {@code leer}
     */
    public Ejecucion lanzar() {
        if (leer == null) {
            throw new IllegalStateException("Una TareaUI necesita un leer(...)");
        }

        Handle handle = new Handle();
        SwingWorker<T, Void> worker = crearWorker(handle);
        handle.asociar(worker);

        enHiloUi(antes);
        worker.execute();
        return handle;
    }

    private SwingWorker<T, Void> crearWorker(Handle handle) {
        return new SwingWorker<>() {

            @Override
            protected T doInBackground() throws Exception {
                Thread hilo = Thread.currentThread();
                String nombreOriginal = hilo.getName();
                hilo.setName(nombre);
                TokenTarea token = handle.token();
                TokenTarea.asociarAlHiloActual(token);
                long inicio = System.nanoTime();
                try {
                    return leer.call();
                } finally {
                    log.info("Tarea '{}' leyó en {} ms", nombre, milisegundosDesde(inicio));
                    if (ConnectionPool.hayPresion()) {
                        log.warn("Pool bajo presión tras la tarea '{}': {}", nombre, ConnectionPool.getStats());
                    }
                    // El hilo vuelve al pool de SwingWorker y lo hereda otra tarea: sacar el token
                    // y olvidar el registro es lo que impide que cancelar ESTA tarea, ya terminada,
                    // mate la consulta viva de la que venga después.
                    TokenTarea.desasociarDelHiloActual();
                    ConexionesSupervisadas.olvidar(token);
                    hilo.setName(nombreOriginal);
                }
            }

            @Override
            protected void done() {
                if (handle.estaCancelada()) {
                    return;
                }
                try {
                    T resultado = get();
                    long inicio = System.nanoTime();
                    pintar.accept(resultado);
                    log.info("Tarea '{}' pintó en {} ms", nombre, milisegundosDesde(inicio));
                } catch (CancellationException e) {
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    manejarFallo(e);
                } catch (ExecutionException e) {
                    manejarFallo(e.getCause() != null ? e.getCause() : e);
                } catch (RuntimeException e) {
                    manejarFallo(e);
                } finally {
                    despues.run();
                }
            }
        };
    }

    private void manejarFallo(Throwable causa) {
        registrarFallo(causa);
        if (siFalla == null) {
            return;
        }
        try {
            siFalla.accept(causa);
        } catch (RuntimeException e) {
            log.error("El manejador de error de la tarea '{}' también falló", nombre, e);
        }
    }

    /**
     * Una {@link ValidationException} es una regla de negocio que el usuario violó —el código de
     * catálogo no existe, falta un campo—, no un fallo del sistema: se loguea a WARN y sin stack,
     * porque el stack no aporta nada y ensucia el log de errores de producción con casos normales.
     * Cualquier otra causa sí es un fallo y va a ERROR con la traza completa.
     */
    private void registrarFallo(Throwable causa) {
        if (causa instanceof ValidationException) {
            log.warn("Validación rechazada en la tarea '{}': {}", nombre, causa.getMessage());
            return;
        }
        log.error("Fallo en la tarea de fondo '{}'", nombre, causa);
    }

    private static long milisegundosDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    private static void enHiloUi(Runnable accion) {
        if (EventQueue.isDispatchThread()) {
            accion.run();
        } else {
            EventQueue.invokeLater(accion);
        }
    }

    private final class Handle implements Ejecucion {

        private final AtomicBoolean cancelada = new AtomicBoolean(false);
        /** Creado en el constructor, no en {@code doInBackground}: se puede cancelar antes de arrancar. */
        private final TokenTarea token = TokenTarea.nuevo(nombre);
        private volatile SwingWorker<?, ?> worker;

        TokenTarea token() {
            return token;
        }

        void asociar(SwingWorker<?, ?> worker) {
            this.worker = worker;
        }

        @Override
        public void cancelar() {
            cancelada.set(true);
            // Sincrónico y ANTES de despachar el cancelarDe: la marca no hace I/O, y prenderla
            // antes de recorrer el registro es la mitad de la carrera que cierra (ver TokenTarea).
            token.marcarCancelado();
            SwingWorker<?, ?> actual = worker;
            if (actual != null) {
                // Sin interrumpir: interrumpir una query JDBC en curso no es confiable.
                // Esto solo evita que arranque si todavía estaba encolada.
                actual.cancel(false);
            }
            // Lo que sí es confiable: cancelar la sentencia en el servidor. Fuera del hilo de UI
            // porque cancel() abre una conexión para mandar el KILL QUERY — ver el javadoc de la
            // clase. Si la tarea ya terminó, el token no tiene sentencias y esto no hace nada.
            CANCELADOR.execute(() -> ConexionesSupervisadas.cancelarDe(token));
        }

        @Override
        public boolean estaCancelada() {
            return cancelada.get();
        }
    }
}
