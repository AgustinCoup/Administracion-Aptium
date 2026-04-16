package com.example.features.equipos.ortopedias.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EquipoDAOTest extends AbstractDAOTest {

    private final EquipoDAO dao = new EquipoDAO();

    // La BD de test arranca sin equipos; ON DELETE CASCADE cubre equipo_materiales
    // y material_movimientos al borrar de equipos.
    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
    }

    // ── guardarEquipo ─────────────────────────────────────────────────────────

    @Test
    void guardarEquipo_sinMateriales_asignaIdYRetornaTrue() {
        Equipo e = equipoBase();
        assertTrue(dao.guardarEquipo(e));
        assertTrue(e.getId() > 0);
    }

    @Test
    void guardarEquipo_conMateriales_persisteMaterialesYMovimientos() {
        Equipo e = equipoBase();
        e.agregarMaterial(new Material(400, "Tornillera", 2));
        dao.guardarEquipo(e);

        Equipo obtenido = dao.obtenerPorId(String.valueOf(e.getId()));
        assertEquals(1, obtenido.getMateriales().size());
        assertEquals(400, obtenido.getMateriales().get(0).getCodigo());
        assertEquals(2, obtenido.getMateriales().get(0).getCantidad());
    }

    @Test
    void guardarEquipo_conProfesionalNull_persiste() {
        Equipo e = equipoBase();
        e.setNroProfesional(null);
        assertTrue(dao.guardarEquipo(e));
    }

    // ── obtenerPorId ──────────────────────────────────────────────────────────

    @Test
    void obtenerPorId_existente_retornaEquipoCompleto() {
        Equipo e = equipoBase();
        e.agregarMaterial(new Material(400, "Tornillera", 3));
        dao.guardarEquipo(e);

        Equipo obtenido = dao.obtenerPorId(String.valueOf(e.getId()));
        assertEquals(e.getId(), obtenido.getId());
        assertEquals(EstadoEquipo.NUEVO, obtenido.getEstado());
        assertFalse(obtenido.getMateriales().isEmpty());
    }

    @Test
    void obtenerPorId_noExistente_lanzaResourceNotFoundException() {
        assertThrows(ResourceNotFoundException.class,
            () -> dao.obtenerPorId("999999"));
    }

    @Test
    void obtenerPorId_idNoNumerico_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> dao.obtenerPorId("no-es-un-numero"));
    }

    // ── obtenerTodos ──────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_sinEquipos_retornaListaVacia() {
        assertTrue(dao.obtenerTodos().isEmpty());
    }

    @Test
    void obtenerTodos_despuesDeGuardar_retornaEquipo() {
        dao.guardarEquipo(equipoBase());
        assertEquals(1, dao.obtenerTodos().size());
    }

    // ── actualizar ────────────────────────────────────────────────────────────

    @Test
    void actualizar_cambiaEstado_retornaTrue() {
        Equipo e = equipoBase();
        dao.guardarEquipo(e);
        e.setEstado(EstadoEquipo.LAVANDO);
        assertTrue(dao.actualizar(e));

        Equipo obtenido = dao.obtenerPorId(String.valueOf(e.getId()));
        assertEquals(EstadoEquipo.LAVANDO, obtenido.getEstado());
    }

    @Test
    void actualizar_idInexistente_retornaFalse() {
        Equipo e = equipoBase();
        e.setId(999999);
        assertFalse(dao.actualizar(e));
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Test
    void eliminar_existente_retornaTrue() {
        Equipo e = equipoBase();
        dao.guardarEquipo(e);
        assertTrue(dao.eliminar(String.valueOf(e.getId())));
        assertThrows(ResourceNotFoundException.class,
            () -> dao.obtenerPorId(String.valueOf(e.getId())));
    }

    @Test
    void eliminar_idInexistente_retornaFalse() {
        assertFalse(dao.eliminar("999999"));
    }

    // ── contar ────────────────────────────────────────────────────────────────

    @Test
    void contar_sinEquipos_retornaCero() {
        assertEquals(0, dao.contar());
    }

    @Test
    void contar_despuesDeGuardar_incrementa() {
        dao.guardarEquipo(equipoBase());
        assertEquals(1, dao.contar());
    }

    // ── obtenerEquiposNuevos ──────────────────────────────────────────────────

    @Test
    void obtenerEquiposNuevos_retornaSoloEstadoNuevo() {
        Equipo nuevo = equipoBase();
        dao.guardarEquipo(nuevo);

        Equipo lavando = equipoBase();
        lavando.setEstado(EstadoEquipo.LAVANDO);
        dao.guardarEquipo(lavando);
        // forzar estado LAVANDO en BD (guardarEquipo inserta con el estado del objeto)
        // lavando ya tiene LAVANDO en el INSERT

        List<Equipo> nuevos = dao.obtenerEquiposNuevos();
        assertTrue(nuevos.stream().allMatch(e -> e.getEstado() == EstadoEquipo.NUEVO));
        assertEquals(1, nuevos.size());
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Equipo equipoBase() {
        Equipo e = new Equipo();
        e.setNroCliente(1);         // primer cliente del seed
        e.setNroInstitucion(1);     // primera institución del seed
        e.setClienteNombre("Cliente Seed");
        e.setEstado(EstadoEquipo.NUEVO);
        return e;
    }
}
