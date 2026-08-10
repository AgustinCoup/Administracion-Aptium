package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TipoLavadoTest {

    @Test
    void desdeBD_valorValido_devuelveElEnum() {
        assertEquals(TipoLavado.LIMPIO,  TipoLavado.desdeBD("LIMPIO"));
        assertEquals(TipoLavado.SUCIO,   TipoLavado.desdeBD("SUCIO"));
        assertEquals(TipoLavado.PODRIDO, TipoLavado.desdeBD("PODRIDO"));
    }

    @Test
    void desdeBD_ignoraMayusculasYEspacios() {
        assertEquals(TipoLavado.PODRIDO, TipoLavado.desdeBD("podrido"));
        assertEquals(TipoLavado.LIMPIO,  TipoLavado.desdeBD("  Limpio  "));
    }

    @Test
    void desdeBD_null_devuelveSucio() {
        assertEquals(TipoLavado.SUCIO, TipoLavado.desdeBD(null));
    }

    @Test
    void desdeBD_valorDesconocido_devuelveSucio() {
        assertEquals(TipoLavado.SUCIO, TipoLavado.desdeBD("INEXISTENTE"));
        assertEquals(TipoLavado.SUCIO, TipoLavado.desdeBD(""));
    }

    @Test
    void getNombre_esElTextoDeUi() {
        assertEquals("Limpio",  TipoLavado.LIMPIO.getNombre());
        assertEquals("Sucio",   TipoLavado.SUCIO.getNombre());
        assertEquals("Podrido", TipoLavado.PODRIDO.getNombre());
    }

    @Test
    void toString_devuelveElNombreLegible_paraElCombo() {
        assertEquals("Podrido", TipoLavado.PODRIDO.toString());
    }

    @Test
    void name_esLoQueSePersiste_yNoElNombreDeUi() {
        assertEquals("LIMPIO", TipoLavado.LIMPIO.name());
        assertNotEquals(TipoLavado.LIMPIO.name(), TipoLavado.LIMPIO.getNombre());
    }
}
