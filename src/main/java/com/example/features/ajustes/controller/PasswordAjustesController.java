package com.example.features.ajustes.controller;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ValidationException;
import com.example.features.ajustes.controller.helpers.ValidadorCambioPassword;
import com.example.features.ajustes.view.PanelPasswordEliminacion;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.seguridad.service.PasswordEliminacionService;
import com.example.ui.common.TareaUI;

import javax.swing.*;
import java.util.Arrays;
import java.util.Optional;

/**
 * Pestaña Seguridad de Ajustes: cambiar la password de eliminación, pidiendo la actual.
 *
 * <p><b>Las passwords son {@code char[]} de punta a punta.</b> Se toman en el EDT con
 * {@code getPassword()}, viajan al hilo de fondo dentro del {@code leer} y las limpia quien las
 * creó —este controller— en un {@code finally} <b>dentro del mismo lambda de {@code leer}</b>:
 * no en {@code pintar} ni en {@code siFalla}, que no corren si la tarea se cancela o si
 * {@code pintar} lanza. Nunca se construye un {@code String} con ellas ({@code getText()}
 * tampoco), y el nombre de la tarea es fijo, sin datos.</p>
 *
 * <p>El hash (PBKDF2, cientos de ms a propósito) corre en {@link TareaUI}, nunca en el EDT. El
 * feedback inmediato (vacíos, repetición distinta) sale de {@link ValidadorCambioPassword} sin
 * hashear; el service revalida todo igual.</p>
 */
public class PasswordAjustesController {

    private static final String NOMBRE_TAREA_CAMBIAR = "ajustes-cambiar-password";
    private static final String NOMBRE_TAREA_AVISO   = "ajustes-password-es-inicial";

    private final PanelPasswordEliminacion   panel;
    private final PasswordEliminacionService passwordService;

    public PasswordAjustesController(
        PantallaAjustes vista,
        PanelPasswordEliminacion panel,
        PasswordEliminacionService passwordService
    ) {
        this.panel           = panel;
        this.passwordService = passwordService;

        panel.setOnCambiar(this::cambiar);
        vista.setOnPestanaSeleccionada(PantallaAjustes.TAB_SEGURIDAD, this::cargarAviso);
    }

    private void cargarAviso() {
        // Sin siFalla de cara al operador: es un aviso informativo, y TareaUI ya loguea el fallo.
        TareaUI.<Boolean>nueva()
            .nombre(NOMBRE_TAREA_AVISO)
            .leer(passwordService::esInicial)
            .pintar(panel::setAvisoInicialVisible)
            .siFalla(e -> panel.setAvisoInicialVisible(false))
            .lanzar();
    }

    private void cambiar() {
        char[] actual   = panel.getPasswordActual();
        char[] nueva    = panel.getPasswordNueva();
        char[] repetida = panel.getPasswordRepetida();

        Optional<String> error = ValidadorCambioPassword.validar(actual, nueva, repetida);
        if (error.isPresent()) {
            limpiar(actual, nueva, repetida);
            mostrarError(error.get());
            return;
        }

        TareaUI.<Boolean>nueva()
            .nombre(NOMBRE_TAREA_CAMBIAR)
            .antes(() -> panel.setCambiarHabilitado(false))
            .leer(() -> {
                try {
                    passwordService.cambiar(actual, nueva, repetida);
                } finally {
                    limpiar(actual, nueva, repetida);
                }
                // Se relee en vez de suponer que ya no es la inicial: el aviso dice la verdad.
                return passwordService.esInicial();
            })
            .pintar(sigueSiendoInicial -> {
                panel.limpiarCampos();
                panel.setAvisoInicialVisible(sigueSiendoInicial);
                JOptionPane.showMessageDialog(panel, Mensajes.PASSWORD_CAMBIADA, "Aviso",
                    JOptionPane.INFORMATION_MESSAGE);
            })
            .siFalla(e -> {
                panel.limpiarCampos();
                mostrarError(mensajeDe(e));
            })
            .despues(() -> panel.setCambiarHabilitado(true))
            .lanzar();
    }

    private static String mensajeDe(Throwable e) {
        if (e instanceof ValidationException v) {
            return String.join("\n", v.getValidationErrors());
        }
        if (e instanceof BusinessException) {
            return e.getMessage();
        }
        return Mensajes.PASSWORD_CAMBIO_ERROR_TECNICO;
    }

    private static void limpiar(char[]... passwords) {
        for (char[] password : passwords) {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(panel, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
