package com.example.infrastructure.db;

import com.example.AbstractDAOTest;
import com.example.common.exception.EsquemaDesactualizadoException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cubre el guard del Paso 2b: la app se niega a arrancar si la base tiene aplicada una migración
 * más nueva que la que este build conoce.
 *
 * <p>Los primeros casos ejercen el núcleo comparable sin tocar la base; los últimos verifican el
 * cableado real contra el H2 compartido de {@link AbstractDAOTest}, insertando y borrando una
 * fila sintética en {@code flyway_schema_history}.</p>
 */
class DatabaseInitializerTest extends AbstractDAOTest {

    private static final int RANK_SINTETICO = 999;

    @Override
    protected void limpiarTablas() throws SQLException {
        borrarFilaSintetica();
    }

    // ── Núcleo comparable, sin base ──────────────────────────────────────────

    @Test
    @DisplayName("una migración atrasada aplicada después NO cuenta como base adelantada")
    void migracionFueraDeOrden_noAborta() {
        // La base llegó sólo hasta V20 (le falta una V8 tardía que este build sí trae); el build
        // conoce hasta V21. maxAplicado (20) <= maxLocal (21) → no pasa nada.
        assertDoesNotThrow(() -> DatabaseInitializer.verificarNoAdelantado(
            MigrationVersion.fromVersion("20"), MigrationVersion.fromVersion("21")));
    }

    @Test
    @DisplayName("base y build a la par → no aborta")
    void mismaVersion_noAborta() {
        assertDoesNotThrow(() -> DatabaseInitializer.verificarNoAdelantado(
            MigrationVersion.fromVersion("21"), MigrationVersion.fromVersion("21")));
    }

    @Test
    @DisplayName("base por encima del build → aborta el arranque")
    void baseAdelantada_aborta() {
        assertThrows(EsquemaDesactualizadoException.class,
            () -> DatabaseInitializer.verificarNoAdelantado(
                MigrationVersion.fromVersion("99"), MigrationVersion.fromVersion("21")));
    }

    @Test
    @DisplayName("máximos nulos (historial vacío) → no aborta")
    void versionesNulas_noAbortan() {
        assertDoesNotThrow(() -> {
            DatabaseInitializer.verificarNoAdelantado(null, MigrationVersion.fromVersion("21"));
            DatabaseInitializer.verificarNoAdelantado(MigrationVersion.fromVersion("21"), null);
        });
    }

    // ── Cableado real sobre H2 ───────────────────────────────────────────────

    @Test
    @DisplayName("con el historial normal (V21 en las dos puntas) arranca")
    void historialNormal_noAborta() {
        Flyway flyway = flywayDeTest();
        assertDoesNotThrow(() -> DatabaseInitializer.verificarEsquemaNoAdelantado(flyway));
    }

    @Test
    @DisplayName("con una V99 aplicada que este build no trae, aborta")
    void historialConMigracionFutura_aborta() throws SQLException {
        insertarFilaSintetica("99");

        Flyway flyway = flywayDeTest();
        EsquemaDesactualizadoException ex = assertThrows(EsquemaDesactualizadoException.class,
            () -> DatabaseInitializer.verificarEsquemaNoAdelantado(flyway));
        assertEquals(true, ex.getMessage().contains("Actualizá"));
    }

    @Test
    @DisplayName("una versión baja registrada fuera de orden (rank alto) no aborta")
    void migracionAtrasadaEnHistorial_noAborta() throws SQLException {
        insertarFilaSintetica("2.9");

        Flyway flyway = flywayDeTest();
        assertDoesNotThrow(() -> DatabaseInitializer.verificarEsquemaNoAdelantado(flyway));
    }

    @Test
    @DisplayName("el historial de H2 llega exactamente hasta V21")
    void sanityMaximoLocal() {
        assertEquals(MigrationVersion.fromVersion("21"),
            flywayDeTest().info().current().getVersion());
    }

    private Flyway flywayDeTest() {
        return Flyway.configure()
            .dataSource(ConnectionPool.getDataSource())
            .locations("classpath:db/migration")
            .validateOnMigrate(false)
            .load();
    }

    private void insertarFilaSintetica(String version) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "INSERT INTO \"flyway_schema_history\" "
                + "(\"installed_rank\", \"version\", \"description\", \"type\", \"script\", "
                + "\"checksum\", \"installed_by\", \"execution_time\", \"success\") "
                + "VALUES (" + RANK_SINTETICO + ", '" + version + "', 'sintetica', 'SQL', "
                + "'V" + version + "__sintetica.sql', -1, 'test', 0, TRUE)");
        }
    }

    private void borrarFilaSintetica() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM \"flyway_schema_history\" WHERE \"installed_rank\" = "
                + RANK_SINTETICO);
        }
    }
}
