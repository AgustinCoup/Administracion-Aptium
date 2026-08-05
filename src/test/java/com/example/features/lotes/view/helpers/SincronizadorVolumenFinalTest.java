package com.example.features.lotes.view.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre la regla de negocio del diálogo de lanzamiento: el volumen final
 * sigue al calculado (ortopedias + litros por ingreso) hasta que el usuario
 * lo edita a mano, y ahí deja de seguirlo. Sin Swing ni H2.
 */
class SincronizadorVolumenFinalTest {

    @Test
    void sinEdicionManual_sigueElTotalCalculado() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(5, 100);

        assertEquals(15, sync.onLitrosIngresoChange(10));
        assertEquals(30, sync.onLitrosIngresoChange(25));
    }

    @Test
    void trasEdicionManual_dejaDeSeguirElTotalCalculado() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(5, 100);
        sync.onLitrosIngresoChange(10);

        sync.onVolumenFinalEditadoPorUsuario(50);

        assertEquals(50, sync.onLitrosIngresoChange(20));
        assertEquals(50, sync.onLitrosIngresoChange(99));
    }

    @Test
    void totalCalculado_seSigueActualizandoAunqueEditadoAMano() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(5, 100);
        sync.onVolumenFinalEditadoPorUsuario(50);

        sync.onLitrosIngresoChange(20);

        assertEquals(25, sync.totalCalculado());
        assertEquals(50, sync.getVolumenFinal());
    }

    @Test
    void totalCalculado_superaCapacidad_seClampeaAlTope() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(5, 20);

        assertEquals(20, sync.onLitrosIngresoChange(30));
        assertEquals(35, sync.totalCalculado());
    }

    @Test
    void textoAdvertencia_sinDiscrepanciaYSobre80Porciento_estaVacio() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(0, 100);
        sync.onLitrosIngresoChange(90);

        assertEquals(" ", sync.textoAdvertencia());
    }

    @Test
    void textoAdvertencia_bajoCapacidad_avisaPorcentaje() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(0, 100);
        sync.onLitrosIngresoChange(50);

        assertTrue(sync.textoAdvertencia().contains("80%"));
    }

    @Test
    void textoAdvertencia_editadoAMano_avisaAjusteManual() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(0, 100);
        sync.onLitrosIngresoChange(90);
        sync.onVolumenFinalEditadoPorUsuario(85);

        assertTrue(sync.textoAdvertencia().contains("ajustado manualmente"));
    }

    @Test
    void textoCalculado_muestraTotalYCapacidad() {
        SincronizadorVolumenFinal sync = new SincronizadorVolumenFinal(5, 100);
        sync.onLitrosIngresoChange(10);

        assertEquals("Volumen calculado: 15 / 100", sync.textoCalculado());
    }
}
