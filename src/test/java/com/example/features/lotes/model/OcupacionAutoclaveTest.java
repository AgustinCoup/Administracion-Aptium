package com.example.features.lotes.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre la aritmética de capacidad que antes vivía en CapacidadCalculatorImpl y
 * duplicada como literales en el controller y los helpers de Swing.
 */
class OcupacionAutoclaveTest {

    // ── porcentaje ───────────────────────────────────────────────────────────

    @Test
    void porcentaje_capacidadCero_retornaCero() {
        assertEquals(0, new OcupacionAutoclave(50, 0).porcentaje());
    }

    @Test
    void porcentaje_capacidadNegativa_retornaCero() {
        assertEquals(0, new OcupacionAutoclave(50, -10).porcentaje());
    }

    @Test
    void porcentaje_vacio_retornaCero() {
        assertEquals(0, new OcupacionAutoclave(0, 100).porcentaje());
    }

    @Test
    void porcentaje_mitad_retorna50() {
        assertEquals(50, new OcupacionAutoclave(50, 100).porcentaje());
    }

    @Test
    void porcentaje_lleno_retorna100() {
        assertEquals(100, new OcupacionAutoclave(200, 200).porcentaje());
    }

    @Test
    void porcentaje_sobreCapacidad_retornaMasDe100() {
        assertEquals(150, new OcupacionAutoclave(300, 200).porcentaje());
    }

    @Test
    void porcentaje_trunca_noRedondea() {
        // 2/3 = 66,67 % → la barra y el diálogo muestran el mismo 66
        assertEquals(66, new OcupacionAutoclave(2, 3).porcentaje());
    }

    // ── estaSobrecargado ─────────────────────────────────────────────────────

    @Test
    void estaSobrecargado_exactamenteLleno_retornaFalse() {
        assertFalse(new OcupacionAutoclave(100, 100).estaSobrecargado());
    }

    @Test
    void estaSobrecargado_hayEspacio_retornaFalse() {
        assertFalse(new OcupacionAutoclave(30, 100).estaSobrecargado());
    }

    @Test
    void estaSobrecargado_seExcede_retornaTrue() {
        assertTrue(new OcupacionAutoclave(101, 100).estaSobrecargado());
    }

    @Test
    void estaSobrecargado_excesoMenorAlUnoPorCiento_retornaTrue() {
        // Con división entera el porcentaje trunca a 100 y el exceso pasaba
        // desapercibido; la comparación directa sí lo detecta.
        assertEquals(100, new OcupacionAutoclave(1001, 1000).porcentaje());
        assertTrue(new OcupacionAutoclave(1001, 1000).estaSobrecargado());
    }

    // ── estaPocoCargado ──────────────────────────────────────────────────────

    @Test
    void estaPocoCargado_capacidadCero_retornaTrue() {
        assertTrue(new OcupacionAutoclave(80, 0).estaPocoCargado());
    }

    @Test
    void estaPocoCargado_bajoElUmbral_retornaTrue() {
        assertTrue(new OcupacionAutoclave(79, 100).estaPocoCargado());
    }

    @Test
    void estaPocoCargado_exactamenteEnElUmbral_retornaFalse() {
        assertFalse(new OcupacionAutoclave(80, 100).estaPocoCargado());
    }

    @Test
    void estaPocoCargado_sobreElUmbral_retornaFalse() {
        assertFalse(new OcupacionAutoclave(90, 100).estaPocoCargado());
    }

    @Test
    void estaPocoCargado_lleno_retornaFalse() {
        assertFalse(new OcupacionAutoclave(100, 100).estaPocoCargado());
    }

    @Test
    void umbralAdvertencia_esOchentaPorCiento() {
        assertEquals(80, OcupacionAutoclave.UMBRAL_ADVERTENCIA);
    }
}
