package com.example.common.exception;

/**
 * Excepción lanzada cuando una regla de negocio impide completar una operación que,
 * en sí misma, estaba bien formada.
 *
 * <p>Se diferencia de {@link ValidationException} en el momento y el origen: la validación
 * mira los datos que entraron (campos vacíos, formatos, rangos) y se puede resolver
 * corrigiendo el formulario; una {@code BusinessException} se detecta contra el estado real
 * de la base — típicamente porque ese estado cambió entre que la pantalla lo leyó y el
 * usuario apretó el botón.</p>
 *
 * <p>De {@link DatabaseException} se diferencia en que no hay ningún fallo técnico: la
 * consulta corrió bien y la respuesta fue "esto no se puede hacer".</p>
 *
 * <p>El mensaje va directo al usuario, así que tiene que decirle qué pasó y qué hacer
 * ("... ya no tiene 10 disponibles. Refrescá la pantalla.").</p>
 */
public class BusinessException extends ApplicationException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
