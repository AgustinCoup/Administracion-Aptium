package com.example.common.eliminacion;

import java.util.List;
import java.util.Objects;

/**
 * El resumen previo de un ingreso de Lavadero.
 *
 * <p><b>Declarado con los campos mínimos</b> para que {@link ResumenEliminacion} quede cerrado
 * desde el principio. Lo completa el Paso 4 de {@code plans/eliminar-ingresos.md} (cliente, peso,
 * elementos, derivados al CDE y el token de guarda: estado + ids de derivados).</p>
 */
public record ResumenIngresoLavadero(IngresoAEliminar ingreso, List<Bloqueo> bloqueos)
        implements ResumenEliminacion {

    public ResumenIngresoLavadero {
        Objects.requireNonNull(ingreso, "ingreso");
        if (ingreso.modulo() != ModuloIngreso.LAVADERO) {
            throw new IllegalArgumentException("Un ingreso del CDE se resume con ResumenEquipo");
        }
        bloqueos = List.copyOf(bloqueos);
    }
}
