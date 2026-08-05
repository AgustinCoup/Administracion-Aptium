package com.example.features.lotes.dao;

import com.example.AbstractDAOTest;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la persistencia de volúmenes POR INGRESO en lotes (tabla
 * {@code lote_otros_volumenes}). Fase RED del paso 2 del plan
 * {@code plans/refactor-volumenes-por-ingreso.md}: especifican la firma nueva
 * {@code lanzarLote(..., Map<equipoOtrosId, litros>)} y el método
 * {@code obtenerVolumenesPorLote}.
 */
class LoteVolumenesIngresoDAOTest extends AbstractDAOTest {

    private final LoteDAO dao = new LoteDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM lote_otros_volumenes");
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion = 'Elementos'");
    }

    @Test
    void lanzarLote_conMapa_insertaUnaFilaPorIngreso() throws SQLException {
        int ingresoA = insertarEquipoOtrosRemito(20);
        int ingresoB = insertarEquipoOtrosRemito(15);

        Lote lote = dao.lanzarLote("E01", 120, 25,
            List.of(
                new LoteMovimiento(-ingresoA, ingresoA, 20, true),
                new LoteMovimiento(-ingresoB, ingresoB, 15, true)),
            Map.of(ingresoA, 20, ingresoB, 5));

        assertEquals(20, volumenGuardado(lote.getId(), ingresoA));
        assertEquals(5,  volumenGuardado(lote.getId(), ingresoB));
        assertEquals(2,  contarFilasVolumen(lote.getId()));
    }

    @Test
    void lanzarLote_ingresoSinEntradaEnMapa_noInsertaFila() throws SQLException {
        int ingresoA = insertarEquipoOtrosRemito(20);
        int ingresoB = insertarEquipoOtrosRemito(15);

        Lote lote = dao.lanzarLote("E01", 120, 20,
            List.of(
                new LoteMovimiento(-ingresoA, ingresoA, 20, true),
                new LoteMovimiento(-ingresoB, ingresoB, 15, true)),
            Map.of(ingresoA, 20));

        assertEquals(1, contarFilasVolumen(lote.getId()));
    }

    @Test
    void obtenerVolumenesPorLote_devuelveMapaPorIngreso() throws SQLException {
        int ingresoA = insertarEquipoOtrosRemito(20);
        int ingresoB = insertarEquipoOtrosRemito(15);

        Lote lote = dao.lanzarLote("E01", 120, 25,
            List.of(
                new LoteMovimiento(-ingresoA, ingresoA, 20, true),
                new LoteMovimiento(-ingresoB, ingresoB, 15, true)),
            Map.of(ingresoA, 20, ingresoB, 5));

        assertEquals(Map.of(ingresoA, 20, ingresoB, 5),
            dao.obtenerVolumenesPorLote(lote.getId()));
    }

    @Test
    void obtenerVolumenesPorLote_sinFilas_retornaMapaVacio() {
        assertTrue(dao.obtenerVolumenesPorLote(9999).isEmpty());
    }

    @Test
    void finalizarLote_acumulaVolumenEquipoDesdeTablaNueva() throws SQLException {
        int ingresoA = insertarEquipoOtrosRemito(20);

        Lote lote = dao.lanzarLote("E01", 120, 20,
            List.of(new LoteMovimiento(-ingresoA, ingresoA, 20, true)),
            Map.of(ingresoA, 20));
        dao.finalizarLote(lote.getId());

        // El dato de negocio se acumula y la fila histórica del lote se conserva
        assertEquals(20, volumenEquipo(ingresoA));
        assertEquals(20, volumenGuardado(lote.getId(), ingresoA));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int volumenGuardado(int loteId, int equipoOtrosId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT volumen FROM lote_otros_volumenes WHERE lote_id = ? AND equipo_otros_id = ?")) {
            ps.setInt(1, loteId);
            ps.setInt(2, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "No hay fila de volumen para lote=" + loteId + " ingreso=" + equipoOtrosId);
                return rs.getInt(1);
            }
        }
    }

    private int contarFilasVolumen(int loteId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM lote_otros_volumenes WHERE lote_id = ?")) {
            ps.setInt(1, loteId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int volumenEquipo(int equipoOtrosId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT volumen_equipo FROM equipo_otros WHERE id = ?")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int insertarEquipoOtrosRemito(int remitoCantidad) throws SQLException {
        String sql = "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, " +
                     "requiere_empaque, tipo_ingreso, remito_cantidad) " +
                     "VALUES (1, 'Nuevo', 0, 0, 'REMITO', ?)";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, remitoCantidad);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se pudo insertar equipo_otros REMITO");
    }
}
