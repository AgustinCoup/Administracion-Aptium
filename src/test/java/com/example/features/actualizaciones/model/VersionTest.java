package com.example.features.actualizaciones.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    @ParameterizedTest
    @CsvSource({
        "v1.2.3, 1, 2, 3",
        "1.2.3, 1, 2, 3",
        "v0.0.1, 0, 0, 1",
    })
    void parse_conYSinPrefijoV_extraeComponentes(String texto, int major, int minor, int patch) {
        Version version = Version.parse(texto);

        assertEquals(major, version.major());
        assertEquals(minor, version.minor());
        assertEquals(patch, version.patch());
        assertEquals(0, version.hotfix());
    }

    @ParameterizedTest
    @CsvSource({
        "v1.1.4.2, 1, 1, 4, 2",
        "1.1.4.2, 1, 1, 4, 2",
        "v1.1.4.10, 1, 1, 4, 10",
    })
    void parse_conCuartoSegmentoDeHotfix_extraeComponentes(
        String texto, int major, int minor, int patch, int hotfix
    ) {
        Version version = Version.parse(texto);

        assertEquals(major, version.major());
        assertEquals(minor, version.minor());
        assertEquals(patch, version.patch());
        assertEquals(hotfix, version.hotfix());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1.2", "1.2.3.4.5", "v1.2.x", "dev-SNAPSHOT"})
    void parse_formatoInvalido_lanzaIllegalArgument(String texto) {
        assertThrows(IllegalArgumentException.class, () -> Version.parse(texto));
    }

    @Test
    void parse_null_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse(null));
    }

    @Test
    void compareTo_mayorPorMajor_esMayor() {
        assertTrue(Version.parse("2.0.0").compareTo(Version.parse("1.9.9")) > 0);
    }

    @Test
    void compareTo_mayorPorMinor_esMayor() {
        assertTrue(Version.parse("1.3.0").compareTo(Version.parse("1.2.9")) > 0);
    }

    @Test
    void compareTo_mayorPorPatch_esMayor() {
        assertTrue(Version.parse("1.2.10").compareTo(Version.parse("1.2.3")) > 0);
    }

    @Test
    void compareTo_numericaNoLexicografica() {
        // "1.2.10" no debe compararse como menor que "1.2.3" por orden de string
        assertTrue(Version.parse("1.2.10").compareTo(Version.parse("1.2.3")) > 0);
        assertTrue(Version.parse("1.2.3").compareTo(Version.parse("1.2.10")) < 0);
    }

    @Test
    void compareTo_versionesIguales_esCero() {
        assertEquals(0, Version.parse("1.2.3").compareTo(Version.parse("v1.2.3")));
    }

    @Test
    void compareTo_mayorPorHotfix_esMayor() {
        assertTrue(Version.parse("v1.1.4.2").compareTo(Version.parse("v1.1.4.1")) > 0);
    }

    @Test
    void compareTo_sinHotfixEquivaleAHotfixCero() {
        assertEquals(0, Version.parse("1.1.4").compareTo(Version.parse("1.1.4.0")));
    }

    @Test
    void equals_mismosComponentes_sonIguales() {
        assertEquals(Version.parse("1.2.3"), Version.parse("v1.2.3"));
    }

    @Test
    void toString_conHotfixCero_omiteElSegmento() {
        assertEquals("1.1.4", Version.parse("1.1.4.0").toString());
    }

    @Test
    void toString_conHotfixDistintoDeCero_incluyeElSegmento() {
        assertEquals("1.1.4.2", Version.parse("v1.1.4.2").toString());
    }
}
