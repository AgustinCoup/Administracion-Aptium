package com.example.common.dao;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlConcurrenciaTest {

    private static final String MENSAJE = "Otro usuario tocó esto. Revisá y volvé a confirmar.";

    // ── exigirFilaAfectada ───────────────────────────────────────────────────

    @Test
    void ceroFilas_lanzaConflicto() {
        assertThrows(ConflictoConcurrenciaException.class,
            () -> ControlConcurrencia.exigirFilaAfectada(0, MENSAJE));
    }

    @Test
    void ceroFilas_elMensajeLlegaIntactoAlUsuario() {
        ConflictoConcurrenciaException ex = assertThrows(ConflictoConcurrenciaException.class,
            () -> ControlConcurrencia.exigirFilaAfectada(0, MENSAJE));

        assertEquals(MENSAJE, ex.getMessage());
    }

    @Test
    void unaFila_noLanza() {
        assertDoesNotThrow(() -> ControlConcurrencia.exigirFilaAfectada(1, MENSAJE));
    }

    @Test
    void variasFilas_noLanza() {
        // Es un bug de la condición del WHERE, no un conflicto: se loguea y se deja seguir,
        // porque abortar acá revertiría una escritura que sí se aplicó.
        assertDoesNotThrow(() -> ControlConcurrencia.exigirFilaAfectada(3, MENSAJE));
    }

    // ── exigirFilasAfectadas ─────────────────────────────────────────────────

    @Test
    void batchConLasFilasEsperadas_noLanza() {
        assertDoesNotThrow(() -> ControlConcurrencia.exigirFilasAfectadas(4, 4, MENSAJE));
    }

    @Test
    void batchConMenosFilasDeLasEsperadas_lanzaConflicto() {
        ConflictoConcurrenciaException ex = assertThrows(ConflictoConcurrenciaException.class,
            () -> ControlConcurrencia.exigirFilasAfectadas(4, 3, MENSAJE));

        assertEquals(MENSAJE, ex.getMessage());
    }

    @Test
    void batchConMasFilasDeLasEsperadas_lanzaConflicto() {
        assertThrows(ConflictoConcurrenciaException.class,
            () -> ControlConcurrencia.exigirFilasAfectadas(4, 5, MENSAJE));
    }

    @Test
    void batchVacioEsperandoCero_noLanza() {
        assertDoesNotThrow(() -> ControlConcurrencia.exigirFilasAfectadas(0, 0, MENSAJE));
    }

    // ── contrato de la excepción ─────────────────────────────────────────────

    @Test
    void elConflictoEsUnaBusinessException_asiLosCatchExistentesLoRutean() {
        ConflictoConcurrenciaException ex = assertThrows(ConflictoConcurrenciaException.class,
            () -> ControlConcurrencia.exigirFilaAfectada(0, MENSAJE));

        assertInstanceOf(BusinessException.class, ex);
    }
}
