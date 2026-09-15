package com.example.infrastructure.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Connection Pool usando HikariCP para gestión eficiente de conexiones.
 * 
 * VENTAJAS sobre DriverManager.getConnection():
 * - Conexiones pre-establecidas (0ms overhead vs 50-100ms)
 * - Reutilización automática de conexiones
 * - Límite de conexiones concurrentes configurable
 * - Detección y cierre de conexiones perdidas (leak detection)
 * - Validación automática de conexiones antes de usar
 * 
 * PATRÓN SINGLETON: Una sola instancia del pool para toda la aplicación.
 * Thread-safe: HikariCP maneja concurrencia internamente.
 */
public class ConnectionPool {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);

    private static HikariDataSource dataSource;
    private static final Properties PROPS = new Properties();

    /**
     * Parámetros comunes de la URL JDBC, en una sola constante para que las dos URLs que
     * arma esta clase (la de {@code crearBaseDeDatosSiNoExiste}, sin base, y la del pool)
     * no puedan divergir: agregar un parámetro de seguridad a una sola de las dos deja la
     * otra conexión —que también lleva las credenciales— sin la garantía.
     *
     * <p>{@code sslMode=REQUIRED} obliga a que la conexión viaje cifrada. El default de
     * Connector/J 8.x es {@code PREFERRED}, que cifra si el servidor ofrece TLS pero cae a
     * texto plano <em>sin avisar</em> si no: las credenciales y todos los datos cruzarían la
     * red en claro y nada en la app lo delataría. Hoy el túnel de Tailscale ya cifra, pero
     * eso es otra capa, y el propio runbook de conexión remota admite que la regla de
     * firewall no persiste entre migraciones de servidor — esta garantía no depende de que
     * aquélla siga en pie.
     *
     * <p>No es {@code VERIFY_CA} porque no hay CA propia: {@code REQUIRED} cifra pero no
     * valida el certificado del servidor. Subirlo a {@code VERIFY_CA} exige distribuir un
     * truststore a cada puesto, y es la decisión siguiente, no ésta.
     *
     * <p>{@code connectTimeout} y {@code socketTimeout} importan especialmente porque la base es
     * remota sobre Tailscale. El default de Connector/J para los dos es {@code 0} = infinito: si
     * el túnel se corta a mitad de una lectura, el driver queda bloqueado en {@code read()} para
     * siempre y esa conexión <em>nunca vuelve al pool</em>. Con unas pocas así el pool se agota y
     * la app queda muerta hasta reiniciar, sin que haya ninguna consulta pesada de por medio.
     */
    static final String PARAMS_JDBC =
        "serverTimezone=UTC&connectionTimeZone=LOCAL&sslMode=REQUIRED"
        + "&connectTimeout=" + ConnectionPool.CONNECT_TIMEOUT_MS
        + "&socketTimeout="  + ConnectionPool.SOCKET_TIMEOUT_MS;

    /**
     * 5 s para establecer el socket (incluye el handshake TLS de {@code sslMode=REQUIRED}).
     * <b>Estrictamente menor que {@link #CONNECTION_TIMEOUT_MS}, con margen:</b> con
     * {@code minimumIdle} bajo muchos checkouts crean una conexión física nueva, y si los dos
     * números fueran iguales un handshake lento saldría como "Connection is not available,
     * request timed out" con el pool vacío en vez de como el error de red que es.
     * Atado por {@code ConnectionPoolTest}.
     */
    static final int CONNECT_TIMEOUT_MS = 5_000;

    /**
     * 60 s sin recibir un byte del servidor ⇒ el cliente abandona la conexión. Es el
     * <b>respaldo de red</b>, NO el techo de la consulta: sólo libera el lado del cliente, la
     * consulta sigue corriendo en MySQL. Tiene que ser <b>mayor</b> que
     * {@link #TIMEOUT_CONSULTA_S}; al revés mataría consultas legítimas antes de que el
     * mecanismo que sabe cancelarlas del lado del servidor llegue a actuar.
     * Atado por {@code ConnectionPoolTest}.
     */
    static final int SOCKET_TIMEOUT_MS = 60_000;

    /**
     * Techo de consulta ({@code Statement.setQueryTimeout}), menor que {@link #SOCKET_TIMEOUT_MS}.
     * Todavía no lo aplica nadie: lo usa el Paso 4 de {@code plans/conexiones-y-paginacion.md}.
     * Vive acá desde ahora para que la relación con {@code socketTimeout} quede atada por test
     * desde el primer despliegue.
     */
    static final int TIMEOUT_CONSULTA_S = 30;

    /**
     * Cuánto espera quien pide una conexión con el pool lleno. Bajó de 30 s a 10 s porque los
     * autocompletados sincrónicos piden conexión <em>desde el EDT</em>: ese tiempo es app congelada.
     */
    static final int CONNECTION_TIMEOUT_MS = 10_000;

    /**
     * Falla del bloque {@code static}, guardada en vez de propagada. Propagada llegaba como
     * {@code ExceptionInInitializerError}: lleva la causa, pero es un {@code Error} y el
     * {@code catch (Exception)} de {@code App} no la agarra, y además el primer toque de la
     * clase era {@code getStats()}, que con el pool en null dejaba seguir hasta una NPE de
     * Flyway. {@link #verificarArranque()} la relanza con la causa original.
     *
     * <p>Deuda anotada: lo limpio sería inicializar desde un método explícito llamado por
     * {@code App.main}, pero eso cambia el orden de arranque de la app y de los tests
     * ({@code aptium.testing}, {@link #setDataSourceForTesting}); este cambio quiere ser
     * desplegable solo.
     */
    private static volatile Throwable fallaDeArranque = null;

    /** Solo para tests — null en producción. Establecer con {@link #setDataSourceForTesting}. */
    private static volatile javax.sql.DataSource testDataSource = null;

    /**
     * Solo para tests de integración con H2.
     * Debe llamarse ANTES de que cualquier DAO intente obtener una conexión.
     * Nunca llamar desde código productivo.
     */
    public static void setDataSourceForTesting(javax.sql.DataSource ds) {
        testDataSource = ds;
    }

    /**
     * Retorna el DataSource activo (test o producción). Usado por Flyway.
     *
     * @throws IllegalStateException con la causa original si el pool no pudo inicializarse
     */
    public static javax.sql.DataSource getDataSource() {
        javax.sql.DataSource override = testDataSource;
        if (override != null) {
            return override;
        }
        exigirArranqueSinFalla();
        return dataSource;
    }

    // Bloque estático: Se ejecuta UNA SOLA VEZ al cargar la clase.
    // Skipeado cuando la propiedad aptium.testing=true está activa (tests).
    // Throwable y no Exception: también un Error (driver ausente) tiene que llegar al diálogo.
    static {
        if (System.getProperty("aptium.testing") == null) {
            try {
                cargarConfiguracion();
                crearBaseDeDatosSiNoExiste(); // PRIMERO: Asegurar que la BD existe
                inicializarPool();            // SEGUNDO: Conectar al pool con la BD específica
            } catch (Throwable t) {
                fallaDeArranque = t;
            }
        }
    }

    /**
     * Relanza, con la causa original, la falla del bloque {@code static}. Tiene que ser la
     * <b>primera</b> sentencia del PASO 1/4 de {@code App.main}: es lo que carga la clase y lo
     * que impide que el arranque siga con el pool en null.
     *
     * @throws SQLException si el pool no pudo inicializarse; {@code getCause()} es la falla real
     */
    public static void verificarArranque() throws SQLException {
        if (fallaDeArranque != null) {
            throw new SQLException("El pool de conexiones no pudo inicializarse", fallaDeArranque);
        }
    }

    /** Versión sin checked de {@link #verificarArranque()}, para los métodos que no declaran SQLException. */
    private static void exigirArranqueSinFalla() {
        if (fallaDeArranque != null) {
            throw new IllegalStateException("El pool de conexiones no pudo inicializarse", fallaDeArranque);
        }
    }
    
    /**
     * Carga configuración con precedencia:
     * 1. VARIABLES DE ENTORNO (mayor prioridad - PRODUCCIÓN)
     * 2. config.properties externo (segundo nivel - STAGING)
     * 3. Valores por defecto (DESARROLLO SOLO)
     * 
     * VARIABLES DE ENTORNO SOPORTADAS:
     * - DB_HOST: IP/hostname del servidor MySQL (default: localhost)
     * - DB_PORT: Puerto MySQL (default: 3306)
     * - DB_NAME: Nombre de la base de datos (default: sistema_empresa)
     * - DB_USER: Usuario MySQL (default: root)
     * - DB_PASS: Contraseña MySQL (CRÍTICO: no debe ir en config.properties en PROD)
     * 
     * ARCHIVO config.properties:
     * - Se busca en: /etc/aptium/config.properties (Linux/Mac) o C:\Aptium\config.properties (Windows)
     * - O en el directorio actual / Administracion-Aptium
     * - NO debe commitearse al repositorio (agregar a .gitignore)
     */
    private static void cargarConfiguracion() {
        // PASO 1: Leer variables de entorno (mayor prioridad)
        String envDbHost = System.getenv("DB_HOST");
        String envDbPort = System.getenv("DB_PORT");
        String envDbName = System.getenv("DB_NAME");
        String envDbUser = System.getenv("DB_USER");
        String envDbPass = System.getenv("DB_PASS");
        
        if (envDbHost != null) {
            PROPS.setProperty("db.ip", envDbHost);
            log.info("DB_HOST cargado desde variable de entorno");
        }
        if (envDbPort != null) {
            PROPS.setProperty("db.port", envDbPort);
            log.info("DB_PORT cargado desde variable de entorno");
        }
        if (envDbName != null) {
            PROPS.setProperty("db.name", envDbName);
            log.info("DB_NAME cargado desde variable de entorno");
        }
        if (envDbUser != null) {
            PROPS.setProperty("db.user", envDbUser);
            log.info("DB_USER cargado desde variable de entorno");
        }
        if (envDbPass != null) {
            PROPS.setProperty("db.pass", envDbPass);
            log.info("DB_PASS cargado desde variable de entorno");
        }
        
        // PASO 2: Intenta cargar config.properties como complemento
        cargarConfiguracionDesdeArchivo();
        
        // PASO 3: Valores por defecto solo para desarrollo
        aplicarValoresPorDefecto();
        
        // PASO 4: Validar que tenemos al menos las credenciales mínimas
        validarConfiguracionCritica();
    }
    
    /**
     * Busca y carga config.properties desde múltiples ubicaciones.
     * Útil para desarrollo local sin variables de entorno.
     */
    private static void cargarConfiguracionDesdeArchivo() {
        // Rutas a buscar (en orden de preferencia)
        String[] rutasPosibles = {
            "/etc/aptium/config.properties",                    // Linux/Mac producción
            "C:\\Aptium\\config.properties",                    // Windows producción
            "config.properties",                                 // Raíz del proyecto
            "Administracion-Aptium/config.properties"          // Dentro del dir del proyecto
        };
        
        for (String ruta : rutasPosibles) {
            Path p = Paths.get(ruta);
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    Properties fileProps = new Properties();
                    fileProps.load(is);
                    
                    // Cargar solo si no estaban ya en variables de entorno
                    fileProps.forEach((key, value) -> {
                        if (!PROPS.containsKey(key)) {
                            PROPS.setProperty((String) key, (String) value);
                        }
                    });
                    
                    log.info("config.properties cargado desde: {}", p.toAbsolutePath());
                    return; // Éxito, no buscar más
                } catch (IOException e) {
                    log.debug("Error al cargar desde {}: {}", ruta, e.getMessage());
                }
            }
        }
        
        log.debug("config.properties no encontrado en ninguna ubicación esperada");
    }
    
    /**
     * Aplica valores por defecto SOLO si no existen.
     * Estos defaults son seguros solo para DESARROLLO local.
     */
    private static void aplicarValoresPorDefecto() {
        PROPS.putIfAbsent("db.ip", "localhost");
        PROPS.putIfAbsent("db.port", "3306");
        PROPS.putIfAbsent("db.name", "sistema_empresa");
        PROPS.putIfAbsent("db.user", "root");
        PROPS.putIfAbsent("db.pass", "root");  // Default SIN SEGURIDAD para DEV
    }
    
    /**
     * Valida que tenemos credenciales mínimas.
     * Falla de forma clara si faltan datos críticos en producción.
     */
    private static void validarConfiguracionCritica() {
        String dbIp = PROPS.getProperty("db.ip", "").trim();
        String dbUser = PROPS.getProperty("db.user", "").trim();
        String dbPass = PROPS.getProperty("db.pass", "").trim();
        String dbName = PROPS.getProperty("db.name", "").trim();
        
        if (dbIp.isEmpty() || dbUser.isEmpty() || dbName.isEmpty()) {
            String error = String.format(
                "CONFIGURACIÓN INCOMPLETA:\n" +
                "  db.ip=%s\n" +
                "  db.user=%s\n" +
                "  db.name=%s\n" +
                "Debe establecer variables de entorno: DB_HOST, DB_USER, DB_NAME\n" +
                "O crear config.properties en /etc/aptium/ o C:\\Aptium\\",
                dbIp, dbUser, dbName
            );
            log.error(error);
            throw new RuntimeException(error);
        }
        
        // Advertencia si usa credenciales por defecto (desarrollo)
        if ("localhost".equals(dbIp) && "root".equals(dbUser) && "root".equals(dbPass)) {
            log.warn("⚠️  USANDO CREDENCIALES DE DESARROLLO (localhost:root:root)");
            log.warn("⚠️  EN PRODUCCIÓN, ESTABLECE: DB_HOST, DB_USER, DB_PASS");
        }
    }
    
    /**
     * Crea la base de datos si no existe.
     * 
     * Este método se ejecuta ANTES de inicializar el pool.
     * Se conecta a MySQL SIN especificar base de datos, crea la BD si es necesaria,
     * y luego permite que el pool se conecte a la BD específica.
     * 
     * CRÍTICO: Debe ejecutarse antes de inicializarPool().
     */
    private static void crearBaseDeDatosSiNoExiste() {
        String dbIp = PROPS.getProperty("db.ip", "localhost");
        String dbPort = PROPS.getProperty("db.port", "3306");
        String dbName = PROPS.getProperty("db.name", "sistema_empresa");
        String dbUser = PROPS.getProperty("db.user", "root");
        String dbPass = PROPS.getProperty("db.pass", "root");
        
        if (!dbName.matches("[a-zA-Z0-9_]+")) {
            throw new RuntimeException(
                "Nombre de base de datos inválido: '" + dbName + "'. Solo se permiten letras, números y guiones bajos.");
        }

        // Conectar a MySQL SIN especificar base de datos
        String urlSinBD = "jdbc:mysql://" + dbIp + ":" + dbPort + "/?" + PARAMS_JDBC;

        try (Connection conn = java.sql.DriverManager.getConnection(urlSinBD, dbUser, dbPass);
             java.sql.Statement stmt = conn.createStatement()) {

            // Crear base de datos si no existe
            String createDB = "CREATE DATABASE IF NOT EXISTS " + dbName;
            stmt.execute(createDB);
            
            log.info("Base de datos '{}' verificada/creada en {}:{}", dbName, dbIp, dbPort);
            
        } catch (SQLException e) {
            log.error("No se pudo verificar/crear la base de datos. Verifique MySQL y credenciales.", e);
            throw new RuntimeException("No se pudo inicializar la base de datos", e);
        }
    }
    
    /**
     * Inicializa HikariCP con configuración optimizada para producción.
     * 
     * IMPORTANTE: Este método se ejecuta DESPUÉS de crearBaseDeDatosSiNoExiste(),
     * garantizando que la base de datos existe antes de conectar el pool.
     * 
     * Dimensionado para varios puestos remotos sobre Tailscale, cada uno con su propio pool:
     * el servidor ve N × maximumPoolSize. Cada valor lleva su porqué al lado.
     */
    private static void inicializarPool() {
        try {
            HikariConfig config = new HikariConfig();
            
            // URL de conexión incluyendo la base de datos
            String dbIp = PROPS.getProperty("db.ip", "localhost");
            String dbPort = PROPS.getProperty("db.port", "3306");
            String dbName = PROPS.getProperty("db.name", "sistema_empresa");
            config.setJdbcUrl("jdbc:mysql://" + dbIp + ":" + dbPort + "/" + dbName + "?" + PARAMS_JDBC);
            
            // Credenciales
            config.setUsername(PROPS.getProperty("db.user", "root"));
            config.setPassword(PROPS.getProperty("db.pass", "root"));
            
            // Configuración del pool
            // maximumPoolSize SÓLO BAJA, nunca sube: con N puestos son N×8 contra max_connections
            // del servidor, y ahí el agotamiento tumba a todos los puestos a la vez. El techo de
            // lecturas concurrentes del Paso 4 se dimensiona a partir de este número: si cambia
            // uno, mirar el otro.
            config.setMaximumPoolSize(8);
            config.setMinimumIdle(2);             // baja de 5: menos ociosas que el túnel pueda matar en silencio
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            config.setKeepaliveTime(120000);      // pinga las ociosas cada 2 min para que el NAT de Tailscale no las corte (< maxLifetime, >= 30 s)
            config.setIdleTimeout(300000);        // baja de 10 a 5 min
            config.setMaxLifetime(1800000);       // 30 minutos vida máxima

            // Sin setConnectionTestQuery: fijarlo obliga a Hikari al camino legacy en vez del
            // isValid() de JDBC4, que Connector/J 8.3 soporta. NO ahorra un round-trip (isValid()
            // también manda un COM_PING, y Hikari saltea la validación entera dentro de su
            // aliveBypassWindow de 500 ms): ahorra el parseo de una query y habilita validationTimeout.

            // Nombre del pool para logs
            config.setPoolName("AptiumPool");
            
            // Leak detection: detecta conexiones no cerradas (útil en desarrollo)
            config.setLeakDetectionThreshold(60000); // 60 segundos
            
            dataSource = new HikariDataSource(config);
            
            log.info("Connection Pool inicializado correctamente");
            log.info("Pool: {}", config.getPoolName());
            log.info("Base de datos: {}:{}/{}", dbIp, dbPort, dbName);
            log.info("Max conexiones: {}", config.getMaximumPoolSize());
            log.info("Min idle: {}", config.getMinimumIdle());
            log.info("Timeouts: connect={} ms, socket={} ms, connection={} ms, keepalive={} ms",
                CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS, config.getConnectionTimeout(), config.getKeepaliveTime());
            
        } catch (Exception e) {
            log.error("No se pudo inicializar el Connection Pool", e);
            throw new RuntimeException("Fallo al inicializar Connection Pool", e);
        }
    }
    
    /**
     * Obtiene una conexión del pool.
     * 
     * USO CORRECTO:
     * <pre>
     * try (Connection conn = ConnectionPool.getConnection()) {
     *     // Usar la conexión
     * } // Se devuelve automáticamente al pool
     * </pre>
     * 
     * IMPORTANTE: Siempre usar try-with-resources para garantizar que la
     * conexión se devuelva al pool. NO cerrar manualmente en finally.
     * 
     * @return Conexión del pool (nunca null)
     * @throws SQLException Si no hay conexiones disponibles después del timeout
     */
    public static Connection getConnection() throws SQLException {
        EdtGuard.verificarFueraDelHiloUi();
        javax.sql.DataSource override = testDataSource;
        if (override != null) {
            return override.getConnection();
        }
        verificarArranque();
        if (dataSource == null) {
            throw new SQLException("Connection Pool no inicializado");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Verifica que el pool pueda entregar una conexión utilizable.
     *
     * <p>Se usa como chequeo de arranque antes de levantar la UI: si devuelve
     * false, la aplicación muestra el diálogo de error de conexión y termina.
     *
     * @return true si se pudo abrir y devolver una conexión
     */
    public static boolean validarConexion() {
        try (Connection conn = getConnection()) {
            return conn != null;
        } catch (SQLException e) {
            log.error("Error al validar conexión", e);
            return false;
        }
    }

    /**
     * Cierra el pool de conexiones.
     *
     * Debe llamarse al cerrar la aplicación para liberar recursos.
     * Cierra todas las conexiones activas de forma ordenada.
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Connection Pool cerrado correctamente");
        }
    }
    
    /**
     * Conexiones activas a partir de las cuales se considera que el pool está bajo presión,
     * usado por {@link #hayPresion()}. Dos por debajo del máximo (8): avisa antes de que un
     * checkout más se quede esperando {@link #CONNECTION_TIMEOUT_MS}.
     */
    private static final int UMBRAL_ACTIVAS_PRESION = 6;

    /**
     * El {@code HikariPoolMXBean}, o {@code null} si el pool no está en condiciones de informar
     * estadísticas: no inicializado, o ya cerrado por {@link #shutdown()}.
     *
     * <p>⚠️ El chequeo es {@code dataSource.isClosed()}, <b>no</b> {@code getHikariPoolMXBean() ==
     * null}: verificado contra la fuente de HikariCP 5.0.1, {@code HikariDataSource.close()} nunca
     * pone su campo {@code pool} en null (sólo llama {@code pool.shutdown()}), y como
     * {@link #inicializarPool()} siempre usa el constructor con {@code HikariConfig}, el pool queda
     * asignado desde la construcción — {@code getHikariPoolMXBean()} no es null ni antes ni después
     * de cerrar. Confirmado con un {@code HikariDataSource} cerrado de verdad en
     * {@code ConnectionPoolTest}: sin este chequeo, un pool cerrado devolvía estadísticas en cero
     * en vez de decir que está cerrado.
     */
    private static com.zaxxer.hikari.HikariPoolMXBean obtenerMXBean() {
        return (dataSource == null || dataSource.isClosed()) ? null : dataSource.getHikariPoolMXBean();
    }

    /**
     * Obtiene estadísticas del pool (útil para monitoreo).
     *
     * @return String con estadísticas actuales del pool
     * @throws IllegalStateException con la causa original si el pool no pudo inicializarse
     */
    public static String getStats() {
        exigirArranqueSinFalla();
        com.zaxxer.hikari.HikariPoolMXBean mxBean = obtenerMXBean();
        if (mxBean == null) {
            return dataSource == null ? "Pool no inicializado" : "Pool cerrado";
        }

        return String.format(
            "Pool Stats: Total=%d, Activas=%d, Idle=%d, Esperando=%d",
            mxBean.getTotalConnections(),
            mxBean.getActiveConnections(),
            mxBean.getIdleConnections(),
            mxBean.getThreadsAwaitingConnection()
        );
    }

    /**
     * Si el pool está bajo presión: hay checkouts esperando, o las conexiones activas llegaron
     * al {@link #UMBRAL_ACTIVAS_PRESION}. Usado por {@code TareaUI} para avisar en el log sólo
     * cuando vale la pena, en vez de en cada lectura. {@code false} si el pool no está
     * inicializado o ya se cerró — nunca lanza.
     */
    public static boolean hayPresion() {
        com.zaxxer.hikari.HikariPoolMXBean mxBean = obtenerMXBean();
        if (mxBean == null) {
            return false;
        }
        return mxBean.getThreadsAwaitingConnection() > 0
            || mxBean.getActiveConnections() >= UMBRAL_ACTIVAS_PRESION;
    }
    
    /**
     * Inicializa el esquema de la base de datos (crea tablas y datos iniciales).
     * 
     * DEBE llamarse DESPUÉS de que el ConnectionPool se haya inicializado.
     * Típicamente se llama desde App.java antes de crear los DAOs.
     */
    public static void inicializarEsquema() {
        DatabaseInitializer.inicializar();
    }
    
    // Constructor privado: Evita instanciación (Singleton)
    private ConnectionPool() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }
}


