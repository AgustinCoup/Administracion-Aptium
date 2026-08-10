package com.example.features.lavadero.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tipo de lavado con el que se lanza un ciclo. Es sólo un dato descriptivo: se elige, se guarda
 * y se muestra. No condiciona el jabón, los litros ni qué elementos pueden mezclarse.
 *
 * <p>Se persiste {@link #name()} ({@code LIMPIO}/{@code SUCIO}/{@code PODRIDO}) y no
 * {@link #getNombre()}, para desacoplar el texto visible del valor almacenado.</p>
 */
public enum TipoLavado {

    LIMPIO("Limpio"),
    SUCIO("Sucio"),
    PODRIDO("Podrido");

    private static final Logger log = LoggerFactory.getLogger(TipoLavado.class);

    private final String nombre;

    TipoLavado(String nombre) {
        this.nombre = nombre;
    }

    /** Texto legible para la UI. */
    public String getNombre() {
        return nombre;
    }

    /**
     * Convierte el valor almacenado en BD al enum correspondiente.
     * Si el valor es nulo o desconocido devuelve {@link #SUCIO} y lo deja registrado: la columna es
     * {@code NOT NULL}, así que llegar acá significa que alguien escribió la tabla por fuera del DAO.
     */
    public static TipoLavado desdeBD(String valor) {
        if (valor == null) {
            log.warn("tipo_lavado nulo en ciclos_lavadero; se asume {}", SUCIO);
            return SUCIO;
        }
        for (TipoLavado t : values()) {
            if (t.name().equalsIgnoreCase(valor.trim())) return t;
        }
        log.warn("tipo_lavado desconocido '{}' en ciclos_lavadero; se asume {}", valor, SUCIO);
        return SUCIO;
    }

    /** Lo que muestra el {@code JComboBox} de la card. */
    @Override
    public String toString() {
        return nombre;
    }
}
