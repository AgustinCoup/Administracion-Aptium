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

    // ── helper ────────────────────────────────────────────────────────────────

    private EquipoOtros nuevoRemito(int cantidad) {
        EquipoOtros remito = new EquipoOtros();
        remito.setNroCliente(1);
        remito.setTipoIngreso(TipoIngresoOtros.REMITO);
        remito.setRemitoCantidad(cantidad);
        return remito;
    }
}
