package com.example.features.equipos.ortopedias.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
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

    @Test
    void obtenerTodos_ordenaPorFechaIngresoDescendente_masAllaDelOrdenDeInsercion() throws SQLException {
        Equipo primero = equipoBase();
        dao.guardarEquipo(primero); // id menor, insertado antes
        Equipo segundo = equipoBase();
        dao.guardarEquipo(segundo); // id mayor, insertado después

        // El de id menor tiene fecha_ingreso más reciente: debe listarse primero.
        ejecutarSQL("UPDATE equipos SET fecha_ingreso = '2030-01-01 00:00:00' WHERE id = " + primero.getId());
        ejecutarSQL("UPDATE equipos SET fecha_ingreso = '2020-01-01 00:00:00' WHERE id = " + segundo.getId());

        List<Equipo> todos = dao.obtenerTodos();
        assertEquals(primero.getId(), todos.get(0).getId());
        assertEquals(segundo.getId(), todos.get(1).getId());
    }

    @Test
    void obtenerEntreFechas_filtraPorInstitucionCuandoSeEspecifica() {
        Equipo institucion1 = equipoBase();
        institucion1.setNroInstitucion(1);
        dao.guardarEquipo(institucion1);

        Equipo institucion2 = equipoBase();
        institucion2.setNroInstitucion(2);
        dao.guardarEquipo(institucion2);

        LocalDate desde = LocalDate.now().minusDays(1);
        LocalDate hasta = LocalDate.now().plusDays(1);

        List<Equipo> filtrados = dao.obtenerEntreFechas(desde, hasta, null, 1);

        assertEquals(1, filtrados.size());
        assertEquals(institucion1.getId(), filtrados.get(0).getId());
    }

    @Test
    void obtenerEntreFechas_sinInstitucion_retornaTodos() {
        Equipo institucion1 = equipoBase();
        institucion1.setNroInstitucion(1);
        dao.guardarEquipo(institucion1);

        Equipo institucion2 = equipoBase();
        institucion2.setNroInstitucion(2);
        dao.guardarEquipo(institucion2);

        LocalDate desde = LocalDate.now().minusDays(1);
        LocalDate hasta = LocalDate.now().plusDays(1);

        List<Equipo> todos = dao.obtenerEntreFechas(desde, hasta, null, null);

        assertEquals(2, todos.size());
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

    // ── obtenerActivos ────────────────────────────────────────────────────────
    //
    // El contrato de obtenerActivos() no es "hace un WHERE": es que el WHERE
    // selecciona *exactamente* lo mismo que el filtro Java que reemplaza. Por eso
    // cada caso borde se comprueba contra la implementación vieja, no contra una
    // lista de ids escrita a mano — así un cambio en calcularEstado() rompe el test
    // en vez de desincronizarse en silencio.

    @Test
    void obtenerActivos_equivaleAFiltrarTodosPorCalcularEstado() {
        // Set de prueba con las cuatro formas que puede tomar un equipo.
        dao.guardarEquipo(equipoConMateriales(EstadoEquipo.NUEVO));                        // activo
        dao.guardarEquipo(equipoConMateriales(EstadoEquipo.ESTERILIZADO));                 // activo
        dao.guardarEquipo(equipoConMateriales(EstadoEquipo.ENTREGADO));                    // entregado
        dao.guardarEquipo(equipoConMateriales(EstadoEquipo.ENTREGADO, EstadoEquipo.LAVANDO)); // mixto → activo
        dao.guardarEquipo(equipoBase());                                                   // sin materiales → NUEVO

        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_equipoSinMateriales_seIncluye() {
        Equipo sinMateriales = equipoBase();
        dao.guardarEquipo(sinMateriales);

        // calcularEstado() de un equipo vacío es NUEVO, no ENTREGADO: sigue en la cola.
        assertEquals(List.of(sinMateriales.getId()), ids(dao.obtenerActivos()));
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_todosLosMaterialesEntregados_seExcluye() {
        Equipo entregado = equipoConMateriales(EstadoEquipo.ENTREGADO, EstadoEquipo.ENTREGADO);
        dao.guardarEquipo(entregado);

        assertTrue(dao.obtenerActivos().isEmpty());
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_unSoloMaterialSinEntregar_mantieneElEquipo() {
        Equipo mixto = equipoConMateriales(
            EstadoEquipo.ENTREGADO, EstadoEquipo.ENTREGADO, EstadoEquipo.ESTERILIZADO);
        dao.guardarEquipo(mixto);

        assertEquals(List.of(mixto.getId()), ids(dao.obtenerActivos()));
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_traeLosMaterialesEntregadosDelEquipoActivo() {
        // El WHERE filtra equipos, no materiales: un equipo mixto llega completo,
        // porque AgrupadorEntregas necesita ver lo entregado para descontarlo.
        dao.guardarEquipo(equipoConMateriales(EstadoEquipo.ENTREGADO, EstadoEquipo.LAVANDO));

        assertEquals(2, dao.obtenerActivos().get(0).getMateriales().size());
    }

    @Test
    void obtenerActivos_respetaElMismoOrdenQueObtenerTodos() throws SQLException {
        Equipo viejo = equipoConMateriales(EstadoEquipo.NUEVO);
        dao.guardarEquipo(viejo);
        Equipo reciente = equipoConMateriales(EstadoEquipo.NUEVO);
        dao.guardarEquipo(reciente);

        ejecutarSQL("UPDATE equipos SET fecha_ingreso = '2020-01-01 00:00:00' WHERE id = " + viejo.getId());
        ejecutarSQL("UPDATE equipos SET fecha_ingreso = '2030-01-01 00:00:00' WHERE id = " + reciente.getId());

        assertEquals(List.of(reciente.getId(), viejo.getId()), ids(dao.obtenerActivos()));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** La respuesta correcta, calculada con la implementación que obtenerActivos() reemplaza. */
    private List<Integer> idsEsperados() {
        return dao.obtenerTodos().stream()
            .filter(e -> e.calcularEstado() != EstadoEquipo.ENTREGADO)
            .map(Equipo::getId)
            .toList();
    }

    private static List<Integer> ids(List<Equipo> equipos) {
        return equipos.stream().map(Equipo::getId).toList();
    }

    private Equipo equipoConMateriales(EstadoEquipo... estados) {
        Equipo e = equipoBase();
        int codigo = 400;
        for (EstadoEquipo estado : estados) {
            e.agregarMaterial(new Material(codigo++, "Tornillera", 1, estado));
        }
        return e;
    }

    private Equipo equipoBase() {
        Equipo e = new Equipo();
        e.setNroCliente(1);         // primer cliente del seed
        e.setNroInstitucion(1);     // primera institución del seed
        e.setClienteNombre("Cliente Seed");
        e.setEstado(EstadoEquipo.NUEVO);
        return e;
    }
}
