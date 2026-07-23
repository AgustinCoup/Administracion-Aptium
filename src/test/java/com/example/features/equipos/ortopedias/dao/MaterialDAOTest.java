package com.example.features.equipos.ortopedias.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.DatabaseException;
import com.example.features.equipos.ortopedias.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialDAOTest extends AbstractDAOTest {

    private final MaterialDAO dao     = new MaterialDAO();
    private final EquipoDAO  equipoDAO = new EquipoDAO();

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
        ejecutarSQL("DELETE FROM material_movimientos");
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
    }

    // ── obtenerCantidad ───────────────────────────────────────────────────────

    @Test
    void obtenerCantidad_materialExistente_retornaCantidad() {
        assertEquals(3, dao.obtenerCantidad(materialId));
    }

    @Test
    void obtenerCantidad_materialInexistente_retornaNull() {
        assertNull(dao.obtenerCantidad(999999));
    }

    // ── obtenerMaterial ───────────────────────────────────────────────────────

    @Test
    void obtenerMaterial_existente_retornaFilaConDatos() {
        FilaMaterial datos = dao.obtenerMaterial(materialId);
        assertNotNull(datos);
        assertEquals((int) materialId,     datos.id());
        assertEquals((int) equipo.getId(), datos.equipoId());
        assertEquals(400,                  datos.codigo());
        assertEquals("Tornillera",         datos.descripcion());  // join con catalogo
        assertEquals(3,                    datos.cantidad());
    }

    @Test
    void obtenerMaterial_inexistente_retornaNull() {
        assertNull(dao.obtenerMaterial(999999));
    }

    // ── actualizarCantidad ────────────────────────────────────────────────────

    @Test
    void actualizarCantidad_existente_retornaTrue() {
        assertTrue(dao.actualizarCantidad(materialId, 5));
        assertEquals(5, dao.obtenerCantidad(materialId));
    }

    @Test
    void actualizarCantidad_inexistente_retornaFalse() {
        assertFalse(dao.actualizarCantidad(999999, 5));
    }

    // ── actualizarCodigo ──────────────────────────────────────────────────────

    @Test
    void actualizarCodigo_existente_retornaTrue() {
        assertTrue(dao.actualizarCodigo(materialId, 401));
        assertEquals(401, dao.obtenerMaterial(materialId).codigo());
    }

    @Test
    void actualizarCodigo_inexistente_retornaFalse() {
        assertFalse(dao.actualizarCodigo(999999, 400));
    }

    // ── agregarMaterial ───────────────────────────────────────────────────────

    @Test
    void agregarMaterial_nuevo_retornaIdPositivo() {
        Integer nuevoId = dao.agregarMaterial(equipo.getId(), 400, 2);
        assertNotNull(nuevoId);
        assertTrue(nuevoId > 0);
    }

    @Test
    void agregarMaterial_insertaMovimiento() {
        dao.agregarMaterial(equipo.getId(), 400, 2);
        // El equipo ahora debería tener 2 filas para el código 400 (el original + el nuevo)
        List<FilaMaterial> materiales = dao.obtenerMaterialesPorCodigo(equipo.getId(), 400);
        assertTrue(materiales.size() >= 2);
    }

    // ── obtenerMaterialesPorCodigo ────────────────────────────────────────────

    @Test
    void obtenerMaterialesPorCodigo_existente_retornaLista() {
        List<FilaMaterial> materiales = dao.obtenerMaterialesPorCodigo(equipo.getId(), 400);
        assertEquals(1, materiales.size());
        assertEquals(400, materiales.get(0).codigo());
    }

    @Test
    void obtenerMaterialesPorCodigo_sinCoincidencias_retornaVacio() {
        assertTrue(dao.obtenerMaterialesPorCodigo(equipo.getId(), 9999).isEmpty());
    }

    // ── actualizarEstadoMaterial ──────────────────────────────────────────────

    @Test
    void actualizarEstadoMaterial_existente_retornaTrue() {
        assertTrue(dao.actualizarEstadoMaterial(equipo.getId(), 400, EstadoEquipo.LAVANDO));

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado());
    }

    @Test
    void actualizarEstadoMaterial_equipoInexistente_lanzaDatabaseException() {
        assertThrows(DatabaseException.class,
            () -> dao.actualizarEstadoMaterial(999999, 400, EstadoEquipo.LAVANDO));
    }

    // ── actualizarMultiplesMateriales ─────────────────────────────────────────

    @Test
    void actualizarMultiplesMateriales_mapaVacio_retornaTrue() {
        assertTrue(dao.actualizarMultiplesMateriales(equipo.getId(), Map.of()));
    }

    @Test
    void actualizarMultiplesMateriales_conMaterial_actualizaEstado() {
        assertTrue(dao.actualizarMultiplesMateriales(
            equipo.getId(), Map.of(materialId, EstadoEquipo.LAVANDO)));

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado());
    }

    // ── aplicarMovimientos ────────────────────────────────────────────────────

    @Test
    void aplicarMovimientos_listaVacia_retornaTrue() {
        assertTrue(dao.aplicarMovimientos(equipo.getId(), List.of()));
    }

    @Test
    void aplicarMovimientos_estadoDestinoExplicito_actualizaEstado() {
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.LAVANDO));
        dao.aplicarMovimientos(equipo.getId(), movs);

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado());
    }

    @Test
    void aplicarMovimientos_estadoDestinoNulo_lanzaExcepcion() {
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, null));
        assertThrows(DatabaseException.class,
            () -> dao.aplicarMovimientos(equipo.getId(), movs));
    }

    @Test
    void aplicarMovimientos_cantidadParcial_splitaMaterial() {
        // Mueve 1 de 3 → original queda con 2, nuevo material con 1
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 1, EstadoEquipo.LAVANDO));
        dao.aplicarMovimientos(equipo.getId(), movs);

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(2, cargado.getMateriales().size());
        int total = cargado.getMateriales().stream().mapToInt(Material::getCantidad).sum();
        assertEquals(3, total);
    }

    // ── eliminarMaterialesPorCodigo ───────────────────────────────────────────

    @Test
    void eliminarMaterialesPorCodigo_existente_retornaTrue() {
        assertTrue(dao.eliminarMaterialesPorCodigo(equipo.getId(), 400));
        assertTrue(dao.obtenerMaterialesPorCodigo(equipo.getId(), 400).isEmpty());
    }

    @Test
    void eliminarMaterialesPorCodigo_codigoNoExistente_retornaFalse() {
        assertFalse(dao.eliminarMaterialesPorCodigo(equipo.getId(), 9999));
    }

    // ── entregarInstitucionCompleta ───────────────────────────────────────────

    @Test
    void entregarInstitucionCompleta_sinEquiposEnInstitucion_retornaTrue() {
        // nroInstitucion=999 no tiene equipos
        assertTrue(dao.entregarInstitucionCompleta(999));
    }

    @Test
    void entregarInstitucionCompleta_materialEsterilizado_actualizaAEntregado() throws SQLException {
        // Pone el material en estado Esterilizado directamente en BD
        ejecutarSQL("UPDATE equipo_materiales SET estado = 'Esterilizado' WHERE id = " + materialId);
        ejecutarSQL("UPDATE equipos SET estado = 'Esterilizado' WHERE id = " + equipo.getId());

        dao.entregarInstitucionCompleta(equipo.getNroInstitucion());

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.ENTREGADO, cargado.getMateriales().get(0).getEstado());
    }
}
