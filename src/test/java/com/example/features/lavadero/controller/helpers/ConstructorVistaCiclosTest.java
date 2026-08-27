package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.controller.helpers.ConstructorVistaCiclos.VistaCard;
import com.example.features.lavadero.controller.helpers.ConstructorVistaCiclos.VistaCiclos;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.view.helpers.LavarropasItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del armado de la pantalla de Ciclos: qué muestra cada card y qué queda en
 * disponibles, dado lo que vino de la base y lo que hay en el staging. Es la parte de
 * {@code CiclosController} que no habla con Swing ni con JDBC, extraída para poder
 * ejercitarla sin EDT ni base (hallazgo #8 de {@code plans/refactor-concurrencia-edt.md}).
 */
class ConstructorVistaCiclosTest {

    private static final List<Integer> CARDS = List.of(1, 2, 3);

    private final StagingCiclos staging = new StagingCiclos();

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static ElementoCicloItem regular(int clasificacionId, String nombre, int total) {
        return new ElementoCicloItem(clasificacionId, 100 + clasificacionId, nombre, total, 0, "Cliente");
    }

    private static ElementoCicloItem equipo(int clasificacionId, String nombre, int total) {
        return new ElementoCicloItem(clasificacionId, 100 + clasificacionId, nombre, total, 0, "Cliente",
                ElementoCicloItem.CATEGORIA_EQUIPO);
    }

    private static ElementoCicloItem fraccion(ElementoCicloItem origen, int instanciaId) {
        ElementoCicloItem copia = equipo(origen.getElementoClasificacionId(),
                origen.getElementoNombre(), origen.getCantidadTotal());
        copia.setInstanciaId(instanciaId);
        copia.setCantidadEnCiclo(1);
        return copia;
    }

    private static CicloLavadero ciclo(int id, int lavarropasNumero) {
        return new CicloLavadero(id, lavarropasNumero, null, null, null, false, false, null, null, null);
    }

    private static DatosCiclos datos(Map<Integer, CicloLavadero> activos,
                                     List<ElementoCicloItem> disponibles,
                                     Map<Integer, List<ElementoCicloItem>> itemsActivos) {
        return new DatosCiclos(activos, disponibles,
                List.of(new Lavarropas(1, 10), new Lavarropas(2, 20), new Lavarropas(3, 30)),
                itemsActivos, List.of());
    }

    private static VistaCard card(VistaCiclos vista, int numero) {
        return vista.cards().stream()
                .filter(c -> c.lavarropasNumero() == numero)
                .findFirst().orElseThrow();
    }

    // ── Cards ────────────────────────────────────────────────────────────────

    @Test
    void construir_lavarropasLibre_muestraSusPendientesDeStaging() {
        staging.agregarRegular(2, regular(7, "Sábana", 10), 3);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(regular(7, "Sábana", 10)), Map.of()), CARDS, staging);

        VistaCard card2 = card(vista, 2);
        assertFalse(card2.esActivo());
        assertNull(card2.cicloActivoId());
        assertEquals(1, card2.items().size());
        assertEquals(3, card2.items().get(0).getCantidadEnCiclo());
        assertTrue(card(vista, 1).items().isEmpty());
    }

    @Test
    void construir_lavarropasLibre_llevaElDenominadorDeLasFracciones() {
        ElementoCicloItem origen = equipo(9, "Equipo A", 1);
        staging.agregarFraccionEquipo(1, fraccion(origen, 55));
        staging.agregarFraccionEquipo(2, fraccion(origen, 55));

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(origen), Map.of()), CARDS, staging);

        assertEquals(Map.of(55, 2), card(vista, 1).fracciones());
        assertEquals(Map.of(55, 2), card(vista, 2).fracciones());
    }

    @Test
    void construir_lavarropasConCicloActivo_muestraLoDelCicloYNoElStaging() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 3);
        List<ElementoCicloItem> delCiclo = List.of(regular(4, "Toalla", 5));

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(1, ciclo(88, 1)), List.of(), Map.of(1, delCiclo)), CARDS, staging);

        VistaCard card1 = card(vista, 1);
        assertTrue(card1.esActivo());
        assertEquals(88, card1.cicloActivoId());
        assertEquals(delCiclo, card1.items());
        // Una card activa muestra lo que hay adentro del lavarropas: el denominador de
        // fracciones es del staging y ahí no aplica.
        assertTrue(card1.fracciones().isEmpty());
    }

    @Test
    void construir_cicloActivoSinElementosLeidos_dejaLaCardVacia() {
        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(3, ciclo(12, 3)), List.of(), Map.of()), CARDS, staging);

        assertTrue(card(vista, 3).esActivo());
        assertTrue(card(vista, 3).items().isEmpty());
    }

    // ── Disponibles ──────────────────────────────────────────────────────────

    @Test
    void construir_descuentaDelDisponibleLoQueYaEstaEnStaging() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 4);
        ElementoCicloItem enBase = regular(7, "Sábana", 10);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(enBase), Map.of()), CARDS, staging);

        assertEquals(1, vista.disponibles().size());
        assertEquals(4, vista.disponibles().get(0).getCantidadEnCiclo());
    }

    @Test
    void construir_elementoAgotadoPorElStaging_desapareceDeDisponibles() {
        staging.agregarRegular(1, regular(7, "Sábana", 3), 3);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(regular(7, "Sábana", 3)), Map.of()), CARDS, staging);

        assertTrue(vista.disponibles().isEmpty());
    }

    // ── Lavarropas ───────────────────────────────────────────────────────────

    @Test
    void construir_marcaOcupadosLosLavarropasConCicloActivo() {
        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(2, ciclo(77, 2)), List.of(), Map.of(2, List.of())), CARDS, staging);

        Map<Integer, LavarropasItem> porNumero = vista.lavarropas().stream()
                .collect(java.util.stream.Collectors.toMap(LavarropasItem::getNumero, l -> l));

        assertTrue(porNumero.get(2).isOcupado());
        assertEquals(77, porNumero.get(2).getCicloId());
        assertEquals(20, porNumero.get(2).getCapacidadLitros());
        assertFalse(porNumero.get(1).isOcupado());
        assertNull(porNumero.get(1).getCicloId());
    }

    // ── Flags de los botones globales ────────────────────────────────────────

    @Test
    void construir_sinStagingNiActivos_apagaLosDosFlags() {
        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(regular(7, "Sábana", 10)), Map.of()), CARDS, staging);

        assertFalse(vista.hayPendientes());
        assertFalse(vista.hayActivos());
    }

    @Test
    void construir_conStagingYConActivos_prendeLosDosFlags() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 1);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(2, ciclo(5, 2)), List.of(), Map.of(2, List.of())), CARDS, staging);

        assertTrue(vista.hayPendientes());
        assertTrue(vista.hayActivos());
    }
}
