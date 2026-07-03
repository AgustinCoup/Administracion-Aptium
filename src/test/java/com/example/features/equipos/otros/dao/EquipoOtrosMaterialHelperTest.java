package com.example.features.equipos.otros.dao;

import com.example.AbstractDAOTest;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para {@link EquipoOtrosMaterialHelper#materializarRemitoSplit}.
 * Usan H2 in-memory a través de {@link AbstractDAOTest}.
 */
class EquipoOtrosMaterialHelperTest extends AbstractDAOTest {

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion = 'Elementos'");
    }

    // ── Validación de parámetros ──────────────────────────────────────────────

    @Test
    void cantidadMoverCero_lanzaSQLException() throws Exception {
        int equipoId   = insertarEquipoRemito(10);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                EquipoOtrosMaterialHelper.materializarRemitoSplit(
                    conn, equipoId, catalogoId, 10, "Nuevo", 0, "Lavando", null, null));
            assertTrue(ex.getMessage().contains("cantidadMover"));
        }
    }

    @Test
    void cantidadMoverMayorQueRemito_lanzaSQLException() throws Exception {
        int equipoId   = insertarEquipoRemito(5);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                EquipoOtrosMaterialHelper.materializarRemitoSplit(
                    conn, equipoId, catalogoId, 5, "Nuevo", 10, "Lavando", null, null));
            assertTrue(ex.getMessage().contains("cantidadMover"));
            assertTrue(ex.getMessage().contains("remitoCantidad"));
        }
    }

    // ── Avance total (cantidadMover == remitoCantidad) ────────────────────────

    @Test
    void avanceTotal_insertaUnaFilaEnMateriales() throws Exception {
        int equipoId   = insertarEquipoRemito(8);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 8, "Nuevo", 8, "Lavando", null, null);
            conn.commit();
        }

        assertEquals(1, contarMateriales(equipoId));
    }

    @Test
    void avanceTotal_filaTieneCantidadCompleta() throws Exception {
        int equipoId   = insertarEquipoRemito(8);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 8, "Nuevo", 8, "Lavando", null, null);
            conn.commit();
        }

        assertEquals(8, sumaCantidades(equipoId));
    }

    @Test
    void avanceTotal_filaAvanzaAlEstadoDestino() throws Exception {
        int equipoId   = insertarEquipoRemito(8);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 8, "Nuevo", 8, "Lavando", null, null);
            conn.commit();
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT estado FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Lavando", rs.getString("estado"));
            }
        }
    }

    @Test
    void avanceTotal_registraUnMovimiento() throws Exception {
        int equipoId   = insertarEquipoRemito(8);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 8, "Nuevo", 8, "Lavando", null, null);
            conn.commit();
        }

        assertEquals(1, contarMovimientos(equipoId));
    }

    // PASO 2 del plan refactor-volumenes-por-ingreso: la columna volumen_lote
    // desaparece del esquema. Este test se reduce a assertar solo lote_id y el
    // parámetro volumenLote se elimina de materializarRemitoSplit. El
    // comportamiento de negocio (litros por ingreso) queda cubierto por
    // LoteVolumenesCaracterizacionTest.
    @Test
    void avanceTotal_conLoteId_filaGuardaLoteYVolumen() throws Exception {
        int equipoId   = insertarEquipoRemito(5);
        int catalogoId = obtenerOCrearCatalogo();
        int loteId     = insertarLote();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 5, "Nuevo", 5, "Esterilizando", loteId, 20);
            conn.commit();
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT lote_id, volumen_lote FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(loteId, rs.getInt("lote_id"));
                assertEquals(20,     rs.getInt("volumen_lote"));
            }
        }
    }

    @Test
    void avanceTotal_retornaIdDeLaFilaInsertada() throws Exception {
        int equipoId   = insertarEquipoRemito(5);
        int catalogoId = obtenerOCrearCatalogo();

        int movedId;
        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            movedId = EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 5, "Nuevo", 5, "Lavando", null, null);
            conn.commit();
        }

        assertTrue(movedId > 0);
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM equipo_otros_materiales WHERE id = ?")) {
            ps.setInt(1, movedId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "El ID retornado debe existir en la tabla");
            }
        }
    }

    // ── Avance parcial (cantidadMover < remitoCantidad) ───────────────────────

    @Test
    void avanceParcial_insertaDosFilas() throws Exception {
        int equipoId   = insertarEquipoRemito(10);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 10, "Nuevo", 4, "Lavando", null, null);
            conn.commit();
        }

        assertEquals(2, contarMateriales(equipoId));
    }

    @Test
    void avanceParcial_sumaConservaTotalOriginal() throws Exception {
        int equipoId   = insertarEquipoRemito(10);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 10, "Nuevo", 4, "Lavando", null, null);
            conn.commit();
        }

        assertEquals(10, sumaCantidades(equipoId));
    }

    @Test
    void avanceParcial_filaRestanteConservaEstadoActual() throws Exception {
        int equipoId   = insertarEquipoRemito(10);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 10, "Nuevo", 4, "Lavando", null, null);
            conn.commit();
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT cantidad FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND estado = 'Nuevo'")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Debe existir fila con estado original");
                assertEquals(6, rs.getInt("cantidad"), "Cantidad restante = 10 - 4");
            }
        }
    }

    @Test
    void avanceParcial_filaAvanzadaTieneCantidadMovida() throws Exception {
        int equipoId   = insertarEquipoRemito(10);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 10, "Nuevo", 4, "Lavando", null, null);
            conn.commit();
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT cantidad FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND estado = 'Lavando'")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(4, rs.getInt("cantidad"));
            }
        }
    }

    @Test
    void avanceParcial_filaRestanteSinLoteId() throws Exception {
        int equipoId   = insertarEquipoRemito(10);
        int catalogoId = obtenerOCrearCatalogo();
        int loteId     = insertarLote();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 10, "Nuevo", 4, "Esterilizando", loteId, 15);
            conn.commit();
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT lote_id FROM equipo_otros_materiales " +
                 "WHERE equipo_otros_id = ? AND estado = 'Nuevo'")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                rs.getInt("lote_id");
                assertTrue(rs.wasNull(), "La fila restante no debe tener lote_id");
            }
        }
    }

    @Test
    void avanceParcial_registraUnSoloMovimiento() throws Exception {
        int equipoId   = insertarEquipoRemito(10);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 10, "Nuevo", 4, "Lavando", null, null);
            conn.commit();
        }

        assertEquals(1, contarMovimientos(equipoId));
    }

    @Test
    void movimiento_estadoOrigenYDestinoCorrectos() throws Exception {
        int equipoId   = insertarEquipoRemito(5);
        int catalogoId = obtenerOCrearCatalogo();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            EquipoOtrosMaterialHelper.materializarRemitoSplit(
                conn, equipoId, catalogoId, 5, "Nuevo", 3, "Lavando", null, null);
            conn.commit();
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT estado_origen, estado_destino, cantidad " +
                 "FROM otros_material_movimientos WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Nuevo",   rs.getString("estado_origen"));
                assertEquals("Lavando", rs.getString("estado_destino"));
                assertEquals(3,         rs.getInt("cantidad"));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int insertarEquipoRemito(int remitoCantidad) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO equipo_otros (nro_cliente, estado, requiere_lavado, " +
                 "requiere_empaque, tipo_ingreso, remito_cantidad) VALUES (1,'Nuevo',0,0,'REMITO',?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, remitoCantidad);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("No se generó ID para equipo_otros");
            }
        }
    }

    private int obtenerOCrearCatalogo() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM catalogo_otros WHERE descripcion = 'Elementos'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO catalogo_otros (descripcion) VALUES ('Elementos')",
                     Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                    throw new SQLException("No se generó ID para catalogo_otros");
                }
            }
        }
    }

    private int contarMateriales(int equipoId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int sumaCantidades(int equipoId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT SUM(cantidad) FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int insertarLote() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO lotes (id_negocio, anio, secuencia, autoclave_nombre, " +
                 "capacidad_total, capacidad_usada, fecha_inicio) " +
                 "VALUES ('E01-2026-001', 2026, 1, 'E01', 120, 10, CURRENT_TIMESTAMP)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("No se generó ID para lote");
            }
        }
    }

    private int contarMovimientos(int equipoId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM otros_material_movimientos WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
