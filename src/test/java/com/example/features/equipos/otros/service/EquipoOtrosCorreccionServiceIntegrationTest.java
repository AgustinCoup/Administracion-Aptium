package com.example.features.equipos.otros.service;

import com.example.AbstractDAOTest;
import com.example.common.exception.ValidationException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class EquipoOtrosCorreccionServiceIntegrationTest extends AbstractDAOTest {

    private final CatalogoOtrosDAO           catalogoOtrosDAO = new CatalogoOtrosDAO();
    private final EquipoOtrosDAO             equipoOtrosDAO   = new EquipoOtrosDAO(catalogoOtrosDAO);
    private final AuditoriaDAO               auditoriaDAO     = new AuditoriaDAO();
    private final EquipoOtrosCorreccionService service         =
        new EquipoOtrosCorreccionService(equipoOtrosDAO, auditoriaDAO);

    private EquipoOtros equipoDetalles;
    private EquipoOtros equipoRemito;
    private int         materialId;

    @BeforeEach
    void setUp() {
        equipoDetalles = new EquipoOtros();
        equipoDetalles.setNroCliente(1);
        equipoDetalles.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipoDetalles.agregarMaterial(new MaterialOtros("TestMat", 3));
        equipoOtrosDAO.guardar(equipoDetalles);

        equipoDetalles = equipoOtrosDAO.obtenerPorId(equipoDetalles.getId());
        materialId = equipoDetalles.getMateriales().get(0).getId();

        equipoRemito = new EquipoOtros();
        equipoRemito.setNroCliente(1);
        equipoRemito.setTipoIngreso(TipoIngresoOtros.REMITO);
        equipoRemito.setRemitoCantidad(5);
        equipoOtrosDAO.guardar(equipoRemito);
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM materiales_eliminados");
        ejecutarSQL("DELETE FROM equipos_eliminados");
        ejecutarSQL("DELETE FROM equipos_auditoria");
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'Test%' OR descripcion = 'Elementos'");
    }

    // ── modificarCantidadRemito ──────────────────────────────────────────────

    @Test
    void modificarCantidadRemito_valido_actualizaRemitoCantidadYAudita() {
        assertTrue(service.modificarCantidadRemito(equipoRemito.getId(), 10, "correccion"));

        EquipoOtros recargado = equipoOtrosDAO.obtenerPorId(equipoRemito.getId());
        assertEquals(10, recargado.getRemitoCantidad());

        assertEquals(1, auditoriaDAO.obtenerPorEquipo(equipoRemito.getId()).size());
    }

    @Test
    void modificarCantidadRemito_conFilasMateriales_lanzaValidation() {
        service.agregarMaterial(equipoRemito.getId(), "Elementos", 2, "setup");

        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(equipoRemito.getId(), 20, "correccion"));
    }

    // ── modificarCantidadMaterial ────────────────────────────────────────────

    @Test
    void modificarCantidadMaterial_valido_actualizaYAudita() {
        assertTrue(service.modificarCantidadMaterial(
            equipoDetalles.getId(), materialId, 7, "correccion cantidad"));

        EquipoOtros recargado = equipoOtrosDAO.obtenerPorId(equipoDetalles.getId());
        assertEquals(7, recargado.getMateriales().get(0).getCantidad());

        assertEquals(1, auditoriaDAO.obtenerPorEquipo(equipoDetalles.getId()).size());
    }

    @Test
    void modificarCantidadMaterial_materialNoExiste_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(equipoDetalles.getId(), 9999, 5, "motivo"));
    }

    // ── agregarMaterial ──────────────────────────────────────────────────────

    @Test
    void agregarMaterial_valido_insertaMaterialYAudita() {
        assertTrue(service.agregarMaterial(
            equipoDetalles.getId(), "TestNuevoMat", 2, "reposicion"));

        EquipoOtros recargado = equipoOtrosDAO.obtenerPorId(equipoDetalles.getId());
        assertEquals(2, recargado.getMateriales().size());
        assertTrue(recargado.getMateriales().stream()
            .anyMatch(m -> "TestNuevoMat".equals(m.getDescripcion())));

        assertEquals(1, auditoriaDAO.obtenerPorEquipo(equipoDetalles.getId()).size());
    }

    // ── eliminarMaterial ─────────────────────────────────────────────────────

    @Test
    void eliminarMaterial_valido_eliminaYAudita() throws SQLException {
        assertTrue(service.eliminarMaterial(equipoDetalles.getId(), "TestMat", "motivo baja"));

        EquipoOtros recargado = equipoOtrosDAO.obtenerPorId(equipoDetalles.getId());
        assertTrue(recargado.getMateriales().isEmpty());

        assertEquals(1, contarFilas(
            "SELECT COUNT(*) FROM materiales_eliminados WHERE equipo_id_original = " + equipoDetalles.getId()));
        assertEquals(1, auditoriaDAO.obtenerPorEquipo(equipoDetalles.getId()).size());
    }

    @Test
    void eliminarMaterial_sinMaterialesConDescripcion_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(equipoDetalles.getId(), "NoExiste", "motivo"));
    }

    // ── eliminarEquipo ───────────────────────────────────────────────────────

    @Test
    void eliminarEquipo_valido_eliminaEquipoYAudita() throws SQLException {
        int id = equipoDetalles.getId();
        assertTrue(service.eliminarEquipo(id, "baja por error"));

        assertNull(equipoOtrosDAO.obtenerPorId(id));

        assertEquals(1, contarFilas(
            "SELECT COUNT(*) FROM equipos_eliminados WHERE equipo_id_original = " + id));
        assertFalse(auditoriaDAO.obtenerPorEquipo(id).isEmpty());
    }

    @Test
    void eliminarEquipo_conMateriales_snapshotCadaMaterial() throws SQLException {
        int id = equipoDetalles.getId();
        int cantMateriales = equipoDetalles.getMateriales().size();

        service.eliminarEquipo(id, "motivo");

        assertEquals(cantMateriales, contarFilas(
            "SELECT COUNT(*) FROM materiales_eliminados WHERE equipo_id_original = " + id));
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private int contarFilas(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
