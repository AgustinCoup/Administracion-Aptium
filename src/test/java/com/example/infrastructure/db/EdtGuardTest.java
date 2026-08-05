package com.example.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdtGuardTest {

    @AfterEach
    void limpiar() {
        EdtGuard.resetear();
        System.clearProperty(EdtGuard.PROP_ESTRICTO);
    }

    @Test
    @DisplayName("sin detector instalado nunca dispara")
    void sinDetector_noLanza() {
        System.setProperty(EdtGuard.PROP_ESTRICTO, "true");

        assertDoesNotThrow(EdtGuard::verificarFueraDelHiloUi);
    }

    @Test
    @DisplayName("fuera del hilo de UI no lanza aunque esté en modo estricto")
    void fueraDelHiloUi_noLanza() {
        EdtGuard.setDetectorHiloUi(() -> false);
        System.setProperty(EdtGuard.PROP_ESTRICTO, "true");

        assertDoesNotThrow(EdtGuard::verificarFueraDelHiloUi);
    }

    @Test
    @DisplayName("en el hilo de UI y modo estricto lanza IllegalStateException")
    void enHiloUiEstricto_lanza() {
        EdtGuard.setDetectorHiloUi(() -> true);
        System.setProperty(EdtGuard.PROP_ESTRICTO, "true");

        assertThrows(IllegalStateException.class, EdtGuard::verificarFueraDelHiloUi);
    }

    @Test
    @DisplayName("en el hilo de UI sin modo estricto solo advierte, no interrumpe")
    void enHiloUiNoEstricto_noLanza() {
        EdtGuard.setDetectorHiloUi(() -> true);

        assertDoesNotThrow(EdtGuard::verificarFueraDelHiloUi);
    }

    @Test
    @DisplayName("resetear vuelve al estado sin detector")
    void resetear_desactivaElDetector() {
        EdtGuard.setDetectorHiloUi(() -> true);
        System.setProperty(EdtGuard.PROP_ESTRICTO, "true");

        EdtGuard.resetear();

        assertDoesNotThrow(EdtGuard::verificarFueraDelHiloUi);
    }
}
