package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DestinoSalidaTest {

    @Test
    void desdeBD_valorValido_devuelveElEnum() {
        assertEquals(DestinoSalida.FUERA_DE_FLUJO, DestinoSalida.desdeBD("FUERA_DE_FLUJO"));
        assertEquals(DestinoSalida.CDE_OTROS,       DestinoSalida.desdeBD("CDE_OTROS"));
    }

    @Test
    void desdeBD_ignoraMayusculasYEspacios() {
        assertEquals(DestinoSalida.CDE_OTROS, DestinoSalida.desdeBD("cde_otros"));
        assertEquals(DestinoSalida.CDE_OTROS, DestinoSalida.desdeBD("  CDE_OTROS  "));
    }

    @Test
    void desdeBD_null_devuelveNull() {
        assertNull(DestinoSalida.desdeBD(null));
    }

    @Test
    void desdeBD_valorDesconocido_devuelveNull() {
        assertNull(DestinoSalida.desdeBD("INEXISTENTE"));
    }
}
