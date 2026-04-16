package com.example;

import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.DatabaseInitializer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
        DatabaseInitializer.inicializar();
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
