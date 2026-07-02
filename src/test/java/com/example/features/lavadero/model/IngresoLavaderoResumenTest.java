package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class IngresoLavaderoResumenTest {

    @Test
    void toString_conFechaYDatos_formatoEsperado() {
        IngresoLavaderoResumen r = new IngresoLavaderoResumen(
            1, "Hospital Central",
            LocalDateTime.of(2024, 3, 15, 10, 30),
            new BigDecimal("8.50"), 3
        );

        assertEquals("Hospital Central - 15/03/2024 10:30 - 8.50 kg - 3 bolsas", r.toString());
    }

    @Test
    void toString_fechaNull_muestraGuion() {
        IngresoLavaderoResumen r = new IngresoLavaderoResumen(
            2, "Clinica Sur", null, new BigDecimal("5.00"), 2
        );

        assertTrue(r.toString().contains("- -"), "Fecha null debe mostrarse como guión");
    }

    @Test
    void getters_retornanValoresConstructor() {
        LocalDateTime fecha = LocalDateTime.of(2024, 1, 1, 0, 0);
        IngresoLavaderoResumen r = new IngresoLavaderoResumen(
            7, "Clinica Norte", fecha, new BigDecimal("12.00"), 4
        );

        assertEquals(7, r.getId());
        assertEquals("Clinica Norte", r.getClienteNombre());
        assertEquals(fecha, r.getFechaIngreso());
        assertEquals(0, new BigDecimal("12.00").compareTo(r.getPesoTotalKg()));
        assertEquals(4, r.getCantBolsas());
    }
}
