package com.example.infrastructure.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Inicializador de base de datos que ejecuta scripts SQL desde resources.
 * 
 * Ventajas:
 * - Separa datos de código
 * - Facilita mantenimiento y actualizaciones
 * - Permite versionado de datos seed
 * - Scripts SQL reutilizables en otras herramientas
 */
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DatabaseInitializer() {
        // Utilidad estática
    }

    /**
     * Inicializa la estructura completa de base de datos.
     * Ejecuta scripts de schema y seed data en el orden correcto.
     */
    public static void inicializar() {
        try (Connection conn = ConnectionPool.getConnection()) {
            if (conn == null) {
                log.error("No se pudo obtener conexión para inicialización");
                return;
            }

            // 1. Crear estructura de tablas
            ejecutarScript(conn, "/db/schema.sql");

            // 2. Cargar datos seed solo si las tablas están vacías
            if (tablaVacia(conn, "autoclaves")) {
                ejecutarScript(conn, "/db/seed_autoclaves.sql");
            }
            
            if (tablaVacia(conn, "clientes")) {
                ejecutarScript(conn, "/db/seed_clientes.sql");
            }
            
            if (tablaVacia(conn, "instituciones")) {
                ejecutarScript(conn, "/db/seed_instituciones.sql");
            }
            
            if (tablaVacia(conn, "profesionales")) {
                ejecutarScript(conn, "/db/seed_profesionales.sql");
            }
            
            if (tablaVacia(conn, "catalogo_descripciones")) {
                ejecutarScript(conn, "/db/seed_catalogo.sql");
            }

            log.info("Base de datos inicializada correctamente");
        } catch (SQLException e) {
            log.error("Error al inicializar base de datos", e);
        }
    }

    /**
     * Ejecuta un script SQL desde resources.
     * 
     * @param conn Conexión a la base de datos
     * @param resourcePath Ruta del script en resources (ej: "/db/schema.sql")
     */
    private static void ejecutarScript(Connection conn, String resourcePath) {
        try (InputStream is = DatabaseInitializer.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("No se encontró el script: {}", resourcePath);
                return;
            }

            String sql = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .filter(line -> !line.trim().startsWith("--")) // Filtrar comentarios
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.joining("\n"));

            // Ejecutar cada statement separado por ;
            try (Statement stmt = conn.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
                log.info("Script ejecutado: {}", resourcePath);
            }
        } catch (IOException e) {
            log.error("Error al leer script {}", resourcePath, e);
        } catch (SQLException e) {
            log.error("Error al ejecutar script {}", resourcePath, e);
        }
    }

    /**
     * Verifica si una tabla está vacía.
     */
    private static boolean tablaVacia(Connection conn, String tabla) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tabla;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }
}


