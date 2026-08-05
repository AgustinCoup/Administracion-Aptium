package com.example.features.lotes.dao;

import com.example.AbstractDAOTest;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.Material;
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
 * Tests de caracterización del flujo de volúmenes de agua para equipos "otros"
 * en lotes. Paso 1 del plan {@code plans/refactor-volumenes-por-ingreso.md}:
 * congelan el comportamiento observable ANTES del cambio de esquema del paso 2.
 *
 * <p>REGLA: los asserts son SOLO sobre comportamiento observable de negocio
 * ({@code equipo_otros.volumen_equipo}, salida de
 * {@code obtenerOtrosPorClientePorLote}, estados de materiales) — nunca sobre
 * columnas internas como {@code volumen_lote}. Así estos tests valen idénticos
 * antes y después de migrar la persistencia a {@code lote_otros_volumenes}.
 *
 * <p>Toda llamada a lanzarLote pasa por el wrapper
 * {@link #lanzarLoteConOtros(String, int, int, List, Map)}: recibe los litros
 * como mapa {@code equipoOtrosId → litros} (el modelo destino del refactor).
 * En el paso 2 solo se reescribe el cuerpo del wrapper a la firma nueva del
 * DAO; los tests y sus asserts no se tocan.
 */
class LoteVolumenesCaracterizacionTest extends AbstractDAOTest {

    private final LoteDAO   dao       = new LoteDAO();
    private final EquipoDAO equipoDAO = new EquipoDAO();

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM equipos");   // CASCADE cubre equipo_materiales y material_movimientos
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion = 'Elementos'");
    }

    // ── (a) DETALLES completo ────────────────────────────────────────────────

    @Test
    void detallesCompleto_finalizar_acumulaLitrosEnVolumenEquipo() throws SQLException {
        int[] fx = insertarEquipoOtrosDetalles();

        Lote lote = lanzarLoteConOtros("E01", 120, 12,
            List.of(new LoteMovimiento(fx[1], fx[0], 5, true)),
            Map.of(fx[0], 12));
        dao.finalizarLote(lote.getId());

        assertEquals(12, volumenEquipo(fx[0]));
    }

    @Test
    void detallesCompleto_reporte_contieneMaterialesYLineaLitros() throws SQLException {
        int[] fx = insertarEquipoOtrosDetalles();

        Lote lote = lanzarLoteConOtros("E01", 120, 12,
            List.of(new LoteMovimiento(fx[1], fx[0], 5, true)),
            Map.of(fx[0], 12));
        dao.finalizarLote(lote.getId());

        Map<String, List<String>> reporte = dao.obtenerOtrosPorClientePorLote(lote.getId());
        assertEquals(1, reporte.size(), "Un solo cliente en el reporte");
        List<String> lineas = reporte.values().iterator().next();
        assertTrue(lineas.size() >= 2, "Al menos una línea de material más la de litros");
        assertEquals("Litros: 12", lineas.get(lineas.size() - 1));
    }

    // ── (b) DETALLES parcial (split) ─────────────────────────────────────────

    @Test
    void detallesParcial_finalizar_acumulaSoloLitrosDelLote() throws SQLException {
        int[] fx = insertarEquipoOtrosDetalles();

        // Mueve 2 de 5 con 7 litros declarados para el ingreso
        Lote lote = lanzarLoteConOtros("E01", 120, 7,
            List.of(new LoteMovimiento(fx[1], fx[0], 2, true)),
            Map.of(fx[0], 7));
        dao.finalizarLote(lote.getId());

        assertEquals(7, volumenEquipo(fx[0]));
        // Los 3 restantes siguen disponibles, sin lote
        assertEquals(3, sumaCantidadSinLote(fx[0]));
    }

    // ── (c) REMITO: primer split y split posterior ───────────────────────────

    @Test
    void remitoPrimerSplit_finalizar_acumulaLitros() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosRemito(50);

        Lote lote = lanzarLoteConOtros("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true)),
            Map.of(equipoOtrosId, 10));
        dao.finalizarLote(lote.getId());

        assertEquals(10, volumenEquipo(equipoOtrosId));
    }

    @Test
    void remitoSplitPosterior_finalizar_acumulaSobreLoAnterior() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosRemito(50);

        Lote lote1 = lanzarLoteConOtros("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 20, true)),
            Map.of(equipoOtrosId, 10));
        dao.finalizarLote(lote1.getId());

        // La UI vuelve a mandar materialId negativo para el remanente del REMITO
        Lote lote2 = lanzarLoteConOtros("E02", 120, 5,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true)),
            Map.of(equipoOtrosId, 5));
        dao.finalizarLote(lote2.getId());

        assertEquals(15, volumenEquipo(equipoOtrosId), "10 del lote1 + 5 del lote2");
    }

    // ── (d) Dos ingresos del mismo cliente en el mismo lote ─────────────────

    @Test
    void dosIngresosMismoCliente_volumenPorIngresoIndependiente() throws SQLException {
        int ingresoA = insertarEquipoOtrosRemito(20);
        int ingresoB = insertarEquipoOtrosRemito(15);

        Lote lote = lanzarLoteConOtros("E01", 120, 25,
            List.of(
                new LoteMovimiento(-ingresoA, ingresoA, 20, true),
                new LoteMovimiento(-ingresoB, ingresoB, 15, true)),
            Map.of(ingresoA, 20, ingresoB, 5));
        dao.finalizarLote(lote.getId());

        assertEquals(20, volumenEquipo(ingresoA));
        assertEquals(5,  volumenEquipo(ingresoB));
    }

    @Test
    void dosIngresosMismoCliente_reporteSumaLitrosPorCliente() throws SQLException {
        int ingresoA = insertarEquipoOtrosRemito(20);
        int ingresoB = insertarEquipoOtrosRemito(15);

        Lote lote = lanzarLoteConOtros("E01", 120, 25,
            List.of(
                new LoteMovimiento(-ingresoA, ingresoA, 20, true),
                new LoteMovimiento(-ingresoB, ingresoB, 15, true)),
            Map.of(ingresoA, 20, ingresoB, 5));
        dao.finalizarLote(lote.getId());

        Map<String, List<String>> reporte = dao.obtenerOtrosPorClientePorLote(lote.getId());
        assertEquals(1, reporte.size(), "Ambos ingresos son del mismo cliente");
        List<String> lineas = reporte.values().iterator().next();
        assertEquals("Litros: 25", lineas.get(lineas.size() - 1),
            "El reporte suma los litros de todos los ingresos del cliente");
    }

    // ── (e) Lote fallido ─────────────────────────────────────────────────────

    @Test
    void loteFallido_noAcumulaVolumenEquipo() throws SQLException {
        int[] fx = insertarEquipoOtrosDetalles();

        Lote lote = lanzarLoteConOtros("E01", 120, 9,
            List.of(new LoteMovimiento(fx[1], fx[0], 5, true)),
            Map.of(fx[0], 9));
        dao.marcarLoteFallo(lote.getId());

        assertEquals(0, volumenEquipo(fx[0]));
    }

    @Test
    void loteFallido_materialesNoQuedanEsterilizando() throws SQLException {
        int[] fx = insertarEquipoOtrosDetalles();

        Lote lote = lanzarLoteConOtros("E01", 120, 9,
            List.of(new LoteMovimiento(fx[1], fx[0], 5, true)),
            Map.of(fx[0], 9));
        dao.marcarLoteFallo(lote.getId());

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND estado = 'Esterilizando'")) {
            ps.setInt(1, fx[0]);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Ningún material debe quedar Esterilizando tras el fallo");
            }
        }
    }

    // ── (f) Lote mixto ortopedia + otros ─────────────────────────────────────

    @Test
    void loteMixto_reporteOrtopediasNoSeVeAfectado() throws SQLException {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroInstitucion(1);
        equipo.agregarMaterial(new Material(400, "Tornillera", 3));
        equipoDAO.guardarEquipo(equipo);
        equipo = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        int materialOrtopediaId = equipo.getMateriales().get(0).getId();

        int equipoOtrosId = insertarEquipoOtrosRemito(10);

        Lote lote = lanzarLoteConOtros("E01", 120, 45,
            List.of(
                new LoteMovimiento(materialOrtopediaId, equipo.getId(), 3),
                new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true)),
            Map.of(equipoOtrosId, 20));
        dao.finalizarLote(lote.getId());

        Map<String, List<String>> ortopedias = dao.obtenerMaterialesPorClientePorLote(lote.getId());
        assertEquals(1, ortopedias.size(), "El reporte de ortopedias lista su cliente");
        assertFalse(ortopedias.values().iterator().next().isEmpty());

        Map<String, List<String>> otros = dao.obtenerOtrosPorClientePorLote(lote.getId());
        List<String> lineasOtros = otros.values().iterator().next();
        assertEquals("Litros: 20", lineasOtros.get(lineasOtros.size() - 1));
        assertEquals(20, volumenEquipo(equipoOtrosId));
    }

    // ── (g) Ingreso sin litros declarados ────────────────────────────────────

    @Test
    void ingresoSinLitrosDeclarados_volumenEquipoQuedaEnCero() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosRemito(10);

        Lote lote = lanzarLoteConOtros("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true)),
            Map.of()); // sin litros para este ingreso
        dao.finalizarLote(lote.getId());

        assertEquals(0, volumenEquipo(equipoOtrosId));
    }

    @Test
    void ingresoSinLitrosDeclarados_reporteMuestraLitrosCero() throws SQLException {
        int equipoOtrosId = insertarEquipoOtrosRemito(10);

        Lote lote = lanzarLoteConOtros("E01", 120, 10,
            List.of(new LoteMovimiento(-equipoOtrosId, equipoOtrosId, 10, true)),
            Map.of());
        dao.finalizarLote(lote.getId());

        Map<String, List<String>> reporte = dao.obtenerOtrosPorClientePorLote(lote.getId());
        List<String> lineas = reporte.values().iterator().next();
        assertEquals("Litros: 0", lineas.get(lineas.size() - 1));
    }

    // ── Wrapper (único punto de churn del paso 2) ────────────────────────────

    /**
     * Lanza un lote recibiendo los litros como mapa {@code equipoOtrosId → litros}.
     * Desde el paso 2 delega directo en la firma nueva del DAO; los tests y sus
     * asserts no cambiaron respecto del paso 1 (esa es la prueba de paridad).
     */
    private Lote lanzarLoteConOtros(String autoclave, int capacidadTotal, int capacidadUsada,
                                    List<LoteMovimiento> movimientos,
                                    Map<Integer, Integer> litrosPorIngreso) {
        return dao.lanzarLote(autoclave, capacidadTotal, capacidadUsada, movimientos, litrosPorIngreso);
    }

    // ── Helpers de fixture y lectura ─────────────────────────────────────────

    /** Litros de agua acumulados del ingreso — el dato de negocio que este refactor debe preservar. */
    private int volumenEquipo(int equipoOtrosId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT volumen_equipo FROM equipo_otros WHERE id = ?")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "equipo_otros no encontrado: " + equipoOtrosId);
                return rs.getInt(1);
            }
        }
    }

    private int sumaCantidadSinLote(int equipoOtrosId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COALESCE(SUM(cantidad), 0) FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND lote_id IS NULL")) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    /** Inserta un equipo_otros REMITO (nro_cliente=1, seed) y devuelve su id. */
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

    /** Inserta un equipo_otros DETALLES con 1 material (cantidad=5). Devuelve [equipoOtrosId, materialId]. */
    private int[] insertarEquipoOtrosDetalles() throws SQLException {
        int equipoOtrosId;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, " +
                 "requiere_empaque, tipo_ingreso) VALUES (1, 'Nuevo', 0, 0, 'DETALLES')",
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

        int materialId;
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
                if (rs.next()) materialId = rs.getInt(1);
                else throw new SQLException("No se pudo insertar equipo_otros_materiales");
            }
        }

        return new int[]{equipoOtrosId, materialId};
    }
}
