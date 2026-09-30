package com.example.features.ajustes.controller.helpers;

import com.example.common.constants.Constantes.Mensajes;

import java.util.Arrays;
import java.util.Optional;

/**
 * Feedback inmediato del formulario de cambio de password, para dar en el EDT <b>sin hashear</b>:
 * verificar la actual es PBKDF2 y tarda cientos de ms a propósito, así que un campo vacío o un
 * error de tipeo en la repetición no deberían esperar a la base ni a un hilo de fondo.
 *
 * <p>Clase plana, sin Swing y sin estado. <b>Sólo mira lo que se resuelve con los tres campos a la
 * vista</b>: vacíos y nueva ≠ repetida. El largo mínimo y "igual a la actual" los decide
 * {@code PasswordEliminacionService.cambiar}, que revalida todo igual: duplicarlos acá dejaría dos
 * lugares que mantener.</p>
 *
 * <p>Sólo {@code char[]}: nada se convierte a {@code String}, y esta clase no modifica los arrays
 * (los limpia quien los creó).</p>
 */
public final class ValidadorCambioPassword {

    private ValidadorCambioPassword() {}

    /** @return el mensaje para el operador, o vacío si los tres campos están bien para enviar */
    public static Optional<String> validar(char[] actual, char[] nueva, char[] repetida) {
        if (esVacia(actual) || esVacia(nueva) || esVacia(repetida)) {
            return Optional.of(Mensajes.PASSWORD_CAMPOS_VACIOS);
        }
        if (!Arrays.equals(nueva, repetida)) {
            return Optional.of(Mensajes.PASSWORD_NUEVA_NO_COINCIDE);
        }
        return Optional.empty();
    }

    private static boolean esVacia(char[] campo) {
        return campo == null || campo.length == 0;
    }
}
