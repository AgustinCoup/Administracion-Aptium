package com.example.features.ajustes.controller.helpers;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottleDeProgresoTest {

    @Test
    void permitir_primeraLlamada_siempreDejaPasar() {
        AtomicLong reloj = new AtomicLong(0);
        ThrottleDeProgreso throttle = new ThrottleDeProgreso(100, reloj::get);

        assertTrue(throttle.permitir(), "la primera notificación nunca debe throttlearse");
    }

    @Test
    void permitir_dentroDelIntervalo_bloqueaLlamadasSubsiguientes() {
        AtomicLong reloj = new AtomicLong(0);
        ThrottleDeProgreso throttle = new ThrottleDeProgreso(100, reloj::get);

        assertTrue(throttle.permitir());
        reloj.set(50);
        assertFalse(throttle.permitir(), "no debe dejar pasar una segunda notificación antes de que venza el intervalo");
        reloj.set(99);
        assertFalse(throttle.permitir());
    }

    @Test
    void permitir_pasadoElIntervalo_dejaPasarDeNuevo() {
        AtomicLong reloj = new AtomicLong(0);
        ThrottleDeProgreso throttle = new ThrottleDeProgreso(100, reloj::get);

        assertTrue(throttle.permitir());
        reloj.set(100);
        assertTrue(throttle.permitir(), "debe dejar pasar apenas se cumple el intervalo");
        reloj.set(250);
        assertTrue(throttle.permitir());
    }

    @Test
    void permitir_muchasLlamadasEnRafaga_dejaPasarSoloUnaPorIntervalo() {
        AtomicLong reloj = new AtomicLong(0);
        ThrottleDeProgreso throttle = new ThrottleDeProgreso(100, reloj::get);

        int dejadasPasar = 0;
        for (int i = 0; i < 3000; i++) {
            reloj.set(i); // simula ~3000 chunks de 8 KiB llegando en ~3ms cada uno
            if (throttle.permitir()) {
                dejadasPasar++;
            }
        }

        assertTrue(dejadasPasar <= 31,
            "en 3000ms con intervalo de 100ms no deberían dejarse pasar más de ~30 notificaciones, dejó pasar " + dejadasPasar);
    }
}
