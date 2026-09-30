package com.example.common.exception;

/**
 * La password de una acción protegida no coincide con la guardada.
 *
 * <p>Es una {@link BusinessException} porque el mensaje va directo al operador y los controllers
 * ya la rutean así. <b>El mensaje es siempre un texto fijo de {@code Constantes.Mensajes}</b>: ni
 * la candidata ni nada derivado de ella viaja en la excepción, y tampoco se distingue "vacía" de
 * "distinta".</p>
 */
public class PasswordIncorrectaException extends BusinessException {

    public PasswordIncorrectaException(String mensajeFijo) {
        super(mensajeFijo);
    }
}
