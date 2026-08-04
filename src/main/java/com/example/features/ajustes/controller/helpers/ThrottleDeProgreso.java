package com.example.features.ajustes.controller.helpers;

import java.util.function.LongSupplier;

/**
 * Deja pasar como máximo una notificación por intervalo. Usado para no inundar el EDT con un
 * {@code invokeLater} por cada chunk leído durante una descarga (DescargaService notifica en
 * bloques de 8 KiB, órdenes de magnitud más seguido de lo que un humano puede percibir en una
 * barra de progreso). Lógica pura, sin Swing: testeable en aislamiento.
 */
public final class ThrottleDeProgreso {

    private final long intervaloMs;
    private final LongSupplier reloj;
    private long ultimaNotificacionMs;
    private boolean notifico = false;

    public ThrottleDeProgreso(long intervaloMs) {
        this(intervaloMs, System::currentTimeMillis);
    }

    ThrottleDeProgreso(long intervaloMs, LongSupplier reloj) {
        this.intervaloMs = intervaloMs;
        this.reloj = reloj;
    }

    /**
     * @return {@code true} si pasó al menos {@code intervaloMs} desde la última vez que devolvió
     *     {@code true} (o si es la primera llamada); en ese caso marca este instante como el
     *     último notificado.
     */
    public boolean permitir() {
        long ahora = reloj.getAsLong();
        if (notifico && ahora - ultimaNotificacionMs < intervaloMs) {
            return false;
        }
        ultimaNotificacionMs = ahora;
        notifico = true;
        return true;
    }
}
