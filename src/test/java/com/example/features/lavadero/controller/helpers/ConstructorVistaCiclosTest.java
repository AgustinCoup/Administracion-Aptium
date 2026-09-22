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
        return new CicloLavadero(id, lavarropasNumero, null, null, null, List.of(), null, null);
    }

    private static DatosCiclos datos(Map<Integer, CicloLavadero> activos,
                                     List<ElementoCicloItem> disponibles,
                                     Map<Integer, List<ElementoCicloItem>> itemsActivos) {
        return new DatosCiclos(activos, disponibles,
                List.of(new Lavarropas(1, true), new Lavarropas(2, true), new Lavarropas(3, true)),
                itemsActivos, List.of(), List.of(), Map.of());
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

    /**
     * Un lavarropas se puede ocupar entre que el operador le arrastró ropa y el refresco
     * siguiente. La card deja de mostrar ese staging pero el staging seguía vivo, y "Lanzar todos"
     * lo mandaba igual: un segundo ciclo ACTIVO en el mismo lavarropas, que la pantalla ni
     * siquiera puede mostrar.
     */
    @Test
    void construir_lavarropasQueSeOcupo_descartaSuStagingYDevuelveLaRopaADisponibles() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 4);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(1, ciclo(88, 1)), List.of(regular(7, "Sábana", 10)),
                      Map.of(1, List.of())), CARDS, staging);

        assertFalse(vista.hayPendientes(), "el staging del lavarropas ocupado se descartó");
        assertEquals(1, vista.disponibles().size());
        assertEquals(0, vista.disponibles().get(0).getCantidadEnCiclo(),
            "la ropa vuelve a estar entera en disponibles, sin descontar");
        assertEquals(List.of(1), vista.stagingDescartadoPorOcupacion(),
            "el descarte se reporta: el operador no puede enterarse por ausencia");
        assertTrue(vista.stagingDescartadoPorBaja().isEmpty(),
            "nadie dio de baja nada: el cartel de baja sería falso");
    }

    @Test
    void construir_sinLavarropasOcupadosConStaging_noReportaNingunDescarte() {
        staging.agregarRegular(2, regular(7, "Sábana", 10), 3);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(1, ciclo(88, 1)), List.of(regular(7, "Sábana", 10)),
                      Map.of(1, List.of())), CARDS, staging);

        assertTrue(vista.stagingDescartadoPorOcupacion().isEmpty(),
            "el lavarropas 1 se ocupó pero no tenía nada cargado: no hay nada que avisar");
        assertTrue(vista.hayPendientes());
    }

    /**
     * Misma regla que la devolución manual: una fracción no se quita sola. Dejar 2 de 3 cambiaría
     * en silencio el reparto que el operador armó.
     */
    @Test
    void construir_lavarropasQueSeOcupoConUnaFraccion_deshaceLaSubdivisionEntera() {
        ElementoCicloItem origen = equipo(9, "Equipo A", 1);
        staging.agregarFraccionEquipo(1, fraccion(origen, 55));
        staging.agregarFraccionEquipo(2, fraccion(origen, 55));
        staging.agregarFraccionEquipo(3, fraccion(origen, 55));

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(1, ciclo(88, 1)), List.of(origen), Map.of(1, List.of())), CARDS, staging);

        assertFalse(vista.hayPendientes(), "las tres fracciones se fueron, no sólo la del ocupado");
        assertTrue(card(vista, 2).items().isEmpty());
        assertTrue(card(vista, 3).items().isEmpty());
        assertEquals(List.of(1, 2, 3), vista.stagingDescartadoPorOcupacion(),
            "el aviso nombra también las cards libres que se vaciaron de arrastre");
    }

    // ── Descarte por baja del lavarropas ─────────────────────────────────────

    /**
     * Un lavarropas que ya no está entre los que la pantalla dibuja lo dieron de baja: la tanda
     * que lo incluyera la rechazaría {@code exigirLavarropasActivos}, así que ese staging sólo
     * puede terminar en un choque. Se descarta y la ropa vuelve a disponibles, igual que con la
     * ocupación — lo que cambia es el cartel.
     */
    @Test
    void construir_lavarropasDadoDeBaja_descartaSuStagingYLoReportaEnSuPropiaLista() {
        staging.agregarRegular(3, regular(7, "Sábana", 10), 4);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(regular(7, "Sábana", 10)), Map.of()),
                List.of(1, 2), staging);

        assertFalse(vista.hayPendientes(), "el staging del lavarropas de baja se descartó");
        assertEquals(1, vista.disponibles().size());
        assertEquals(0, vista.disponibles().get(0).getCantidadEnCiclo(),
            "la ropa vuelve a estar entera en disponibles, sin descontar");
        assertEquals(List.of(3), vista.stagingDescartadoPorBaja());
        assertTrue(vista.stagingDescartadoPorOcupacion().isEmpty(),
            "nadie ocupó nada: reusar ese cartel diría que otro usuario lanzó un ciclo, y es falso");
    }

    @Test
    void construir_lavarropasDadoDeBajaSinNadaCargado_noReportaNingunDescarte() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 3);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(regular(7, "Sábana", 10)), Map.of()),
                List.of(1, 2), staging);

        assertTrue(vista.stagingDescartadoPorBaja().isEmpty(),
            "el 3 se dio de baja pero no tenía nada cargado: no hay nada que avisar");
        assertTrue(vista.hayPendientes());
    }

    /** Misma regla que la ocupación: una fracción no se va sola, se deshace el reparto entero. */
    @Test
    void construir_fraccionEnUnLavarropasDadoDeBaja_deshaceLaSubdivisionEntera() {
        ElementoCicloItem origen = equipo(9, "Equipo A", 1);
        staging.agregarFraccionEquipo(1, fraccion(origen, 55));
        staging.agregarFraccionEquipo(2, fraccion(origen, 55));
        staging.agregarFraccionEquipo(3, fraccion(origen, 55));

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(), List.of(origen), Map.of()), List.of(1, 2), staging);

        assertFalse(vista.hayPendientes(), "las tres fracciones se fueron, no sólo la del de baja");
        assertTrue(card(vista, 1).items().isEmpty());
        assertTrue(card(vista, 2).items().isEmpty());
        assertEquals(List.of(1, 2, 3), vista.stagingDescartadoPorBaja(),
            "el aviso nombra también las cards que siguen dibujadas y se vaciaron de arrastre");
    }

    /**
     * Los dos motivos a la vez: cada lista lleva lo suyo y no se mezclan. Si compartieran campo
     * habría que elegir un cartel, y cualquiera de los dos mentiría sobre la mitad de los
     * lavarropas que nombra.
     */
    @Test
    void construir_ocupacionYBajaALaVez_cadaListaLlevaLoSuyo() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 2);
        staging.agregarRegular(3, regular(8, "Toalla", 10), 2);

        VistaCiclos vista = ConstructorVistaCiclos.construir(
                datos(Map.of(1, ciclo(88, 1)),
                      List.of(regular(7, "Sábana", 10), regular(8, "Toalla", 10)),
                      Map.of(1, List.of())),
                List.of(1, 2), staging);

        assertEquals(List.of(1), vista.stagingDescartadoPorOcupacion());
        assertEquals(List.of(3), vista.stagingDescartadoPorBaja());
        assertFalse(vista.hayPendientes());
    }

    /**
     * Un lavarropas inactivo con un ciclo sin finalizar <b>sí</b> entra en los dibujables (lo trae
     * {@code obtenerDibujables()}), justamente para que tenga card y con ella el botón Finalizar.
     * Acá se fija que la vista lo trata como cualquier otro ocupado y no como una baja.
     */
    @Test
    void construir_inactivoConCicloAbierto_tieneCardActivaYNoCuentaComoBaja() {
        VistaCiclos vista = ConstructorVistaCiclos.construir(
                new DatosCiclos(Map.of(3, ciclo(12, 3)), List.of(),
                        List.of(new Lavarropas(1, true), new Lavarropas(2, true),
                                new Lavarropas(3, false)),
                        Map.of(3, List.of()), List.of(), List.of(), Map.of()),
                CARDS, staging);

        assertTrue(card(vista, 3).esActivo());
        assertEquals(12, card(vista, 3).cicloActivoId());
        assertTrue(vista.stagingDescartadoPorBaja().isEmpty());
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
