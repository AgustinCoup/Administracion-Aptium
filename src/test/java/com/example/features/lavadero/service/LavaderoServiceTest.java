package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.IngresoLavaderoDAO;
import com.example.features.lavadero.model.BolsaLavadero;
import com.example.features.lavadero.model.IngresoLavadero;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LavaderoServiceTest {

    @Mock
    private IngresoLavaderoDAO dao;

    private LavaderoService service;

    @BeforeEach
    void setUp() {
        service = new LavaderoService(dao);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new LavaderoService(null));
    }

    // ── registrarIngreso — validación de cliente ─────────────────────────────

    @Test
    void registrarIngreso_clienteCero_lanzaValidationException() {
        IngresoLavadero ingreso = ingresoConBolsa(0, new BigDecimal("3.50"));
        ValidationException ex = assertThrows(ValidationException.class,
            () -> service.registrarIngreso(ingreso));
        assertTrue(ex.getValidationErrors().stream().anyMatch(s -> s.toLowerCase().contains("cliente")));
        verifyNoInteractions(dao);
    }

    @Test
    void registrarIngreso_clienteNegativo_lanzaValidationException() {
        IngresoLavadero ingreso = ingresoConBolsa(-1, new BigDecimal("3.50"));
        assertThrows(ValidationException.class, () -> service.registrarIngreso(ingreso));
        verifyNoInteractions(dao);
    }

    // ── registrarIngreso — validación de bolsas ──────────────────────────────

    @Test
    void registrarIngreso_sinBolsas_lanzaValidationException() {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(1);
        ValidationException ex = assertThrows(ValidationException.class,
            () -> service.registrarIngreso(ingreso));
        assertTrue(ex.getValidationErrors().stream().anyMatch(s -> s.toLowerCase().contains("bolsa")));
        verifyNoInteractions(dao);
    }

    @Test
    void registrarIngreso_bolsaConPesoCero_lanzaValidationException() {
        IngresoLavadero ingreso = ingresoConBolsa(1, BigDecimal.ZERO);
        assertThrows(ValidationException.class, () -> service.registrarIngreso(ingreso));
        verifyNoInteractions(dao);
    }

    @Test
    void registrarIngreso_bolsaConPesoNegativo_lanzaValidationException() {
        IngresoLavadero ingreso = ingresoConBolsa(1, new BigDecimal("-1.00"));
        assertThrows(ValidationException.class, () -> service.registrarIngreso(ingreso));
        verifyNoInteractions(dao);
    }

    @Test
    void registrarIngreso_bolsaConPesoNulo_lanzaValidationException() {
        IngresoLavadero ingreso = ingresoConBolsa(1, null);
        assertThrows(ValidationException.class, () -> service.registrarIngreso(ingreso));
        verifyNoInteractions(dao);
    }

    // ── registrarIngreso — camino feliz ──────────────────────────────────────

    @Test
    void registrarIngreso_datosValidos_delegaADAOYRetornaTrue() {
        IngresoLavadero ingreso = ingresoConBolsa(1, new BigDecimal("5.00"));
        when(dao.guardar(ingreso)).thenReturn(true);

        assertTrue(service.registrarIngreso(ingreso));
        verify(dao).guardar(ingreso);
    }

    @Test
    void registrarIngreso_daoRetornaFalse_retornaFalse() {
        IngresoLavadero ingreso = ingresoConBolsa(1, new BigDecimal("5.00"));
        when(dao.guardar(ingreso)).thenReturn(false);

        assertFalse(service.registrarIngreso(ingreso));
        verify(dao).guardar(ingreso);
    }

    @Test
    void registrarIngreso_variasBolsasValidas_delegaADAO() {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(5);
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("3.50")));
        ingreso.agregarBolsa(new BolsaLavadero(new BigDecimal("7.00")));
        when(dao.guardar(ingreso)).thenReturn(true);

        assertTrue(service.registrarIngreso(ingreso));
        verify(dao).guardar(ingreso);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private IngresoLavadero ingresoConBolsa(int clienteId, BigDecimal peso) {
        IngresoLavadero ingreso = new IngresoLavadero();
        ingreso.setClienteId(clienteId);
        ingreso.agregarBolsa(new BolsaLavadero(peso));
        return ingreso;
    }
}
