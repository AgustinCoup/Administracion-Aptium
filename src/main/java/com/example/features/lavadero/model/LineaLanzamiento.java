package com.example.features.lavadero.model;

/**
 * Una línea del staging lista para lanzarse, todavía con el id de instancia <b>de staging</b>:
 * el id real lo asigna la base recién dentro de la transacción que crea la instancia.
 *
 * @param instanciaStagingId {@code null} si la línea no es una fracción de equipo repartido
 * @param totalPartes        en cuántos lavarropas se repartió el equipo (1 si no se repartió)
 */
public record LineaLanzamiento(int elementoClasificacionId, int cantidad,
                               Integer instanciaStagingId, int totalPartes) {

    /** Línea común: no es fracción de ningún equipo repartido. */
    public LineaLanzamiento(int elementoClasificacionId, int cantidad) {
        this(elementoClasificacionId, cantidad, null, 1);
    }

    public boolean esFraccionDeEquipo() {
        return instanciaStagingId != null;
    }
}
