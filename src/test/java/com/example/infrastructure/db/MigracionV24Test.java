package com.example.infrastructure.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de la migración de datos de V24__insumos_ciclo_lavadero: migra una H2 propia hasta V23
 * (ciclos con los booleanos {@code suavizante}/{@code potenciador}), siembra tres ciclos y aplica
 * V24 verificando que cada booleano en {@code TRUE} se convirtió en una fila de
 * {@code insumos_ciclo_lavadero}. Migra sin {@code target}, así que corre también la V25: la
 * copia tiene que sobrevivir a los {@code DROP} de las tres columnas, que ya no existen.
 *
 * <p>Es el único test que puede probarlo: el H2 de {@code AbstractDAOTest} se construye entero
 * desde cero, así que nunca hay ciclos con booleanos esperando a ser migrados.
 *
 * <p>Usa su propia base H2 (aptium_mig_v24) y NO toca {@code ConnectionPool}: pisar su
 * {@code DataSource} dejaría al resto de la suite leyendo la base equivocada según el orden en
 * que Surefire corra las clases. Replica las fases 1-2 de {@code AbstractDAOTest} (V4 es
 * MySQL-específica).
 */
class MigracionV24Test {

    private static final String LOCATIONS = "classpath:db/migration";

    @Test
    void migracion_convierteBooleanosEnFilasDeInsumos() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:aptium_mig_v24;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setPoolName("MigracionV24TestPool");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            migrarHastaV23(ds);

            int soloSuavizante = insertarCiclo(ds, true,  false);
            int ambos          = insertarCiclo(ds, true,  true);
            int ninguno        = insertarCiclo(ds, false, false);

            Flyway.configure().dataSource(ds).locations(LOCATIONS)
                .validateOnMigrate(false).load().migrate();

            assertEquals(List.of("Suavizante"), insumosDe(ds, soloSuavizante));
            assertEquals(List.of("Potenciador", "Suavizante"), insumosDe(ds, ambos));
            assertEquals(List.of(), insumosDe(ds, ninguno));
            assertEquals(List.of("Potenciador", "Suavizante"), catalogoActivo(ds),
                "El catálogo nace con los dos insumos, activos");
            assertEquals(2, contar(ds, "SELECT COUNT(*) FROM catalogo_insumos"),
                "No hay insumos inactivos ni de más");
            assertEquals(List.of(), columnasBorradasPorV25(ds),
                "La V25 borra los dos booleanos y litros_totales, después de la copia de la V24");
        }
    }

    // ── Infra Flyway (espejo de las fases 1-2 de AbstractDAOTest) ────────────

    private void migrarHastaV23(HikariDataSource ds) throws SQLException {
        Flyway.configure().dataSource(ds).locations(LOCATIONS)
            .baselineOnMigrate(true).baselineVersion("1")
            .validateOnMigrate(false).target("3").load().migrate();

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE clientes ADD CONSTRAINT IF NOT EXISTS uq_clientes_nombre UNIQUE (nombre)");
            stmt.execute(
                "INSERT IGNORE INTO \"flyway_schema_history\" " +
                "(\"installed_rank\", \"version\", \"description\", \"type\", \"script\", " +
                "\"checksum\", \"installed_by\", \"execution_time\", \"success\") " +
                "VALUES (4, '4', 'clientes unique nombre', 'SQL', 'V4__clientes_unique_nombre.sql', -1, 'test', 0, TRUE)");
        }

        Flyway.configure().dataSource(ds).locations(LOCATIONS)
            .validateOnMigrate(false).target("23").load().migrate();
    }

    // ── Seeds ────────────────────────────────────────────────────────────────

    /**
     * Sólo hacen falta las FK de {@code ciclos_lavadero}: {@code lavarropas} (sembrada por V10) y
     * {@code catalogo_jabones} (sembrado por V12). El jabón se resuelve por nombre, no por id
     * hardcodeado: es AUTO_INCREMENT, el mismo argumento que usa la V24 para los insumos.
     */
    private int insertarCiclo(HikariDataSource ds, boolean suavizante, boolean potenciador)
            throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO ciclos_lavadero (lavarropas_numero, jabon_id, litros_jabon, " +
                 "suavizante, potenciador, litros_totales, tipo_lavado) " +
                 "VALUES (1, (SELECT id FROM catalogo_jabones WHERE nombre = 'Skip'), 0.5, ?, ?, 40, 'LIMPIO')",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setBoolean(1, suavizante);
            ps.setBoolean(2, potenciador);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se generó ID para el ciclo");
    }

    // ── Asserts ──────────────────────────────────────────────────────────────

    private List<String> insumosDe(HikariDataSource ds, int cicloId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ci.nombre FROM insumos_ciclo_lavadero icl " +
                 "JOIN catalogo_insumos ci ON ci.id = icl.insumo_id " +
                 "WHERE icl.ciclo_id = ? ORDER BY ci.nombre")) {
            ps.setInt(1, cicloId);
            return nombres(ps);
        }
    }

    private List<String> catalogoActivo(HikariDataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT nombre FROM catalogo_insumos WHERE activo = TRUE ORDER BY nombre")) {
            return nombres(ps);
        }
    }

    /** Consulta el esquema en vez de atrapar el {@code SQLException} de un {@code SELECT}. */
    private List<String> columnasBorradasPorV25(HikariDataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS " +
                 "WHERE LOWER(TABLE_NAME) = 'ciclos_lavadero' " +
                 "AND LOWER(COLUMN_NAME) IN ('suavizante', 'potenciador', 'litros_totales')")) {
            return nombres(ps);
        }
    }

    private List<String> nombres(PreparedStatement ps) throws SQLException {
        List<String> nombres = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) nombres.add(rs.getString(1));
        }
        return nombres;
    }

    private int contar(HikariDataSource ds, String sql) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
