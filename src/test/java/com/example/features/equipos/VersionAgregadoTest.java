package com.example.features.equipos;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;

import com.example.AbstractDAOTest;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.dao.EquipoMaterialHelper;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.dao.EquipoOtrosMaterialHelper;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica el mantenimiento de la columna {@code version} (V21), el token de bloqueo optimista de
 * {@code equipos} y {@code equipo_otros}.
 *
 * <p><b>Qué NO prueba esta clase:</b> que la version sirva de guarda. No se usa en ningún
 * {@code WHERE} — es deliberado, ver el javadoc de
 * {@link EquipoMaterialHelper#recalcularEstadoEquipo}. Acá sólo se verifica que el token quede
 * honesto: que toda escritura sobre el agregado lo incremente, <b>por cualquiera de los dos
 * caminos</b>. Un bump con agujeros da falsos negativos silenciosos, que es peor que no tenerlo.
 */
class VersionAgregadoTest extends AbstractDAOTest {

    private final EquipoDAO      equipoDAO      = new EquipoDAO();
    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());
    private final LoteDAO        loteDAO        = new LoteDAO();

    private Equipo equipo;
    private int    materialId;

    @BeforeEach
    void crearEquipoConMaterial() {
        equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroInstitucion(1);
        equipo.agregarMaterial(new Material(400, "Tornillera", 3));
        equipoDAO.guardarEquipo(equipo);
        equipo = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        materialId = equipo.getMateriales().get(0).getId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM equipos");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion = 'Elementos'");
    }

    // ── El recálculo bumpea, y bumpea una vez por escritura ──────────────────

    @Test
    void ortopedias_dosRecalculosSeguidos_dejanLaVersionEnDos() throws SQLException {
        assertEquals(0, versionDeEquipo(equipo.getId()), "arranca en 0 por el DEFAULT de la V21");

        try (Connection conn = ConnectionPool.getConnection()) {
            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipo.getId());
            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipo.getId());
        }

        assertEquals(2, versionDeEquipo(equipo.getId()));
    }

    @Test
    void otros_dosRecalculosSeguidos_dejanLaVersionEnDos() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosDetalles()[0];
        assertEquals(0, versionDeEquipoOtros(equipoOtrosId));

        try (Connection conn = ConnectionPool.getConnection()) {
            EquipoOtrosMaterialHelper.recalcularEstadoEquipo(conn, equipoOtrosId);
            EquipoOtrosMaterialHelper.recalcularEstadoEquipo(conn, equipoOtrosId);
        }

        assertEquals(2, versionDeEquipoOtros(equipoOtrosId));
    }

    // ── La regresión que la duplicación habría dejado pasar ──────────────────

    /**
     * Antes de unificar el recalculador, {@code LoteDAO} tenía su propia copia del
     * {@code UPDATE equipo_otros SET estado}. Poner el bump sólo en la de {@code EquipoOtrosDAO}
     * habría dejado este camino sin incrementar la version — un falso negativo silencioso.
     */
    @Test
    void otros_elCaminoDeLoteDAO_bumpeaIgualQueElDeEquipoOtrosDAO() throws SQLException {
        int[] fixture = insertarEquipoOtrosDetalles();
        int equipoOtrosId   = fixture[0];
        int otrosMaterialId = fixture[1];

        loteDAO.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(otrosMaterialId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of());

        assertTrue(versionDeEquipoOtros(equipoOtrosId) > 0,
            "lanzarLote pasa por procesarEquiposOtrosAfectados, que tiene que bumpear");
    }

    @Test
    void ortopedias_elCaminoDeLoteDAO_bumpea() {
        int versionAntes = versionDeEquipo(equipo.getId());

        loteDAO.lanzarLote("E02", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, false, EstadoEquipo.NUEVO)), Map.of());

        assertTrue(versionDeEquipo(equipo.getId()) > versionAntes);
    }

    @Test
    void otros_finalizarLote_bumpeaAunqueDespuesSeAcumuleElVolumen() throws SQLException {
        int[] fixture = insertarEquipoOtrosDetalles();
        int equipoOtrosId   = fixture[0];
        int otrosMaterialId = fixture[1];

        Lote lote = loteDAO.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(otrosMaterialId, equipoOtrosId, 5, true, EstadoEquipo.NUEVO)), Map.of());
        int versionTrasLanzar = versionDeEquipoOtros(equipoOtrosId);

        loteDAO.finalizarLote(lote.getId());

        // acumularVolumenEquipoOtros escribe DESPUÉS del recálculo, en la misma transacción:
        // lo que la cubre es la atomicidad, no el orden.
        assertTrue(versionDeEquipoOtros(equipoOtrosId) > versionTrasLanzar);
    }

    // ── Rutas de Correcciones: sin guarda, pero con el token mantenido ───────

    @Test
    void correcciones_actualizarCantidadDeMaterial_bumpeaLaVersionDelEquipo() {
        int versionAntes = versionDeEquipo(equipo.getId());

        new com.example.features.equipos.ortopedias.dao.MaterialDAO()
            .actualizarCantidad(equipo.getId(), materialId, 7, versionAntes);

        assertEquals(versionAntes + 1, versionDeEquipo(equipo.getId()));
    }

    @Test
    void correcciones_materialInexistente_noBumpeaNada() {
        int versionAntes = versionDeEquipo(equipo.getId());

        boolean actualizado = new com.example.features.equipos.ortopedias.dao.MaterialDAO()
            .actualizarCantidad(equipo.getId(), 999_999, 7, versionAntes);

        assertFalse(actualizado);
        assertEquals(versionAntes, versionDeEquipo(equipo.getId()));
    }

    @Test
    void correcciones_insertarMaterialOtros_bumpeaLaVersionDelEquipo() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosDetalles()[0];
        int versionAntes = versionDeEquipoOtros(equipoOtrosId);

        equipoOtrosDAO.insertarMaterial(equipoOtrosId, "Sabanas", 2, versionAntes);

        assertEquals(versionAntes + 1, versionDeEquipoOtros(equipoOtrosId));
    }

    @Test
    void correcciones_actualizarCantidadRemito_bumpeaLaVersionDelEquipo() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosDetalles()[0];
        int versionAntes = versionDeEquipoOtros(equipoOtrosId);

        equipoOtrosDAO.actualizarCantidadRemito(equipoOtrosId, 9, versionAntes);

        assertEquals(versionAntes + 1, versionDeEquipoOtros(equipoOtrosId));
    }

    @Test
    void correcciones_eliminarMaterialesPorDescripcion_bumpeaLaVersionDelEquipo() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosDetalles()[0];
        int versionAntes = versionDeEquipoOtros(equipoOtrosId);

        equipoOtrosDAO.eliminarMaterialesPorDescripcion(equipoOtrosId, "Elementos", versionAntes);

        assertEquals(versionAntes + 1, versionDeEquipoOtros(equipoOtrosId));
    }

    /**
     * Con guarda, un DELETE de 0 filas después de un bump que sí matcheó es contradictorio
     * (la version dice que nadie tocó el equipo, pero las filas que se buscaban no están):
     * lanza conflicto y no commitea el bump, en vez de fallar en silencio.
     */
    @Test
    void correcciones_eliminarSinFilasQueBorrar_lanzaConflictoYNoBumpea() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosDetalles()[0];
        int versionAntes = versionDeEquipoOtros(equipoOtrosId);

        assertThrows(com.example.common.exception.ConflictoConcurrenciaException.class,
            () -> equipoOtrosDAO.eliminarMaterialesPorDescripcion(equipoOtrosId, "No existe", versionAntes));

        assertEquals(versionAntes, versionDeEquipoOtros(equipoOtrosId));
    }

    // ── El modelo lee la columna ─────────────────────────────────────────────

    @Test
    void elMapeoDeLecturaTraeLaVersion() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipo.getId());
        }

        Equipo recargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));

        assertEquals(versionDeEquipo(equipo.getId()), recargado.getVersion());
        assertEquals(1, recargado.getVersion());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private int versionDeEquipo(int equipoId) {
        return leerVersion("SELECT version FROM equipos WHERE id = ?", equipoId);
    }

    private int versionDeEquipoOtros(int equipoOtrosId) {
        return leerVersion("SELECT version FROM equipo_otros WHERE id = ?", equipoOtrosId);
    }

    private int leerVersion(String sql, int id) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer la version", e);
        }
        throw new IllegalStateException("No existe la fila " + id);
    }

    /** Inserta un equipo_otros DETALLES con 1 material (cantidad=5). Devuelve [equipoOtrosId, materialId]. */
    private int[] insertarEquipoOtrosDetalles() throws SQLException {
        int equipoOtrosId;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, " +
                 "requiere_empaque, tipo_ingreso) VALUES (1, 'Nuevo', 1, 1, 'DETALLES')",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) equipoOtrosId = rs.getInt(1);
                else throw new SQLException("No se pudo insertar equipo_otros DETALLES");
            }
        }

        ejecutarSQL("INSERT IGNORE INTO catalogo_otros (descripcion) VALUES ('Elementos')");

        int catalogoId;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM catalogo_otros WHERE descripcion = 'Elementos'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) catalogoId = rs.getInt(1);
                else throw new SQLException("No se encontró catalogo_otros 'Elementos'");
            }
        }

        int otrosMaterialId;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO equipo_otros_materiales " +
                 "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado) " +
                 "VALUES (?, ?, 'Elementos', 5, 'Nuevo')",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, catalogoId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) otrosMaterialId = rs.getInt(1);
                else throw new SQLException("No se pudo insertar equipo_otros_materiales");
            }
        }

        return new int[]{equipoOtrosId, otrosMaterialId};
    }
}
