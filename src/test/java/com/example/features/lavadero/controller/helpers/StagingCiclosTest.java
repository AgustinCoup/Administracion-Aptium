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
}
