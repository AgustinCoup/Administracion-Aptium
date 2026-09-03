package com.example.common.exception;

/**
 * Excepción lanzada cuando una escritura se apoyaba en datos que otro usuario ya cambió.
 *
 * <p>Se diferencia de su padre {@link BusinessException} en el tiempo, no en la gravedad:
 * la {@code BusinessException} dice <em>"esto no se puede hacer"</em>; el conflicto dice
 * <em>"esto se podía hacer cuando lo leíste, y ya no"</em>. La operación era legítima contra
 * el estado que la pantalla mostró y dejó de serlo mientras el operador pensaba.</p>
 *
 * <p>Es una subclase de {@code BusinessException} a propósito: los controllers ya rutean esa
 * excepción como mensaje al usuario y no como error técnico, así que el conflicto llega bien
 * sin tocar un solo {@code catch}. Quien necesite distinguirlo lo hace con un
 * {@code instanceof} antes del catch general.</p>
 *
 * <p><b>Cómo se detecta:</b> la escritura lleva en el {@code WHERE} una condición que sólo es
 * verdadera si nadie tocó la fila desde que se leyó, y después se mira {@code executeUpdate()}.
 * {@code 0 filas afectadas} no es un error de base: es "la realidad ya no es la que viste".
 * Ver {@link com.example.common.dao.ControlConcurrencia}.</p>
 *
 * <p><b>El mensaje va directo al operador</b>, así que tiene que decirle qué cambió y qué
 * hacer — nunca sólo qué falló. Los textos están en
 * {@code Constantes.Mensajes.CONFLICTO_*}.</p>
 */
public class ConflictoConcurrenciaException extends BusinessException {

    public ConflictoConcurrenciaException(String message) {
        super(message);
    }

    public ConflictoConcurrenciaException(String message, Throwable cause) {
        super(message, cause);
    }
}
