package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccionSalidaTest {

    @Test
    void fueraDeFlujo_persisteDestinoFueraDeFlujo() {
        assertEquals(DestinoSalida.FUERA_DE_FLUJO, AccionSalida.FUERA_DE_FLUJO.getDestinoPersistido());
    }

    @Test
    void ambasAccionesDeCDE_persistenCdeOtros() {
        assertEquals(DestinoSalida.CDE_OTROS, AccionSalida.CDE_CLIENTE.getDestinoPersistido());
        assertEquals(DestinoSalida.CDE_OTROS, AccionSalida.CDE_APTIUM.getDestinoPersistido());
    }
}
