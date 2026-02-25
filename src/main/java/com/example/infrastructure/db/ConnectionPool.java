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
    
    // Bloque estático: Se ejecuta UNA SOLA VEZ al cargar la clase
    static {
        cargarConfiguracion();
        crearBaseDeDatosSiNoExiste(); // PRIMERO: Asegurar que la BD existe
        inicializarPool();            // SEGUNDO: Conectar al pool con la BD específica
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
        
        // Conectar a MySQL SIN especificar base de datos
        String urlSinBD = "jdbc:mysql://" + dbIp + ":" + dbPort + "/?serverTimezone=UTC&connectionTimeZone=LOCAL";
        
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
     * PARÁMETROS CRÍTICOS:
     * - maximumPoolSize: Máximo de conexiones concurrentes (10 es seguro para MySQL)
     * - minimumIdle: Conexiones siempre activas (5 reduce latencia inicial)
     * - connectionTimeout: Tiempo de espera para obtener conexión (30s)
     * - idleTimeout: Tiempo antes de cerrar conexión inactiva (10min)
     * - maxLifetime: Vida máxima de una conexión (30min, evita problemas con MySQL)
     */
    private static void inicializarPool() {
        try {
            HikariConfig config = new HikariConfig();
            
            // URL de conexión incluyendo la base de datos
            String dbIp = PROPS.getProperty("db.ip", "localhost");
            String dbPort = PROPS.getProperty("db.port", "3306");
            String dbName = PROPS.getProperty("db.name", "sistema_empresa");
            config.setJdbcUrl("jdbc:mysql://" + dbIp + ":" + dbPort + "/" + dbName + "?serverTimezone=UTC&connectionTimeZone=LOCAL");
            
            // Credenciales
            config.setUsername(PROPS.getProperty("db.user", "root"));
            config.setPassword(PROPS.getProperty("db.pass", "root"));
            
            // Configuración del pool
            config.setMaximumPoolSize(10);        // Máximo 10 conexiones concurrentes
            config.setMinimumIdle(5);             // Mínimo 5 conexiones siempre listas
            config.setConnectionTimeout(30000);   // 30 segundos timeout
            config.setIdleTimeout(600000);        // 10 minutos idle
            config.setMaxLifetime(1800000);       // 30 minutos vida máxima
            
            // Validación de conexiones
            config.setConnectionTestQuery("SELECT 1");
            
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
        if (dataSource == null) {
            throw new SQLException("Connection Pool no inicializado");
        }
        return dataSource.getConnection();
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
     * Obtiene estadísticas del pool (útil para monitoreo).
     * 
     * @return String con estadísticas actuales del pool
     */
    public static String getStats() {
        if (dataSource == null) {
            return "Pool no inicializado";
        }
        
        return String.format(
            "Pool Stats: Total=%d, Activas=%d, Idle=%d, Esperando=%d",
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
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


