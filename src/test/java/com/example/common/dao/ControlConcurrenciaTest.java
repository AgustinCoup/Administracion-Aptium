package com.example.common.dao;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;

import static org.junit.jupiter.api.Assertions.*;

class ControlConcurrenciaTest {

    private static final String MENSAJE = "Otro usuario tocó esto. Revisá y volvé a confirmar.";

    // ── esContencionDeLock ───────────────────────────────────────────────────

    /**
     * El caso por el que existe la función: el <i>lock wait timeout</i> de MySQL llega como una
     * {@code SQLException} pelada con {@code SQLSTATE HY000}, no como
     * {@code SQLTransactionRollbackException}. Confiar en el tipo —la trampa obvia— deja pasar
     * justamente el desenlace más probable de una guarda que bloquea y espera.
     */
    @Test
    void lockWaitTimeoutDeMySQL_esContencionAunqueElTipoNoLoDiga() {
        assertTrue(ControlConcurrencia.esContencionDeLock(
            new SQLException("Lock wait timeout exceeded", "HY000", 1205)));
    }

    @Test
    void deadlock_esContencionPorElTipo() {
        assertTrue(ControlConcurrencia.esContencionDeLock(
            new SQLTransactionRollbackException("Deadlock found", "40001", 1213)));
    }

    @Test
    void lockTimeoutDeH2_esContencion() {
        assertTrue(ControlConcurrencia.esContencionDeLock(
            new SQLException("Timeout trying to lock table", "HYT00", 50200)));
    }

    @Test
    void errorTecnicoCualquiera_noEsContencion() {
        assertFalse(ControlConcurrencia.esContencionDeLock(
            new SQLException("Unknown column 'foo'", "42S22", 1054)));
    }

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
