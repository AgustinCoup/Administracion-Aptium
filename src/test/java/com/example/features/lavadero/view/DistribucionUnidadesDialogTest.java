package com.example.features.lavadero.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DistribucionUnidadesDialog extiende JDialog: en headless (así corren los tests del
 * repo) construir una Window lanza HeadlessException, así que -igual que
 * EquipoSubdivisionDialogTest- se testea sólo la lógica estática, no el diálogo en sí.
 */
class DistribucionUnidadesDialogTest {

    @Test
    void tildarTodasLlevaElSpinnerAlMaximo() {
        assertEquals(40, DistribucionUnidadesDialog.aplicarTodas(true, 40, 5));
    }

    @Test
    void destildarTodasRestauraElUltimoValorManual() {
        assertEquals(5, DistribucionUnidadesDialog.aplicarTodas(false, 40, 5));
    }
}
