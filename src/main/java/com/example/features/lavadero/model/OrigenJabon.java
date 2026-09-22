package com.example.features.lavadero.model;

/**
 * De dónde salió el jabón que muestra una card de Ciclos.
 *
 * <p>Existe para sostener una sola regla, y conviene leerla en positivo: <b>una elección a mano
 * siempre pesa más que la automática</b>. Mientras el jabón sea {@link #AUTO}, cambiar el tipo de
 * lavado lo reemplaza por el default de ese tipo; apenas el operador elige uno en el combo pasa a
 * {@link #MANUAL} y el tipo deja de arrastrarlo.</p>
 *
 * <p>La decisión de qué hacer con esto no vive acá ni en la card, sino en
 * {@code SelectorJabonAutomatico}, que es una clase plana y testeable.</p>
 */
public enum OrigenJabon {

    /** Lo puso la carga automática por tipo de lavado, o todavía no lo puso nadie. */
    AUTO,

    /** Lo eligió el operador en el combo (o se lo pegó desde otra card). No se pisa. */
    MANUAL
}
