package com.example.ui.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GuardaRefrescoTest {

    @Test
    void sinGuard_refrescaSinConsultarAlConfirmador() {
        GuardaRefresco guarda = new GuardaRefresco(
            null, "mensaje", null,
            m -> { fail("no debe consultar al confirmador sin guard"); return false; });

        assertTrue(guarda.debeRefrescar());
    }

    @Test
    void conPendientes_confirmadorDiceNo_noRefrescaNiDescarta() {
        AtomicInteger descartes = new AtomicInteger();
        GuardaRefresco guarda = new GuardaRefresco(
            () -> true, "mensaje", descartes::incrementAndGet, m -> false);

        assertFalse(guarda.debeRefrescar());
        assertEquals(0, descartes.get());
    }

    @Test
    void conPendientes_confirmadorDiceSi_refrescaYDescartaUnaSolaVez() {
        AtomicInteger descartes = new AtomicInteger();
        AtomicReference<String> mensajeVisto = new AtomicReference<>();
        GuardaRefresco guarda = new GuardaRefresco(
            () -> true, "se descartan los elementos", descartes::incrementAndGet,
            m -> { mensajeVisto.set(m); return true; });

        assertTrue(guarda.debeRefrescar());
        assertEquals(1, descartes.get());
        assertEquals("se descartan los elementos", mensajeVisto.get());
    }

    @Test
    void guardSinPendientes_refrescaYNuncaConsultaAlConfirmador() {
        AtomicInteger consultas = new AtomicInteger();
        GuardaRefresco guarda = new GuardaRefresco(
            () -> false, "mensaje", null,
            m -> { consultas.incrementAndGet(); return true; });

        assertTrue(guarda.debeRefrescar());
        assertEquals(0, consultas.get());
    }
}
