package com.example.common.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de las reglas puras de {@link Validador}, que quedaron sin cubrir cuando el hallazgo #5
 * separó la validación de las restricciones de tecleo de Swing
 * ({@code com.example.ui.common.RestriccionesCampo}).
 *
 * <p>Los cuatro métodos que quedan son los que tienen llamadores reales: {@code esEmailValido} y
 * {@code esNumeroPositivo} se borraron por muertos en el refactor-clean del 2026-08-27.
 */
class ValidadorTest {

    // ── noEstaVacio ──────────────────────────────────────────────────────────

    @Test
    void noEstaVacio_textoConContenido_esTrue() {
        assertTrue(Validador.noEstaVacio("Hospital Italiano"));
    }

    @Test
    void noEstaVacio_nullVacioOSoloEspacios_esFalse() {
        assertFalse(Validador.noEstaVacio(null));
        assertFalse(Validador.noEstaVacio(""));
        assertFalse(Validador.noEstaVacio("   "));
        assertFalse(Validador.noEstaVacio("\t\n"));
    }

    // ── esFormatoNombre ──────────────────────────────────────────────────────

    @Test
    void esFormatoNombre_apellidoYNombre_esTrue() {
        assertTrue(Validador.esFormatoNombre("Perez Juan"));
        assertTrue(Validador.esFormatoNombre("Perez Juan Carlos"));
    }

    @Test
    void esFormatoNombre_aceptaAcentosYEnie() {
        assertTrue(Validador.esFormatoNombre("Muñoz Ramón"));
        assertTrue(Validador.esFormatoNombre("Ángel Íñiguez"));
    }

    @Test
    void esFormatoNombre_unaSolaPalabra_esFalse() {
        assertFalse(Validador.esFormatoNombre("Perez"));
    }

    @Test
    void esFormatoNombre_conNumerosOSimbolos_esFalse() {
        assertFalse(Validador.esFormatoNombre("Perez Juan 2"));
        assertFalse(Validador.esFormatoNombre("Perez, Juan"));
        assertFalse(Validador.esFormatoNombre("Perez-Juan Carlos"));
    }

    @Test
    void esFormatoNombre_vacioONull_esFalse() {
        assertFalse(Validador.esFormatoNombre(null));
        assertFalse(Validador.esFormatoNombre("   "));
    }

    // ── soloNumeros ──────────────────────────────────────────────────────────

    @Test
    void soloNumeros_digitos_esTrue() {
        assertTrue(Validador.soloNumeros("0"));
        assertTrue(Validador.soloNumeros("123456"));
    }

    @Test
    void soloNumeros_conEspaciosAlrededor_esFalse() {
        // noEstaVacio hace trim, pero la regex corre sobre el texto original.
        assertFalse(Validador.soloNumeros(" 123 "));
    }

    @Test
    void soloNumeros_conLetrasSignosOVacio_esFalse() {
        assertFalse(Validador.soloNumeros("12a"));
        assertFalse(Validador.soloNumeros("-5"));
        assertFalse(Validador.soloNumeros("1.5"));
        assertFalse(Validador.soloNumeros(""));
        assertFalse(Validador.soloNumeros(null));
    }

    // ── detectarDuplicados ───────────────────────────────────────────────────

    @Test
    void detectarDuplicados_devuelveSoloLosRepetidos() {
        Set<String> dups = Validador.detectarDuplicados(
            List.of("Tijera", "Pinza", "Tijera", "Bisturi", "Pinza"));

        assertEquals(Set.of("Tijera", "Pinza"), dups);
    }

    @Test
    void detectarDuplicados_sinRepetidos_devuelveVacio() {
        assertTrue(Validador.detectarDuplicados(List.of("Tijera", "Pinza")).isEmpty());
    }

    @Test
    void detectarDuplicados_ignoraVaciosYNulls() {
        Set<String> dups = Validador.detectarDuplicados(
            Arrays.asList("", "", null, null, "Tijera", "Tijera"));

        assertEquals(Set.of("Tijera"), dups);
    }

    @Test
    void detectarDuplicados_noModificaLaColeccionOriginal() {
        List<String> original = List.of("Tijera", "Tijera");

        Validador.detectarDuplicados(original);

        assertEquals(List.of("Tijera", "Tijera"), original);
    }

    // ── la clase no se instancia ─────────────────────────────────────────────

    @Test
    void constructor_esInaccesible() throws Exception {
        var ctor = Validador.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        var causa = assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance)
            .getCause();

        assertInstanceOf(UnsupportedOperationException.class, causa);
    }
}
