package com.example;

import com.example.infrastructure.db.ConnectionPool;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base para tests de integración de DAOs.
 * Inicializa H2 in-memory (modo MySQL) una sola vez por suite.
 * Cada test limpia las tablas con datos variables vía {@link #limpiarTablas()}.
 *
 * Subclases deben sobreescribir {@link #limpiarTablas()} para borrar solo las
 * tablas que usen, en orden inverso a sus FK.
 */
public abstract class AbstractDAOTest {

    private static HikariDataSource h2DataSource;

    @BeforeAll
    static void setupH2() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:aptium_test;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setPoolName("TestPool");
        h2DataSource = new HikariDataSource(config);

        ConnectionPool.setDataSourceForTesting(h2DataSource);

        // Fase 1: V1–V3 con sintaxis estándar compatible con H2.
        // validateOnMigrate=false porque en ejecuciones subsiguientes (múltiples clases de test
        // comparten la misma instancia H2) el registro sintético de V4 tiene checksum -1.
        Flyway.configure()
            .dataSource(h2DataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .validateOnMigrate(false)
            .target("3")
            .load()
            .migrate();

        // Fase 2: V4 usa DELETE alias FROM … JOIN (MySQL-específico, falla en H2).
        // En tests la DB empieza vacía, así que los UPDATEs/DELETE son no-ops;
        // solo se aplica el ALTER TABLE que es el único cambio estructural.
        try (Connection conn = h2DataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE clientes ADD CONSTRAINT IF NOT EXISTS uq_clientes_nombre UNIQUE (nombre)");
            // Registrar V4 como aplicado para que Flyway no intente ejecutarlo.
            // validateOnMigrate=false en la fase 3 evita la validación del checksum sintético.
            stmt.execute(
                "INSERT IGNORE INTO \"flyway_schema_history\" " +
                "(\"installed_rank\", \"version\", \"description\", \"type\", \"script\", " +
                "\"checksum\", \"installed_by\", \"execution_time\", \"success\") " +
                "VALUES (4, '4', 'clientes unique nombre', 'SQL', 'V4__clientes_unique_nombre.sql', -1, 'test', 0, TRUE)"
            );
        }

        // Fase 3: V5+ (no-op hoy; corre automáticamente cuando se agreguen migraciones futuras).
        // validateOnMigrate=false evita el error de checksum en la fila sintética de V4.
        Flyway.configure()
            .dataSource(h2DataSource)
            .locations("classpath:db/migration")
            .validateOnMigrate(false)
            .load()
            .migrate();
    }

    @AfterAll
    static void tearDownH2() {
        if (h2DataSource != null && !h2DataSource.isClosed()) {
            h2DataSource.close();
        }
        ConnectionPool.setDataSourceForTesting(null);
    }

    @AfterEach
    void cleanUp() throws SQLException {
        limpiarTablas();
    }

    /**
     * Borrar solo las tablas usadas por el test, en orden inverso a sus FK.
     * Por defecto no hace nada. Subclases deben sobreescribir.
     */
    protected void limpiarTablas() throws SQLException {
        // default: no-op
    }

    protected void ejecutarSQL(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
