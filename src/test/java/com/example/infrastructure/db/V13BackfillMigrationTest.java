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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test del backfill de V13__lote_otros_volumenes: migra una H2 propia hasta V6
 * (mundo pre-refactor, con {@code volumen_lote} por fila), siembra datos como
 * los que existen en producción y aplica V13 verificando la agregación por
 * (lote, ingreso) y la eliminación de la columna.
 *
 * <p>Usa su propia base H2 (aptium_backfill) y NO toca {@code ConnectionPool},
 * para no interferir con las suites que comparten {@code aptium_test}.
 * Replica las fases 1-2 de {@code AbstractDAOTest} (V4 es MySQL-específica).
 */
class V13BackfillMigrationTest {

    private static final String LOCATIONS = "classpath:db/migration";

    @Test
    void backfill_agrupaPorLoteEIngreso_yEliminaColumnaVolumenLote() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:aptium_backfill;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setPoolName("BackfillTestPool");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            migrarHastaV6(ds);

            int loteId   = insertarLote(ds);
            int ingresoA = insertarEquipoOtros(ds);
            int ingresoB = insertarEquipoOtros(ds);
            int catalogo = insertarCatalogo(ds);

            // Mundo pre-refactor: litros repartidos por fila de material
            insertarMaterial(ds, ingresoA, catalogo, loteId, 10);   // A: 10 +
            insertarMaterial(ds, ingresoA, catalogo, loteId, 5);    // A: 5  = 15
            insertarMaterial(ds, ingresoB, catalogo, loteId, 7);    // B: 7
            insertarMaterial(ds, ingresoA, catalogo, loteId, null); // NULL no suma
            insertarMaterial(ds, ingresoA, catalogo, null, null);   // sin lote: fuera

            // Aplicar V13 (backfill + drop)
            Flyway.configure().dataSource(ds).locations(LOCATIONS)
                .validateOnMigrate(false).load().migrate();

            assertEquals(15, volumenBackfill(ds, loteId, ingresoA), "SUM por ingreso A");
            assertEquals(7,  volumenBackfill(ds, loteId, ingresoB), "SUM por ingreso B");
            assertEquals(2,  contarFilas(ds), "Solo una fila por (lote, ingreso) con litros");
            assertEquals(0,  columnasVolumenLote(ds), "La columna volumen_lote debe desaparecer");
        }
    }

    // ── Infra Flyway (espejo de las fases 1-2 de AbstractDAOTest) ────────────

    private void migrarHastaV6(HikariDataSource ds) throws SQLException {
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
            .validateOnMigrate(false).target("6").load().migrate();
    }

    // ── Seeds ────────────────────────────────────────────────────────────────

    private int insertarLote(HikariDataSource ds) throws SQLException {
        return insertarYObtenerId(ds,
            "INSERT INTO lotes (id_negocio, anio, secuencia, autoclave_nombre, " +
            "capacidad_total, capacidad_usada, fecha_inicio) " +
            "VALUES ('20261', 2026, 1, 'E01', 120, 30, CURRENT_TIMESTAMP)");
    }

    private int insertarEquipoOtros(HikariDataSource ds) throws SQLException {
        return insertarYObtenerId(ds,
            "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, " +
            "requiere_empaque, tipo_ingreso, remito_cantidad) " +
            "VALUES (1, 'Esterilizando', 0, 0, 'REMITO', 10)");
    }

    private int insertarCatalogo(HikariDataSource ds) throws SQLException {
        return insertarYObtenerId(ds,
            "INSERT INTO catalogo_otros (descripcion) VALUES ('ElementosBackfill')");
    }

    private void insertarMaterial(HikariDataSource ds, int equipoOtrosId, int catalogoId,
                                  Integer loteId, Integer volumenLote) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO equipo_otros_materiales " +
                 "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado, lote_id, volumen_lote) " +
                 "VALUES (?, ?, 'ElementosBackfill', 1, 'Esterilizando', ?, ?)")) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, catalogoId);
            if (loteId != null) ps.setInt(3, loteId); else ps.setNull(3, java.sql.Types.INTEGER);
            if (volumenLote != null) ps.setInt(4, volumenLote); else ps.setNull(4, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }

    private int insertarYObtenerId(HikariDataSource ds, String sql) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se generó ID para: " + sql);
    }

    // ── Asserts ──────────────────────────────────────────────────────────────

    private int volumenBackfill(HikariDataSource ds, int loteId, int equipoOtrosId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT volumen FROM lote_otros_volumenes WHERE lote_id = ? AND equipo_otros_id = ?")) {
            ps.setInt(1, loteId);
            ps.setInt(2, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Falta fila de backfill para ingreso " + equipoOtrosId);
                return rs.getInt(1);
            }
        }
    }

    private int contarFilas(HikariDataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM lote_otros_volumenes")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int columnasVolumenLote(HikariDataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                 "WHERE TABLE_NAME = 'EQUIPO_OTROS_MATERIALES' AND COLUMN_NAME = 'VOLUMEN_LOTE'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
