package com.example.features.lavadero.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipoSubdivisionDialogTest {

    @Test
    void fraccionUnoDeUno() {
        assertEquals("1/1", EquipoSubdivisionDialog.calcularFraccion(1));
    }

    @Test
    void fraccionUnoDeTres() {
        assertEquals("1/3", EquipoSubdivisionDialog.calcularFraccion(3));
    }

    @Test
    void fraccionCeroEsGuion() {
        assertEquals("—", EquipoSubdivisionDialog.calcularFraccion(0));
    }
}
