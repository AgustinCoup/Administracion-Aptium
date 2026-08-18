package com.example.features.lavadero.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Destino final de una salida de lavadero, persistido en {@code salidas_lavadero.destino}.
 *
 * <p>A diferencia de {@link TipoLavado}, la columna es nullable y {@code NULL} significa "sin
 * destino todavía" (recién marcada como Listo), que es un estado legítimo. Por eso
 * {@link #desdeBD(String)} devuelve {@code null} ante un valor nulo, en vez de un default.</p>
 */
public enum DestinoSalida {

    FUERA_DE_FLUJO("Sale del flujo"),
    CDE_OTROS("Ingresa al CDE");

    private static final Logger log = LoggerFactory.getLogger(DestinoSalida.class);

    private final String nombre;

    DestinoSalida(String nombre) {
        this.nombre = nombre;
    }

    /** Texto legible para la UI. */
    public String getNombre() {
        return nombre;
    }

    /**
     * Convierte el valor almacenado en BD al enum correspondiente.
     * Devuelve {@code null} si el valor es nulo (sin destino todavía). Ante un valor desconocido
     * también devuelve {@code null} y lo deja registrado.
     */
    public static DestinoSalida desdeBD(String valor) {
        if (valor == null) return null;
        for (DestinoSalida d : values()) {
            if (d.name().equalsIgnoreCase(valor.trim())) return d;
        }
        log.warn("destino desconocido '{}' en salidas_lavadero; se asume sin destino", valor);
        return null;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
