package com.example.features.equipos.otros.dao;

import com.example.AbstractDAOTest;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EquipoOtrosDAOTest extends AbstractDAOTest {

    private final EquipoOtrosDAO dao = new EquipoOtrosDAO(new CatalogoOtrosDAO());

    // Fixture de DETALLES inicializado en @BeforeEach
    private EquipoOtros equipoDetalles;
    private int         materialId;

    @BeforeEach
    void crearEquipoDetallesConMaterial() {
        equipoDetalles = new EquipoOtros();
        equipoDetalles.setNroCliente(1);
        equipoDetalles.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipoDetalles.agregarMaterial(new MaterialOtros("TestDescMat Principal", 3));
        dao.guardar(equipoDetalles);
        // reload para obtener el ID del material
        equipoDetalles = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        materialId = equipoDetalles.getMateriales().get(0).getId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestDesc%' OR descripcion = 'Elementos'");
    }

    // ── guardar — DETALLES ────────────────────────────────────────────────────

    @Test
    void guardar_tipoDetalles_asignaIdPositivo() {
        assertTrue(equipoDetalles.getId() > 0);
    }

    @Test
    void guardar_tipoDetalles_materialPersistidoConId() {
        MaterialOtros mat = equipoDetalles.getMateriales().get(0);
        assertNotNull(mat.getId());
        assertTrue(mat.getId() > 0);
        assertEquals("TestDescMat Principal", mat.getDescripcion());
        assertEquals(3, mat.getCantidad());
    }

    @Test
    void guardar_tipoDetalles_noGeneraRemitoId() {
        assertNull(equipoDetalles.getRemitoId());
    }

    // ── guardar — REMITO ──────────────────────────────────────────────────────

    @Test
    void guardar_tipoRemito_generaRemitoId() {
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        String esperado = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        assertNotNull(remito.getRemitoId());
        assertTrue(remito.getRemitoId().startsWith(esperado),
            "remitoId debe empezar con " + esperado + " pero fue " + remito.getRemitoId());
    }

    @Test
    void guardar_tipoRemito_noInsertaMaterialesEnTabla() {
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(remito.getId()))
            .findFirst().orElseThrow();
        assertTrue(recargado.getMateriales().isEmpty());
    }

    // ── obtenerTodos ──────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_conEquipo_retornaLista() {
        List<EquipoOtros> lista = dao.obtenerTodos();
        assertEquals(1, lista.size());
        assertEquals(equipoDetalles.getId(), lista.get(0).getId());
    }

    @Test
    void obtenerTodos_ordenaPorFechaIngresoDescendente_masAllaDelOrdenDeInsercion() throws SQLException {
        // equipoDetalles (id menor) ya existe por @BeforeEach; se agrega uno con id mayor.
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        // El de id menor tiene fecha_ingreso más reciente: debe listarse primero.
        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2030-01-01 00:00:00' WHERE id = " + equipoDetalles.getId());
        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2020-01-01 00:00:00' WHERE id = " + remito.getId());

        List<EquipoOtros> todos = dao.obtenerTodos();
        assertEquals(equipoDetalles.getId(), todos.get(0).getId());
        assertEquals(remito.getId(), todos.get(1).getId());
    }

    // ── entregarClienteCompleto ───────────────────────────────────────────────

    @Test
    void entregarClienteCompleto_sinEquiposDelCliente_retornaTrue() {
        assertTrue(dao.entregarClienteCompleto(999));
    }

    @Test
    void entregarClienteCompleto_materialEsterilizado_actualizaAEntregado() throws SQLException {
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Esterilizado' WHERE id = " + materialId);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Esterilizado' WHERE id = " + equipoDetalles.getId());

        assertTrue(dao.entregarClienteCompleto(equipoDetalles.getNroCliente()));

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(EstadoEquipo.ENTREGADO,
            recargado.getMateriales().get(0).getEstado());
    }

    @Test
    void entregarClienteCompleto_remitoEsterilizadoSinFilas_actualizaEstadoEquipo() throws SQLException {
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Esterilizado' WHERE id = " + remito.getId());

        dao.entregarClienteCompleto(remito.getNroCliente());

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(remito.getId()))
            .findFirst().orElseThrow();
        assertEquals(EstadoEquipo.ENTREGADO, recargado.getEstado());
    }

    // ── aplicarMovimientos ────────────────────────────────────────────────────

    @Test
    void aplicarMovimientos_listaVacia_retornaTrue() {
        assertTrue(dao.aplicarMovimientos(equipoDetalles.getId(), List.of()));
    }

    @Test
    void aplicarMovimientos_estadoDestinoExplicito_actualizaEstado() {
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.LAVANDO));
        assertTrue(dao.aplicarMovimientos(equipoDetalles.getId(), movs));

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(EstadoEquipo.LAVANDO, recargado.getMateriales().get(0).getEstado());
    }

    @Test
    void aplicarMovimientos_estadoDestinoNulo_calculaSiguienteEstado() {
        // requiereLavado=true (default) → NUEVO → siguiente = LAVANDO
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, null));
        assertTrue(dao.aplicarMovimientos(equipoDetalles.getId(), movs));

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(EstadoEquipo.LAVANDO, recargado.getMateriales().get(0).getEstado());
    }

    @Test
    void aplicarMovimientos_cantidadParcial_splitaMaterial() {
        // Mueve 1 de 3 → 2 filas: original con 2 + nuevo con 1
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 1, EstadoEquipo.LAVANDO));
        dao.aplicarMovimientos(equipoDetalles.getId(), movs);

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(2, recargado.getMateriales().size());
        int total = recargado.getMateriales().stream().mapToInt(MaterialOtros::getCantidad).sum();
        assertEquals(3, total);
    }

    // ── obtenerEquiposNuevos ──────────────────────────────────────────────────

    @Test
    void obtenerEquiposNuevos_conEquipoEnEstadoNuevo_retornaEquipo() {
        List<EquipoOtros> lista = dao.obtenerEquiposNuevos();
        assertEquals(1, lista.size());
        assertEquals(equipoDetalles.getId(), lista.get(0).getId());
    }

    @Test
    void obtenerEquiposNuevos_equipoEnOtroEstado_noApareceEnLista() throws SQLException {
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Lavando' WHERE id = " + equipoDetalles.getId());
        assertTrue(dao.obtenerEquiposNuevos().isEmpty());
    }

    @Test
    void obtenerEquiposNuevos_equipoRemito_incluyeRemitoCantidad() {
        EquipoOtros remito = nuevoRemito(7);
        dao.guardar(remito);

        List<EquipoOtros> lista = dao.obtenerEquiposNuevos();
        EquipoOtros encontrado = lista.stream()
            .filter(e -> e.getId().equals(remito.getId()))
            .findFirst()
            .orElse(null);

        assertNotNull(encontrado);
        assertEquals(7, encontrado.getRemitoCantidad());
    }

    @Test
    void obtenerEquiposNuevos_multipleEquipos_retornaAmbos() {
        EquipoOtros segundo = new EquipoOtros();
        segundo.setNroCliente(2);
        segundo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        segundo.agregarMaterial(new MaterialOtros("TestDesc Segundo", 1));
        dao.guardar(segundo);

        assertEquals(2, dao.obtenerEquiposNuevos().size());
    }

    // ── obtenerPorId ──────────────────────────────────────────────────────────

    @Test
    void obtenerPorId_idExistente_retornaEquipo() {
        EquipoOtros encontrado = dao.obtenerPorId(equipoDetalles.getId());
        assertNotNull(encontrado);
        assertEquals(equipoDetalles.getId(), encontrado.getId());
    }

    @Test
    void obtenerPorId_idInexistente_retornaNull() {
        assertNull(dao.obtenerPorId(9999));
    }

    @Test
    void obtenerPorId_incluyeMateriales() {
        EquipoOtros encontrado = dao.obtenerPorId(equipoDetalles.getId());
        assertNotNull(encontrado);
        assertEquals(1, encontrado.getMateriales().size());
        assertEquals("TestDescMat Principal", encontrado.getMateriales().get(0).getDescripcion());
    }

    // ── obtenerActivos ────────────────────────────────────────────────────────
    //
    // Cada caso se compara contra la implementación que obtenerActivos() reemplaza
    // (obtenerTodos() filtrado por calcularEstado()), no contra ids escritos a mano:
    // el WHERE tiene que seguir a calcularEstado(), no parecérsele.

    @Test
    void obtenerActivos_equivaleAFiltrarTodosPorCalcularEstado() throws SQLException {
        // equipoDetalles (DETALLES en NUEVO) ya existe por @BeforeEach → activo.
        entregarPorCompleto(conMaterial("TestDesc Entregado"));           // entregado
        int mixto = conMaterial("TestDesc Mixto");                        // mixto → activo
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Entregado' " +
                    "WHERE equipo_otros_id = " + mixto + " AND descripcion = 'TestDesc Mixto'");
        agregarFila(mixto, "TestDesc Mixto2", "Lavando");

        EquipoOtros remito = nuevoRemito(4);                              // REMITO sin filas → activo
        dao.guardar(remito);

        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_detallesTodoEntregado_seExcluye() throws SQLException {
        entregarPorCompleto(equipoDetalles.getId());

        assertTrue(dao.obtenerActivos().isEmpty());
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_conFilas_ignoraLaColumnaEstadoDelEncabezado() throws SQLException {
        // calcularEstado() de un equipo con filas es el mínimo de las filas; la
        // columna eo.estado puede haber quedado desfasada y no debe decidir.
        entregarPorCompleto(equipoDetalles.getId());
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Nuevo' WHERE id = " + equipoDetalles.getId());

        assertTrue(dao.obtenerActivos().isEmpty(), "las filas mandan: todas entregadas → fuera");
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_sinFilas_mandaLaColumnaEstadoDelEncabezado() throws SQLException {
        // Un REMITO todavía sin filas reales: ahí sí el estado vive en el encabezado.
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Entregado' WHERE id = " + remito.getId());

        assertFalse(ids(dao.obtenerActivos()).contains(remito.getId()));
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_remitoSinFilasNoEntregado_seIncluye() {
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);

        assertTrue(ids(dao.obtenerActivos()).contains(remito.getId()));
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_respetaElMismoOrdenQueObtenerTodos() throws SQLException {
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2030-01-01 00:00:00' WHERE id = " + equipoDetalles.getId());
        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2020-01-01 00:00:00' WHERE id = " + remito.getId());

        assertEquals(List.of(equipoDetalles.getId(), remito.getId()), ids(dao.obtenerActivos()));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** La respuesta correcta, calculada con la implementación que obtenerActivos() reemplaza. */
    private List<Integer> idsEsperados() {
        return dao.obtenerTodos().stream()
            .filter(e -> e.calcularEstado() != EstadoEquipo.ENTREGADO)
            .map(EquipoOtros::getId)
            .toList();
    }

    private static List<Integer> ids(List<EquipoOtros> equipos) {
        return equipos.stream().map(EquipoOtros::getId).toList();
    }

    /** Crea un equipo DETALLES con un único material en NUEVO y devuelve su id. */
    private int conMaterial(String descripcion) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros(descripcion, 1));
        dao.guardar(equipo);
        return equipo.getId();
    }

    private void agregarFila(int equipoId, String descripcion, String estado) throws SQLException {
        int materialId = dao.insertarMaterial(equipoId, descripcion, 1);
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = '" + estado + "' WHERE id = " + materialId);
    }

    private void entregarPorCompleto(int equipoId) throws SQLException {
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Entregado' WHERE equipo_otros_id = " + equipoId);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Entregado' WHERE id = " + equipoId);
    }

    private EquipoOtros nuevoRemito(int cantidad) {
        EquipoOtros remito = new EquipoOtros();
        remito.setNroCliente(1);
        remito.setTipoIngreso(TipoIngresoOtros.REMITO);
        remito.setRemitoCantidad(cantidad);
        return remito;
    }
}
