package com.example.features.lotes.controller.helpers;

import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del agrupador puro de pendientes por ingreso (paso 3 del plan
 * {@code plans/refactor-volumenes-por-ingreso.md}). Sin Swing ni H2: define
 * las filas (cliente, ingreso, cantidad) que muestra DialogoVolumenesIngreso.
 */
class AgrupadorIngresosLoteTest {

    @Test
    void agrupa_soloItemsOtros_excluyeOrtopedia() {
        MaterialLoteItem orto  = new MaterialLoteItem(1, 10, "Tornillera", 3, 5, "Hosp. Italiano", false);
        MaterialLoteItem otros = new MaterialLoteItem(2, 20, "Caja acero", 4, 1, "Sanatorio Finochietto", true);

        List<IngresoPendienteInfo> res = AgrupadorIngresosLote.agrupar(
            List.of(orto, otros), Map.of(20, equipoRemito("02072026-20")));

        assertEquals(1, res.size());
        assertEquals(20, res.get(0).getEquipoOtrosId());
        assertEquals("Sanatorio Finochietto", res.get(0).getClienteNombre());
    }

    @Test
    void dosItemsDelMismoIngreso_unaFilaConCantidadSumada() {
        MaterialLoteItem a = new MaterialLoteItem(2, 20, "Caja acero", 4, 1, "Sanatorio", true);
        MaterialLoteItem b = new MaterialLoteItem(3, 20, "Tijeras", 6, 1, "Sanatorio", true);

        List<IngresoPendienteInfo> res = AgrupadorIngresosLote.agrupar(
            List.of(a, b), Map.of(20, equipoRemito("02072026-20")));

        assertEquals(1, res.size());
        assertEquals(10, res.get(0).getCantidadTotal());
    }

    @Test
    void dosIngresosDelMismoCliente_dosFilas() {
        MaterialLoteItem a = new MaterialLoteItem(-20, 20, "Elementos", 5, 1, "Sanatorio", true);
        MaterialLoteItem b = new MaterialLoteItem(-30, 30, "Elementos", 8, 1, "Sanatorio", true);

        List<IngresoPendienteInfo> res = AgrupadorIngresosLote.agrupar(
            List.of(a, b), Map.of(20, equipoRemito("01072026-20"), 30, equipoRemito("02072026-30")));

        assertEquals(2, res.size(), "Mismo cliente pero ingresos distintos: una fila por ingreso");
    }

    @Test
    void etiquetaRemito_usaRemitoId() {
        List<IngresoPendienteInfo> res = AgrupadorIngresosLote.agrupar(
            List.of(new MaterialLoteItem(-20, 20, "Elementos", 5, 1, "Sanatorio", true)),
            Map.of(20, equipoRemito("02072026-20")));

        assertEquals("02072026-20", res.get(0).getEtiquetaIngreso());
    }

    @Test
    void etiquetaDetalles_usaFechaIngresoEId() {
        EquipoOtros eq = new EquipoOtros();
        eq.setFechaIngreso(LocalDateTime.of(2026, 7, 2, 10, 30));

        List<IngresoPendienteInfo> res = AgrupadorIngresosLote.agrupar(
            List.of(new MaterialLoteItem(4, 30, "Caja acero", 5, 1, "Sanatorio", true)),
            Map.of(30, eq));

        assertEquals("Ingreso 02/07/2026 (#30)", res.get(0).getEtiquetaIngreso());
    }

    @Test
    void ingresoSinDatosEnElMapa_etiquetaGenerica() {
        List<IngresoPendienteInfo> res = AgrupadorIngresosLote.agrupar(
            List.of(new MaterialLoteItem(4, 30, "Caja acero", 5, 1, "Sanatorio", true)),
            Map.of());

        assertEquals("Ingreso #30", res.get(0).getEtiquetaIngreso());
    }

    @Test
    void listaVacia_retornaListaVacia() {
        assertTrue(AgrupadorIngresosLote.agrupar(List.of(), Map.of()).isEmpty());
    }

    @Test
    void mantieneOrdenDeAparicionDeLosIngresos() {
        MaterialLoteItem b = new MaterialLoteItem(-30, 30, "Elementos", 8, 1, "ClienteB", true);
        MaterialLoteItem a = new MaterialLoteItem(-20, 20, "Elementos", 5, 1, "ClienteA", true);

        List<IngresoPendienteInfo> res = AgrupadorIngresosLote.agrupar(
            List.of(b, a), Map.of(20, equipoRemito("x-20"), 30, equipoRemito("x-30")));

        assertEquals(30, res.get(0).getEquipoOtrosId());
        assertEquals(20, res.get(1).getEquipoOtrosId());
    }

    private EquipoOtros equipoRemito(String remitoId) {
        EquipoOtros eq = new EquipoOtros();
        eq.setRemitoId(remitoId);
        return eq;
    }
}
