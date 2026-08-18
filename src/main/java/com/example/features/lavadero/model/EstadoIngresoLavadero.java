package com.example.features.lavadero.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estado de un ingreso de lavadero. Se persiste {@link #name()} en {@code ingresos_lavadero.estado}.
 */
public enum EstadoIngresoLavadero {

    PENDIENTE,
    CLASIFICADO,
    LAVADO,
    FINALIZADO;

    private static final Logger log = LoggerFactory.getLogger(EstadoIngresoLavadero.class);

    /**
     * Convierte el valor almacenado en BD al enum correspondiente.
     * Si el valor es nulo o desconocido devuelve {@link #PENDIENTE} y lo deja registrado.
     */
    public static EstadoIngresoLavadero desdeBD(String valor) {
        if (valor == null) {
            log.warn("estado nulo en ingresos_lavadero; se asume {}", PENDIENTE);
            return PENDIENTE;
        }
        for (EstadoIngresoLavadero e : values()) {
            if (e.name().equalsIgnoreCase(valor.trim())) return e;
        }
        log.warn("estado desconocido '{}' en ingresos_lavadero; se asume {}", valor, PENDIENTE);
        return PENDIENTE;
    }
}
