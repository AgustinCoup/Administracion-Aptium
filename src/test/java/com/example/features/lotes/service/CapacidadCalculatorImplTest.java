package com.example.features.lotes.service;

import com.example.features.lotes.model.LoteMaterialInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CapacidadCalculatorImplTest {

    private CapacidadCalculatorImpl calc;

    @BeforeEach
    void setUp() {
        calc = new CapacidadCalculatorImpl();
    }

    // ── calcularVolumenTotal ─────────────────────────────────────────────────

    @Test
    void calcularVolumenTotal_listaNull_retornaCero() {
        assertEquals(0, calc.calcularVolumenTotal(null));
    }

    @Test
    void calcularVolumenTotal_listaVacia_retornaCero() {
        assertEquals(0, calc.calcularVolumenTotal(Collections.emptyList()));
    }

    @Test
    void calcularVolumenTotal_unElemento_retornaVolumenTotal() {
        // cantidad=3, volumen=100 → volumenTotal = 300
        List<LoteMaterialInfo> lista = Collections.singletonList(
            new LoteMaterialInfo(1, 1, 100, "Guante", 3, 100)
        );
        assertEquals(300, calc.calcularVolumenTotal(lista));
    }

    @Test
    void calcularVolumenTotal_variosElementos_sumaCorrectamente() {
        List<LoteMaterialInfo> lista = Arrays.asList(
            new LoteMaterialInfo(1, 1, 100, "A", 2, 100),  // 200
            new LoteMaterialInfo(2, 1, 200, "B", 1, 50),   //  50
            new LoteMaterialInfo(3, 2, 300, "C", 5, 20)    // 100
        );
        assertEquals(350, calc.calcularVolumenTotal(lista));
    }

    // ── calcularPorcentajeUso ────────────────────────────────────────────────

    @Test
    void calcularPorcentajeUso_totalCero_retornaCero() {
        assertEquals(0, calc.calcularPorcentajeUso(50, 0));
    }

    @Test
    void calcularPorcentajeUso_totalNegativo_retornaCero() {
        assertEquals(0, calc.calcularPorcentajeUso(50, -10));
    }

    @Test
    void calcularPorcentajeUso_cero_retornaCero() {
        assertEquals(0, calc.calcularPorcentajeUso(0, 100));
    }

    @Test
    void calcularPorcentajeUso_mitad_retorna50() {
        assertEquals(50, calc.calcularPorcentajeUso(50, 100));
    }

    @Test
    void calcularPorcentajeUso_lleno_retorna100() {
        assertEquals(100, calc.calcularPorcentajeUso(200, 200));
    }

    @Test
    void calcularPorcentajeUso_sobreCapacidad_retornaMasDe100() {
        assertEquals(150, calc.calcularPorcentajeUso(300, 200));
    }

    // ── cabeLaCapacidad ──────────────────────────────────────────────────────

    @Test
    void cabeLaCapacidad_totalCero_retornaFalse() {
        assertFalse(calc.cabeLaCapacidad(0, 10, 0));
    }

    @Test
    void cabeLaCapacidad_totalNegativo_retornaFalse() {
        assertFalse(calc.cabeLaCapacidad(0, 10, -1));
    }

    @Test
    void cabeLaCapacidad_exactamenteLleno_retornaTrue() {
        assertTrue(calc.cabeLaCapacidad(50, 50, 100));
    }

    @Test
    void cabeLaCapacidad_seExcede_retornaFalse() {
        assertFalse(calc.cabeLaCapacidad(50, 51, 100));
    }

    @Test
    void cabeLaCapacidad_hayEspacio_retornaTrue() {
        assertTrue(calc.cabeLaCapacidad(30, 20, 100));
    }

    // ── requiereAdvertencia ──────────────────────────────────────────────────

    @Test
    void requiereAdvertencia_totalCero_retornaFalse() {
        assertFalse(calc.requiereAdvertencia(80, 0));
    }

    @Test
    void requiereAdvertencia_bajo80Porciento_retornaFalse() {
        assertFalse(calc.requiereAdvertencia(79, 100));
    }

    @Test
    void requiereAdvertencia_exactamente80Porciento_retornaTrue() {
        assertTrue(calc.requiereAdvertencia(80, 100));
    }

    @Test
    void requiereAdvertencia_sobre80Porciento_retornaTrue() {
        assertTrue(calc.requiereAdvertencia(90, 100));
    }

    @Test
    void requiereAdvertencia_lleno_retornaTrue() {
        assertTrue(calc.requiereAdvertencia(100, 100));
    }

    // ── calcularEspacioDisponible ────────────────────────────────────────────

    @Test
    void calcularEspacioDisponible_totalNegativo_retornaCero() {
        assertEquals(0, calc.calcularEspacioDisponible(0, -1));
    }

    @Test
    void calcularEspacioDisponible_usadoNegativo_retornaCero() {
        assertEquals(0, calc.calcularEspacioDisponible(-1, 100));
    }

    @Test
    void calcularEspacioDisponible_sobreCapacidad_retornaCero() {
        // No retorna negativo
        assertEquals(0, calc.calcularEspacioDisponible(120, 100));
    }

    @Test
    void calcularEspacioDisponible_normal_retornaDiferencia() {
        assertEquals(40, calc.calcularEspacioDisponible(60, 100));
    }

    @Test
    void calcularEspacioDisponible_vacio_retornaTotal() {
        assertEquals(100, calc.calcularEspacioDisponible(0, 100));
    }
}
