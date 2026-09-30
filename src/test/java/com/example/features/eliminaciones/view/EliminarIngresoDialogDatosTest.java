package com.example.features.eliminaciones.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EliminarIngresoDialogDatosTest {

    @Test
    void limpiar_pisaLaPasswordConCeros_yDejaElMotivo() {
        EliminarIngresoDialog.Datos datos =
            new EliminarIngresoDialog.Datos("error de carga", "secreto".toCharArray());

        datos.limpiar();

        assertArrayEquals(new char[7], datos.password());
        assertEquals("error de carga", datos.motivo(), "el motivo se conserva: se reusa al reabrir el diálogo");
    }

    @Test
    void limpiar_esIdempotente() {
        EliminarIngresoDialog.Datos datos = new EliminarIngresoDialog.Datos("m", "abc".toCharArray());

        datos.limpiar();
        datos.limpiar();

        assertArrayEquals(new char[3], datos.password());
    }

    @Test
    void toString_noMuestraLaPassword() {
        EliminarIngresoDialog.Datos datos = new EliminarIngresoDialog.Datos("m", "secreto".toCharArray());

        String texto = datos.toString();

        assertFalse(texto.contains("secreto"));
        assertFalse(texto.contains("[C@"), "tampoco la identidad del array");
    }
}
