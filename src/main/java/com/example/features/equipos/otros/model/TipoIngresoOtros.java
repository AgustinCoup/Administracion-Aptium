package com.example.features.equipos.otros.model;

/**
 * Distingue si un {@link EquipoOtros} se ingresó por Remito o por Detalles de materiales.
 *
 * <ul>
 *   <li>{@link #REMITO}   – se guarda un identificador ddmmaaaa-{id}, una cantidad global
 *                           y observaciones opcionales. Sin materiales detallados.</li>
 *   <li>{@link #DETALLES} – se cargan materiales individuales del catálogo {@code catalogo_otros}.</li>
 * </ul>
 */
public enum TipoIngresoOtros {

    REMITO("REMITO"),
    DETALLES("DETALLES");

    private final String nombre;

    TipoIngresoOtros(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    /**
     * Convierte el valor almacenado en BD al enum correspondiente.
     * Si el valor es nulo o desconocido, devuelve {@link #DETALLES} como valor por defecto.
     */
    public static TipoIngresoOtros desdeBD(String valor) {
        if (valor == null) return DETALLES;
        for (TipoIngresoOtros t : values()) {
            if (t.nombre.equalsIgnoreCase(valor)) return t;
        }
        return DETALLES;
    }
}