package com.example.features.equipos.otros.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquipoOtrosTest {

    private static final LocalDateTime AYER = LocalDateTime.of(2026, 9, 24, 10, 0);

    private static EquipoOtros equipoDetalles(MaterialOtros... materiales) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setId(7);
        equipo.setNroCliente(3);
        equipo.setClienteNombre("Cliente");
        equipo.setRequiereLavado(false);
        equipo.setRequiereEmpaque(true);
        equipo.setEstado(EstadoEquipo.NUEVO);
        equipo.setVolumenEquipo(12);
        equipo.setFechaIngreso(AYER);
        equipo.setVersion(4);
        for (MaterialOtros m : materiales) equipo.agregarMaterial(m);
        return equipo;
    }

    private static EquipoOtros equipoRemitoSinFilas(int cantidad) {
        EquipoOtros equipo = equipoDetalles();
        equipo.setTipoIngreso(TipoIngresoOtros.REMITO);
        equipo.setRemitoCantidad(cantidad);
        equipo.setRemitoId("24092026-7");
        equipo.setRemitoObservaciones("frágil");
        return equipo;
    }

    private static Map<EstadoEquipo, Integer> cantidadesPorEstado(EquipoOtros equipo, String descripcion) {
        return equipo.getMateriales().stream()
            .filter(m -> m.getDescripcion().equalsIgnoreCase(descripcion))
            .collect(Collectors.groupingBy(MaterialOtros::getEstado,
                Collectors.summingInt(MaterialOtros::getCantidad)));
    }

    // ── copiarParaPreview ────────────────────────────────────────────────────

    @Test
    @DisplayName("un preview sobre la copia no toca el original ni sus materiales")
    void copiarParaPreview_esIndependiente() {
        MaterialOtros original = new MaterialOtros(1, 9, "Sábana", 10, EstadoEquipo.NUEVO, AYER);
        EquipoOtros equipo = equipoDetalles(original);

        EquipoOtros copia = equipo.copiarParaPreview();
        copia.aplicarMovimientoPreview(copia.getMateriales().get(0), 4, EstadoEquipo.EMPAQUETADO);

        assertEquals(1, equipo.getMateriales().size());
        assertEquals(10, original.getCantidad());
        assertEquals(EstadoEquipo.NUEVO, original.getEstado());
        assertEquals(2, copia.getMateriales().size());
    }

    @Test
    @DisplayName("la copia es profunda: equipo y cada material son objetos nuevos")
    void copiarParaPreview_esProfunda() {
        MaterialOtros m1 = new MaterialOtros(1, 9, "Sábana", 10, EstadoEquipo.NUEVO, AYER);
        MaterialOtros m2 = new MaterialOtros(2, 8, "Toalla", 2, EstadoEquipo.LAVADO, null);
        EquipoOtros equipo = equipoDetalles(m1, m2);

        EquipoOtros copia = equipo.copiarParaPreview();

        assertNotSame(equipo, copia);
        assertNotSame(equipo.getMateriales(), copia.getMateriales());
        assertNotSame(m1, copia.getMateriales().get(0));
        assertNotSame(m2, copia.getMateriales().get(1));
    }

    @Test
    @DisplayName("la copia conserva ids, cantidades, estados, ultimoMovimiento y los datos del equipo")
    void copiarParaPreview_conservaIdsYUltimoMovimiento() {
        MaterialOtros m1 = new MaterialOtros(1, 9, "Sábana", 10, EstadoEquipo.NUEVO, AYER);
        m1.setLoteIdNegocio("L-1");
        EquipoOtros equipo = equipoDetalles(m1);

        EquipoOtros copia = equipo.copiarParaPreview();
        MaterialOtros c1 = copia.getMateriales().get(0);

        assertEquals(1, c1.getId());
        assertEquals(9, c1.getCatalogoOtrosId());
        assertEquals("Sábana", c1.getDescripcion());
        assertEquals(10, c1.getCantidad());
        assertEquals(EstadoEquipo.NUEVO, c1.getEstado());
        assertEquals(AYER, c1.getUltimoMovimiento());
        assertEquals("L-1", c1.getLoteIdNegocio());

        assertEquals(7, copia.getId());
        assertEquals(3, copia.getNroCliente());
        assertEquals("Cliente", copia.getClienteNombre());
        assertEquals(false, copia.isRequiereLavado());
        assertEquals(true, copia.isRequiereEmpaque());
        assertEquals(EstadoEquipo.NUEVO, copia.getEstado());
        assertEquals(12, copia.getVolumenEquipo());
        assertEquals(AYER, copia.getFechaIngreso());
        assertEquals(4, copia.getVersion());
        assertEquals(TipoIngresoOtros.DETALLES, copia.getTipoIngreso());
    }

    @Test
    @DisplayName("REMITO sin filas: la copia conserva lo del remito y expone el mismo material sintético")
    void copiarParaPreview_remitoSinFilas_conservaElSintetico() {
        EquipoOtros equipo = equipoRemitoSinFilas(20);

        EquipoOtros copia = equipo.copiarParaPreview();

        assertEquals(TipoIngresoOtros.REMITO, copia.getTipoIngreso());
        assertEquals(20, copia.getRemitoCantidad());
        assertEquals("24092026-7", copia.getRemitoId());
        assertEquals("frágil", copia.getRemitoObservaciones());
        List<MaterialRegistrableInterface> sinteticos = copia.getMaterialesRegistrables();
        assertEquals(1, sinteticos.size());
        assertEquals(0, sinteticos.get(0).getId());
        assertEquals(20, sinteticos.get(0).getCantidad());
        assertEquals(EstadoEquipo.NUEVO, sinteticos.get(0).getEstado());
    }

    @Test
    @DisplayName("REMITO sin filas: un preview sobre la copia no crea filas en el original")
    void copiarParaPreview_remitoSinFilas_previewNoTocaElOriginal() {
        EquipoOtros equipo = equipoRemitoSinFilas(20);

        EquipoOtros copia = equipo.copiarParaPreview();
        copia.aplicarMovimientoPreview(copia.getMaterialesRegistrables().get(0), 5, EstadoEquipo.EMPAQUETADO);

        assertTrue(equipo.getMateriales().isEmpty());
        assertEquals(1, equipo.getMaterialesRegistrables().size());
        assertEquals(20, equipo.getMaterialesRegistrables().get(0).getCantidad());
        assertEquals(2, copia.getMateriales().size());
    }

    // ── Previews encadenados (lo que hará el avance múltiple) ────────────────

    @Test
    @DisplayName("dos previews seguidos sobre materiales distintos: cada uno termina en su destino")
    void aplicarMovimientoPreview_dosMaterialesDistintosSeguidos_cadaUnoTerminaEnSuDestino() {
        MaterialOtros sabana = new MaterialOtros(1, 9, "Sábana", 10, EstadoEquipo.NUEVO, AYER);
        MaterialOtros toalla = new MaterialOtros(2, 8, "Toalla", 3, EstadoEquipo.NUEVO, AYER);
        EquipoOtros equipo = equipoDetalles(sabana, toalla);

        equipo.aplicarMovimientoPreview(sabana, 4, EstadoEquipo.EMPAQUETADO);
        equipo.aplicarMovimientoPreview(toalla, 3, EstadoEquipo.EMPAQUETADO);

        assertEquals(Map.of(EstadoEquipo.NUEVO, 6, EstadoEquipo.EMPAQUETADO, 4), cantidadesPorEstado(equipo, "Sábana"));
        assertEquals(Map.of(EstadoEquipo.EMPAQUETADO, 3), cantidadesPorEstado(equipo, "Toalla"));
    }

    @Test
    @DisplayName("dos filas de la misma descripción y estado avanzadas seguidas no pierden cantidad")
    void aplicarMovimientoPreview_dosFilasMismaDescripcionMismoEstado_noPierdeCantidad() {
        MaterialOtros a = new MaterialOtros(1, 9, "Sábana", 5, EstadoEquipo.NUEVO, AYER);
        MaterialOtros b = new MaterialOtros(2, 9, "sábana", 7, EstadoEquipo.NUEVO, AYER.plusHours(1));
        EquipoOtros equipo = equipoDetalles(a, b);

        equipo.aplicarMovimientoPreview(a, 5, EstadoEquipo.EMPAQUETADO);
        equipo.aplicarMovimientoPreview(b, 3, EstadoEquipo.EMPAQUETADO);

        Map<EstadoEquipo, Integer> cantidades = cantidadesPorEstado(equipo, "Sábana");
        assertEquals(Map.of(EstadoEquipo.NUEVO, 4, EstadoEquipo.EMPAQUETADO, 8), cantidades);
        long filasEmpaquetado = equipo.getMateriales().stream()
            .filter(m -> m.getEstado() == EstadoEquipo.EMPAQUETADO).count();
        assertEquals(1, filasEmpaquetado, "el preview unifica las filas de la misma descripción y estado");
    }
}
