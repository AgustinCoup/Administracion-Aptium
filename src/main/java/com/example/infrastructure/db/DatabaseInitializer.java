package com.example.infrastructure.db;

import org.flywaydb.core.Flyway;
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

public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DatabaseInitializer() {}

    public static void inicializar() {
        // 1. Migraciones de schema (Flyway aplica solo las pendientes)
        Flyway.configure()
            .dataSource(ConnectionPool.getDataSource())
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load()
            .migrate();

        // 2. Datos iniciales — solo si las tablas están vacías (instalación nueva)
        try (Connection conn = ConnectionPool.getConnection()) {
            if (tablaVacia(conn, "autoclaves"))             ejecutarSeed(conn, "/db/seed_autoclaves.sql");
            if (tablaVacia(conn, "clientes"))               ejecutarSeed(conn, "/db/seed_clientes.sql");
            if (tablaVacia(conn, "instituciones"))          ejecutarSeed(conn, "/db/seed_instituciones.sql");
            if (tablaVacia(conn, "profesionales"))          ejecutarSeed(conn, "/db/seed_profesionales.sql");
            if (tablaVacia(conn, "catalogo_descripciones")) ejecutarSeed(conn, "/db/seed_catalogo.sql");
            if (tablaVacia(conn, "catalogo_otros"))         ejecutarSeed(conn, "/db/seed_catalogo_otros.sql");
        } catch (SQLException e) {
            log.error("Error al verificar datos iniciales", e);
        }
    }

    private static void ejecutarSeed(Connection conn, String resourcePath) {
        try (InputStream is = DatabaseInitializer.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("No se encontró el seed: {}", resourcePath);
                return;
            }
            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .filter(line -> !line.trim().startsWith("--"))
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.joining("\n"));

            try (Statement stmt = conn.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) stmt.execute(trimmed);
                }
                log.info("Seed ejecutado: {}", resourcePath);
            }
        } catch (IOException e) {
            log.error("Error al leer seed {}", resourcePath, e);
        } catch (SQLException e) {
            log.error("Error al ejecutar seed {}", resourcePath, e);
        }
    }

    private static boolean tablaVacia(Connection conn, String tabla) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }
}
