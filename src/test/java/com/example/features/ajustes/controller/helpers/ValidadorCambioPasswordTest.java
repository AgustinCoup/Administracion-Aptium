package com.example.features.ajustes.controller.helpers;

import com.example.common.constants.Constantes.Mensajes;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorCambioPasswordTest {

    private static char[] c(String s) {
        return s.toCharArray();
    }

    @Test
    void vacios_actualVacia_pideCompletarTodo() {
        assertEquals(Optional.of(Mensajes.PASSWORD_CAMPOS_VACIOS),
            ValidadorCambioPassword.validar(c(""), c("nueva1"), c("nueva1")));
    }

    @Test
    void vacios_nuevaVacia_pideCompletarTodo() {
        assertEquals(Optional.of(Mensajes.PASSWORD_CAMPOS_VACIOS),
            ValidadorCambioPassword.validar(c("actual"), c(""), c("")));
    }

    @Test
    void vacios_repeticionVacia_pideCompletarTodo() {
        assertEquals(Optional.of(Mensajes.PASSWORD_CAMPOS_VACIOS),
            ValidadorCambioPassword.validar(c("actual"), c("nueva1"), c("")));
    }

    @Test
    void vacios_camposNulos_pideCompletarTodo() {
        assertEquals(Optional.of(Mensajes.PASSWORD_CAMPOS_VACIOS),
            ValidadorCambioPassword.validar(null, null, null));
    }

    @Test
    void distintas_nuevaYRepeticion_avisaQueNoCoinciden() {
        assertEquals(Optional.of(Mensajes.PASSWORD_NUEVA_NO_COINCIDE),
            ValidadorCambioPassword.validar(c("actual"), c("nueva1"), c("nueva2")));
    }

    @Test
    void ok_todoCompletoYCoincidente_noHayError() {
        assertTrue(ValidadorCambioPassword.validar(c("actual"), c("nueva1"), c("nueva1")).isEmpty());
    }

    @Test
    void noValidaElLargoNiLaIgualdadConLaActual_eso_lo_decide_el_service() {
        // Un chequeo local duplicaría reglas que el service ya aplica; acá sólo el feedback inmediato.
        assertTrue(ValidadorCambioPassword.validar(c("a"), c("b"), c("b")).isEmpty());
        assertTrue(ValidadorCambioPassword.validar(c("igual"), c("igual"), c("igual")).isEmpty());
    }

    @Test
    void noModificaLosArrays() {
        char[] actual = c("actual");
        char[] nueva = c("nueva1");
        char[] repetida = c("nueva1");

        ValidadorCambioPassword.validar(actual, nueva, repetida);

        assertArrayEquals(c("actual"), actual);
        assertArrayEquals(c("nueva1"), nueva);
        assertArrayEquals(c("nueva1"), repetida);
    }
}
