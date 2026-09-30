package com.example.common.eliminacion;

import java.util.Objects;

/**
 * Qué ingreso se quiere eliminar: el módulo dice en qué tabla vive el {@code id}.
 *
 * @param modulo {@code ORTOPEDIA} → {@code equipos}, {@code OTROS} → {@code equipo_otros},
 *               {@code LAVADERO} → {@code ingresos_lavadero}
 * @param id     el id en esa tabla
 */
public record IngresoAEliminar(ModuloIngreso modulo, int id) {

    public IngresoAEliminar {
        Objects.requireNonNull(modulo, "modulo");
        if (id <= 0) {
            throw new IllegalArgumentException("id debe ser positivo: " + id);
        }
    }
}
