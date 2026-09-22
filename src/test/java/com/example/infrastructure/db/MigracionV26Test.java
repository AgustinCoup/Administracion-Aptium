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
 * Test de V26__ajustes_catalogos_y_lavarropas: migra una H2 propia hasta V25, siembra filas en los
 * cuatro catálogos que reciben {@code activo}, y aplica la V26 verificando que
 * <ul>
 *   <li>las cuatro columnas {@code activo} existen y <b>todas</b> las filas preexistentes quedan
 *       en {@code TRUE} — el {@code DEFAULT TRUE} no alcanza como evidencia: lo que importa es que
 *       no haya quedado ningún {@code NULL} ni {@code FALSE} en las filas que ya estaban;</li>
 *   <li>{@code jabon_por_tipo_lavado} queda con los dos defaults apuntando a Skip y Lider.</li>
 * </ul>
 *
 * <p>El segundo test es el que documenta la decisión de diseño: con {@code catalogo_jabones} sin
 * 'Skip', el {@code INSERT … SELECT} no inserta nada y <b>la migración corre igual</b>. Quedarse sin
 * default es un estado legítimo ("no hay default configurado, no se toca nada"); abortar el arranque
 * de todas las máquinas por una preferencia de conveniencia sería desproporcionado. Sin este test,
 * el primer agente que vea un INSERT que puede no insertar nada lo "arregla" con un
 * {@code NOT NULL}/validación de arranque y se lleva puesta la decisión.
 *
 * <p>Cada test usa su <b>propia</b> base H2 y NO toca {@code ConnectionPool}: pisar su
 * {@code DataSource} dejaría al resto de la suite leyendo la base equivocada según el orden en que
 * Surefire corra las clases. Replica las fases 1-2 de {@code AbstractDAOTest} (V4 es
 * MySQL-específica y hay que registrarla a mano).
 */
class MigracionV26Test {

    private static final String LOCATIONS = "classpath:db/migration";

    /** Las cuatro tablas que la V26 pasa a tener baja lógica. `catalogo_descripciones` NO está:
     *  ya tiene `vigente` desde la V16 y esta migración no la toca. */
    private static final List<String> TABLAS_CON_ACTIVO = List.of(
        "catalogo_elementos_lavadero", "catalogo_jabones", "catalogo_otros", "lavarropas");

    @Test
    void migracion_agregaActivoEnTrueYLosDosDefaultsDeJabon() throws Exception {
        try (HikariDataSource ds = crearH2("aptium_mig_v26")) {
            migrarHastaV25(ds);

            // Filas preexistentes: los seeds de V9/V12/V10 más una de catalogo_otros, que nace vacía.
            ejecutar(ds, "INSERT INTO catalogo_otros (descripcion) VALUES ('Sabanas')");

            migrarAV26(ds);

            for (String tabla : TABLAS_CON_ACTIVO) {
                assertTrue(tieneColumnaActivo(ds, tabla), "Falta la columna activo en " + tabla);
                assertTrue(contar(ds, "SELECT COUNT(*) FROM " + tabla) > 0,
                    "El test no prueba nada si " + tabla + " está vacía");
                assertEquals(0, contar(ds,
                        "SELECT COUNT(*) FROM " + tabla + " WHERE activo IS NULL OR activo = FALSE"),
                    "Las filas preexistentes de " + tabla + " tienen que quedar todas activas");
            }

            assertEquals(List.of("LIMPIO=Lider", "SUCIO=Skip"), defaultsDeJabon(ds),
                "Sucio → Skip y Limpio → Lider, resueltos por nombre");
        }
    }

    /**
     * El caso de borde que fija la decisión: sin el jabón 'Skip' en el catálogo, ese INSERT no
     * inserta nada, la migración termina bien y queda un solo default. El arranque no se aborta.
     */
    @Test
    void migracion_sinElJabonDelSeed_correIgualYDejaUnSoloDefault() throws Exception {
        try (HikariDataSource ds = crearH2("aptium_mig_v26_sin_skip")) {
            migrarHastaV25(ds);

            // A esta altura no hay ciclos (la V12 los borra), así que nada referencia al jabón.
            ejecutar(ds, "DELETE FROM catalogo_jabones WHERE nombre = 'Skip'");

            assertDoesNotThrow(() -> migrarAV26(ds),
                "Quedarse sin default es un estado legítimo: la migración no puede fallar por eso");

            assertEquals(List.of("LIMPIO=Lider"), defaultsDeJabon(ds),
                "Sólo el default que sí encontró su jabón");
        }
    }

    // ── Infra Flyway (espejo de las fases 1-2 de AbstractDAOTest) ────────────

    private HikariDataSource crearH2(String nombre) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + nombre + ";DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setPoolName("MigracionV26TestPool-" + nombre);
        return new HikariDataSource(config);
    }

    private void migrarHastaV25(HikariDataSource ds) throws SQLException {
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
            .validateOnMigrate(false).target("25").load().migrate();
    }

    /** Con {@code target}: lo que se prueba acá es la V26, no las migraciones que vengan después. */
    private void migrarAV26(HikariDataSource ds) {
        Flyway.configure().dataSource(ds).locations(LOCATIONS)
            .validateOnMigrate(false).target("26").load().migrate();
    }

    // ── Asserts ──────────────────────────────────────────────────────────────

    /** Consulta el esquema en vez de atrapar el {@code SQLException} de un {@code SELECT}. */
    private boolean tieneColumnaActivo(HikariDataSource ds, String tabla) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                 "WHERE LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = 'activo'")) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    /** Los defaults como {@code "TIPO=NombreDelJabon"}, para que el assert falle diciendo qué pasó. */
    private List<String> defaultsDeJabon(HikariDataSource ds) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT j.tipo_lavado, cj.nombre FROM jabon_por_tipo_lavado j " +
                 "JOIN catalogo_jabones cj ON cj.id = j.jabon_id ORDER BY j.tipo_lavado");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) filas.add(rs.getString(1) + "=" + rs.getString(2));
        }
        return filas;
    }

    private void ejecutar(HikariDataSource ds, String sql) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
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
