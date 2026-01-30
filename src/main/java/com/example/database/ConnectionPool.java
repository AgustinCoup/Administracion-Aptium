package com.example.database;

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
     * Carga configuración desde config.properties.
     * Usa valores por defecto si el archivo no existe.
     */
    private static void cargarConfiguracion() {
        try {
            Path p = Paths.get("config.properties");
            if (!Files.exists(p)) {
                p = Paths.get("Administracion-Aptium/config.properties");
            }
            
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    PROPS.load(is);
                }
                log.info("Configuración cargada desde: {}", p.toAbsolutePath());
            } else {
                log.warn("config.properties no encontrado, usando valores por defecto");
            }
        } catch (IOException e) {
            log.warn("Error al cargar config.properties", e);
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
        String dbName = PROPS.getProperty("db.name", "sistema_empresa");
        String dbUser = PROPS.getProperty("db.user", "root");
        String dbPass = PROPS.getProperty("db.pass", "tu_password_aqui");
        
        // Conectar a MySQL SIN especificar base de datos
        String urlSinBD = "jdbc:mysql://" + dbIp + ":3306/?serverTimezone=UTC";
        
        try (Connection conn = java.sql.DriverManager.getConnection(urlSinBD, dbUser, dbPass);
             java.sql.Statement stmt = conn.createStatement()) {
            
            // Crear base de datos si no existe
            String createDB = "CREATE DATABASE IF NOT EXISTS " + dbName;
            stmt.execute(createDB);
            
            log.info("Base de datos '{}' verificada/creada", dbName);
            
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
            String dbName = PROPS.getProperty("db.name", "sistema_empresa");
            config.setJdbcUrl("jdbc:mysql://" + dbIp + ":3306/" + dbName + "?serverTimezone=UTC");
            
            // Credenciales
            config.setUsername(PROPS.getProperty("db.user", "root"));
            config.setPassword(PROPS.getProperty("db.pass", "tu_password_aqui"));
            
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
