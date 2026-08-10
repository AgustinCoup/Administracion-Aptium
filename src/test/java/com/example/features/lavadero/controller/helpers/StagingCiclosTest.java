package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.ElementoCicloItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la aritmética del staging de ciclos (paso 6 del plan
 * {@code plans/ciclos-tipo-lavado-grilla-y-dnd-multiple.md}). Sin Swing ni H2:
 * cubre en aislamiento el alta de pendientes y el descuento sobre disponibles.
 */
class StagingCiclosTest {

    private final StagingCiclos staging = new StagingCiclos();

    private static ElementoCicloItem regular(int clasificacionId, String nombre, int total) {
        return new ElementoCicloItem(clasificacionId, 100 + clasificacionId, nombre, total, 0, "Cliente");
    }

    private static ElementoCicloItem equipo(int clasificacionId, String nombre, int total) {
        return new ElementoCicloItem(clasificacionId, 100 + clasificacionId, nombre, total, 0, "Cliente",
                ElementoCicloItem.CATEGORIA_EQUIPO);
    }

    /** Fracción de equipo tal como la construye el diálogo de subdivisión. */
    private static ElementoCicloItem fraccion(ElementoCicloItem origen, int instanciaId) {
        ElementoCicloItem copia = equipo(origen.getElementoClasificacionId(),
                origen.getElementoNombre(), origen.getCantidadTotal());
        copia.setInstanciaId(instanciaId);
        copia.setCantidadEnCiclo(1);
        return copia;
    }

    private static ElementoCicloItem buscar(List<ElementoCicloItem> lista, int clasificacionId) {
        return lista.stream()
                .filter(i -> i.getElementoClasificacionId() == clasificacionId)
                .findFirst().orElse(null);
    }

    @Test
    void agregarRegular_itemNuevo_creaUnaFilaConLaCantidad() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 3);

        List<ElementoCicloItem> pendientes = staging.pendientesDe(1);
        assertEquals(1, pendientes.size());
        assertEquals(7, pendientes.get(0).getElementoClasificacionId());
        assertEquals(3, pendientes.get(0).getCantidadEnCiclo());
    }

    @Test
    void agregarRegular_mismaClasificacion_acumulaEnUnaSolaFila() {
        ElementoCicloItem origen = regular(7, "Sábana", 10);

        staging.agregarRegular(1, origen, 3);
        staging.agregarRegular(1, origen, 4);

        List<ElementoCicloItem> pendientes = staging.pendientesDe(1);
        assertEquals(1, pendientes.size(), "El regular repetido no abre una fila nueva");
        assertEquals(7, pendientes.get(0).getCantidadEnCiclo());
    }

    @Test
    void agregarRegular_noMutaElItemDeOrigen() {
        ElementoCicloItem origen = regular(7, "Sábana", 10);

        staging.agregarRegular(1, origen, 3);

        assertEquals(0, origen.getCantidadEnCiclo(), "El alta copia el origen, no lo modifica");
    }

    @Test
    void agregarFraccionEquipo_nuncaAcumula_cadaFraccionEsSuPropiaFila() {
        ElementoCicloItem eq = equipo(20, "Caja instrumental", 3);

        staging.agregarFraccionEquipo(1, fraccion(eq, 1));
        staging.agregarFraccionEquipo(1, fraccion(eq, 2));

        List<ElementoCicloItem> pendientes = staging.pendientesDe(1);
        assertEquals(2, pendientes.size());
        assertTrue(pendientes.stream().allMatch(i -> i.getCantidadEnCiclo() == 1));
    }

    @Test
    void fraccionesPorInstancia_equipoRepartidoEnTres_cuentaTres() {
        ElementoCicloItem eq = equipo(20, "Caja instrumental", 5);
        staging.agregarFraccionEquipo(1, fraccion(eq, 42));
        staging.agregarFraccionEquipo(2, fraccion(eq, 42));
        staging.agregarFraccionEquipo(3, fraccion(eq, 42));
        staging.agregarRegular(1, regular(7, "Sábana", 10), 2);

        Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();

        assertEquals(Map.of(42, 3), fracciones, "Los regulares no aportan fracciones");
    }

    @Test
    void aplicarSobreDisponibles_descuentoParcial_dejaElItemConCantidadEnCiclo() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 4);
        List<ElementoCicloItem> dbDisponibles = new ArrayList<>(List.of(regular(7, "Sábana", 10)));

        List<ElementoCicloItem> resultado = staging.aplicarSobreDisponibles(dbDisponibles);

        assertEquals(1, resultado.size());
        assertEquals(4, buscar(resultado, 7).getCantidadEnCiclo());
        assertEquals(10, buscar(resultado, 7).getCantidadDisponible(), "El total de la BD no se toca");
    }

    @Test
    void aplicarSobreDisponibles_descuentoTotal_sacaElItemDeLaLista() {
        staging.agregarRegular(1, regular(7, "Sábana", 4), 4);
        List<ElementoCicloItem> dbDisponibles = new ArrayList<>(List.of(regular(7, "Sábana", 4)));

        List<ElementoCicloItem> resultado = staging.aplicarSobreDisponibles(dbDisponibles);

        assertTrue(resultado.isEmpty(), "Agotado → la fila desaparece de disponibles");
    }

    @Test
    void aplicarSobreDisponibles_variosLavarropas_sumaElDescuento() {
        ElementoCicloItem origen = regular(7, "Sábana", 10);
        staging.agregarRegular(1, origen, 3);
        staging.agregarRegular(2, origen, 2);
        List<ElementoCicloItem> dbDisponibles = new ArrayList<>(List.of(regular(7, "Sábana", 10)));

        List<ElementoCicloItem> resultado = staging.aplicarSobreDisponibles(dbDisponibles);

        assertEquals(5, buscar(resultado, 7).getCantidadEnCiclo());
    }

    @Test
    void aplicarSobreDisponibles_equipoRepartido_descuentaUnaUnidadPorInstancia() {
        ElementoCicloItem eq = equipo(20, "Caja instrumental", 3);
        // Una sola unidad del equipo, repartida en 3 lavarropas: descuenta 1, no 3.
        staging.agregarFraccionEquipo(1, fraccion(eq, 42));
        staging.agregarFraccionEquipo(2, fraccion(eq, 42));
        staging.agregarFraccionEquipo(3, fraccion(eq, 42));
        List<ElementoCicloItem> dbDisponibles = new ArrayList<>(List.of(equipo(20, "Caja instrumental", 3)));

        List<ElementoCicloItem> resultado = staging.aplicarSobreDisponibles(dbDisponibles);

        assertEquals(1, buscar(resultado, 20).getCantidadEnCiclo());
    }

    @Test
    void aplicarSobreDisponibles_idStagedQueYaNoEstaEnLaBD_noExplota() {
        staging.agregarRegular(1, regular(99, "Fantasma", 5), 2);
        ElementoCicloItem eq = equipo(98, "Equipo fantasma", 2);
        staging.agregarFraccionEquipo(1, fraccion(eq, 7));
        List<ElementoCicloItem> dbDisponibles = new ArrayList<>(List.of(regular(7, "Sábana", 10)));

        List<ElementoCicloItem> resultado = assertDoesNotThrow(
                () -> staging.aplicarSobreDisponibles(dbDisponibles));

        assertEquals(1, resultado.size());
        assertEquals(0, buscar(resultado, 7).getCantidadEnCiclo(), "El ítem presente queda intacto");
    }

    // ── cantidadStaged: cuánto queda por repartir durante una tanda de drops ──

    @Test
    void cantidadStaged_sinNadaCargado_esCero() {
        assertEquals(0, staging.cantidadStaged(regular(7, "Sábana", 10)));
    }

    @Test
    void cantidadStaged_regularEnVariosLavarropas_sumaLasCantidades() {
        ElementoCicloItem sabana = regular(7, "Sábana", 10);
        staging.agregarRegular(1, sabana, 3);
        staging.agregarRegular(2, sabana, 4);

        assertEquals(7, staging.cantidadStaged(sabana));
    }

    @Test
    void cantidadStaged_equipoRepartido_cuentaInstancias_noFracciones() {
        ElementoCicloItem eq = equipo(9, "Caja de trauma", 5);
        // Una instancia partida en dos lavarropas + otra instancia entera: 2 unidades.
        staging.agregarFraccionEquipo(1, fraccion(eq, 500));
        staging.agregarFraccionEquipo(2, fraccion(eq, 500));
        staging.agregarFraccionEquipo(3, fraccion(eq, 501));

        assertEquals(2, staging.cantidadStaged(eq));
    }

    @Test
    void cantidadStaged_noMezclaElementosDistintos() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 3);

        assertEquals(0, staging.cantidadStaged(regular(8, "Toalla", 10)));
    }

    /**
     * El caso que rompía el drop múltiple: tras la primera alta, el ítem arrastrado
     * sigue con {@code cantidadEnCiclo} viejo (sólo se actualiza en el refresco),
     * pero el staging ya sabe la verdad.
     */
    @Test
    void cantidadStaged_esIndependienteDelCantidadEnCicloDelItemArrastrado() {
        ElementoCicloItem arrastrado = regular(7, "Sábana", 10);
        staging.agregarRegular(1, arrastrado, 4);

        assertEquals(0, arrastrado.getCantidadEnCiclo());
        assertEquals(4, staging.cantidadStaged(arrastrado));
    }

    @Test
    void hayPendientes_reflejaElEstado() {
        assertFalse(staging.hayPendientes());

        staging.agregarRegular(1, regular(7, "Sábana", 10), 1);
        assertTrue(staging.hayPendientes());

        staging.limpiar();
        assertFalse(staging.hayPendientes());
    }

    @Test
    void limpiarLavarropas_soloVaciaElLavarropasIndicado() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 1);
        staging.agregarRegular(2, regular(8, "Toalla", 10), 1);

        staging.limpiarLavarropas(1);

        assertTrue(staging.pendientesDe(1).isEmpty());
        assertEquals(1, staging.pendientesDe(2).size());
        assertTrue(staging.hayPendientes());
    }

    @Test
    void lavarropasConPendientes_devuelveLosNumerosOrdenados() {
        staging.agregarRegular(3, regular(7, "Sábana", 10), 1);
        staging.agregarRegular(1, regular(8, "Toalla", 10), 1);
        staging.agregarRegular(2, regular(9, "Camisolín", 10), 1);
        staging.limpiarLavarropas(2);

        assertEquals(List.of(1, 3), staging.lavarropasConPendientes());
    }

    @Test
    void pendientesDe_devuelveUnaListaNoModificable() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 1);

        List<ElementoCicloItem> pendientes = staging.pendientesDe(1);

        assertThrows(UnsupportedOperationException.class, () -> pendientes.add(regular(8, "Toalla", 1)));
    }

    @Test
    void pendientesDe_lavarropasSinNada_devuelveListaVacia() {
        assertTrue(staging.pendientesDe(5).isEmpty());
    }

    // ── Baja: devolución de una card a la tabla de disponibles ────────────────

    @Test
    void quitar_regularCompleto_desapareceDeLosPendientes() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 3);
        List<ElementoCicloItem> seleccion = staging.pendientesDe(1);

        staging.quitar(seleccion, 1);

        assertTrue(staging.pendientesDe(1).isEmpty());
    }

    @Test
    void quitarRegular_bajaParcial_dejaLaFilaConElResto() {
        ElementoCicloItem sabana = regular(7, "Sábana", 10);
        staging.agregarRegular(1, sabana, 5);

        staging.quitarRegular(1, sabana, 2);

        assertEquals(1, staging.pendientesDe(1).size());
        assertEquals(3, staging.pendientesDe(1).get(0).getCantidadEnCiclo());
        assertEquals(3, staging.cantidadStaged(sabana));
    }

    @Test
    void quitar_soloAfectaAlLavarropasDeOrigen() {
        ElementoCicloItem sabana = regular(7, "Sábana", 10);
        staging.agregarRegular(1, sabana, 3);
        staging.agregarRegular(2, sabana, 4);

        staging.quitar(staging.pendientesDe(1), 1);

        assertTrue(staging.pendientesDe(1).isEmpty());
        assertEquals(4, staging.pendientesDe(2).get(0).getCantidadEnCiclo());
    }

    @Test
    void quitar_unaFraccionDeEquipoRepartidoEnTres_lasQuitaDeLasTresCards() {
        ElementoCicloItem eq = equipo(20, "Caja instrumental", 3);
        staging.agregarFraccionEquipo(1, fraccion(eq, 42));
        staging.agregarFraccionEquipo(2, fraccion(eq, 42));
        staging.agregarFraccionEquipo(3, fraccion(eq, 42));

        // Se devuelve la fracción del lavarropas 2: se deshace la subdivisión entera.
        staging.quitar(staging.pendientesDe(2), 2);

        assertTrue(staging.pendientesDe(1).isEmpty());
        assertTrue(staging.pendientesDe(2).isEmpty());
        assertTrue(staging.pendientesDe(3).isEmpty());
    }

    @Test
    void quitar_fraccionDeEquipo_noTocaOtrasInstanciasDelMismoElemento() {
        ElementoCicloItem eq = equipo(20, "Caja instrumental", 5);
        staging.agregarFraccionEquipo(1, fraccion(eq, 500));
        staging.agregarFraccionEquipo(2, fraccion(eq, 500));
        staging.agregarFraccionEquipo(1, fraccion(eq, 501));

        staging.quitarInstanciaEquipo(500);

        assertEquals(1, staging.pendientesDe(1).size());
        assertEquals(501, staging.pendientesDe(1).get(0).getInstanciaId());
        assertTrue(staging.pendientesDe(2).isEmpty());
    }

    @Test
    void quitar_itemQueYaNoEsta_esNoOp() {
        staging.agregarRegular(1, regular(7, "Sábana", 10), 2);
        ElementoCicloItem ausente = regular(99, "Fantasma", 5);
        ausente.setCantidadEnCiclo(1);

        assertDoesNotThrow(() -> staging.quitar(List.of(ausente), 1));
        assertDoesNotThrow(() -> staging.quitar(List.of(ausente), 8));
        assertDoesNotThrow(() -> staging.quitarInstanciaEquipo(1234));

        assertEquals(1, staging.pendientesDe(1).size(), "Lo que estaba cargado sigue igual");
    }

    @Test
    void hayPendientes_vuelveAFalseDespuesDeQuitarTodo() {
        ElementoCicloItem eq = equipo(20, "Caja instrumental", 3);
        staging.agregarRegular(1, regular(7, "Sábana", 10), 2);
        staging.agregarFraccionEquipo(2, fraccion(eq, 42));
        staging.agregarFraccionEquipo(3, fraccion(eq, 42));

        staging.quitar(staging.pendientesDe(1), 1);
        staging.quitar(staging.pendientesDe(2), 2);

        assertFalse(staging.hayPendientes());
        assertTrue(staging.lavarropasConPendientes().isEmpty());
    }

    @Test
    void aplicarSobreDisponibles_despuesDeUnaBaja_muestraElItemConSuCantidadCompleta() {
        staging.agregarRegular(1, regular(7, "Sábana", 4), 4);
        // Con las 4 unidades staged el ítem desaparecía de disponibles.
        assertTrue(staging.aplicarSobreDisponibles(new ArrayList<>(List.of(regular(7, "Sábana", 4)))).isEmpty());

        staging.quitar(staging.pendientesDe(1), 1);

        List<ElementoCicloItem> resultado =
                staging.aplicarSobreDisponibles(new ArrayList<>(List.of(regular(7, "Sábana", 4))));

        assertEquals(1, resultado.size());
        assertEquals(0, buscar(resultado, 7).getCantidadEnCiclo());
        assertEquals(4, buscar(resultado, 7).getCantidadDisponible());
    }

    @Test
    void lavarropasDeInstancia_cuentaLasCardsQueContienenLaInstancia() {
        ElementoCicloItem eq = equipo(20, "Caja instrumental", 3);
        staging.agregarFraccionEquipo(1, fraccion(eq, 42));
        staging.agregarFraccionEquipo(2, fraccion(eq, 42));

        assertEquals(2, staging.lavarropasDeInstancia(42));
        assertEquals(0, staging.lavarropasDeInstancia(43), "Instancia inexistente → 0");
        assertEquals(0, staging.lavarropasDeInstancia(null), "Un regular no tiene instancia");
    }
}
