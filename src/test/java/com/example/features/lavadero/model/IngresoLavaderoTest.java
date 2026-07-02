package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngresoLavaderoTest {

    @Test
    void getPesoTotal_sinBolsas_retornaCero() {
        IngresoLavadero ingreso = new IngresoLavadero();

        assertEquals(0, BigDecimal.ZERO.compareTo(ingreso.getPesoTotal()));
    }

    @Test
    void getPesoTotal_unasBolsas_sumaCorrectamente() {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("3.50")));
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("2.25")));

        assertEquals(0, new BigDecimal("5.75").compareTo(ingreso.getPesoTotal()));
    }

    @Test
    void getPesoTotal_bolsaUnica_retornaSuPeso() {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("10.00")));

        assertEquals(0, new BigDecimal("10.00").compareTo(ingreso.getPesoTotal()));
    }

    @Test
    void getBolsas_retornaListaInmutable() {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("1.00")));

        List<BolsaLavadero> bolsas = ingreso.getBolsas();

        assertThrows(UnsupportedOperationException.class, () -> bolsas.add(new BolsaLavadero(new BigDecimal("2.00"))));
    }

    @Test
    void agregarBolsa_incrementaCantidad() {
        IngresoLavadero ingreso = new IngresoLavadero();

        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("1.00")));
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("2.00")));

        assertEquals(2, ingreso.getBolsas().size());
    }
}
