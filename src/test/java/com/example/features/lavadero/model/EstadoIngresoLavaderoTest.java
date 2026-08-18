package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EstadoIngresoLavaderoTest {

    @Test
    void desdeBD_valorValido_devuelveElEnum() {
        assertEquals(EstadoIngresoLavadero.PENDIENTE,  EstadoIngresoLavadero.desdeBD("PENDIENTE"));
        assertEquals(EstadoIngresoLavadero.CLASIFICADO, EstadoIngresoLavadero.desdeBD("CLASIFICADO"));
        assertEquals(EstadoIngresoLavadero.LAVADO,      EstadoIngresoLavadero.desdeBD("LAVADO"));
        assertEquals(EstadoIngresoLavadero.FINALIZADO,  EstadoIngresoLavadero.desdeBD("FINALIZADO"));
    }

    @Test
    void desdeBD_ignoraMayusculasYEspacios() {
        assertEquals(EstadoIngresoLavadero.LAVADO, EstadoIngresoLavadero.desdeBD("lavado"));
        assertEquals(EstadoIngresoLavadero.LAVADO, EstadoIngresoLavadero.desdeBD("  Lavado  "));
    }

    @Test
    void desdeBD_null_devuelvePendiente() {
        assertEquals(EstadoIngresoLavadero.PENDIENTE, EstadoIngresoLavadero.desdeBD(null));
    }

    @Test
    void desdeBD_valorDesconocido_devuelvePendiente() {
        assertEquals(EstadoIngresoLavadero.PENDIENTE, EstadoIngresoLavadero.desdeBD("INEXISTENTE"));
        assertEquals(EstadoIngresoLavadero.PENDIENTE, EstadoIngresoLavadero.desdeBD(""));
    }
}
