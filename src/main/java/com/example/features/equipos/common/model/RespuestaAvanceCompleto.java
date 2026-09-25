package com.example.features.equipos.common.model;

/**
 * Respuesta a "¿pasar los materiales seleccionados completos?". La devuelve la vista y la consume
 * el controller, por eso vive en {@code common/model} y no en el paquete de ninguno de los dos.
 */
public enum RespuestaAvanceCompleto {
    /** Sí: cada material pasa con todas sus unidades. */
    TODOS_COMPLETOS,
    /** No: se pide la cantidad de cada material, uno por uno. */
    ELEGIR_CANTIDADES,
    /** Cancelar o cerrar el diálogo: no se avanza nada. */
    CANCELAR
}
