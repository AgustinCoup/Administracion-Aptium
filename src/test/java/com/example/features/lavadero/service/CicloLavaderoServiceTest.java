package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.JabonCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CicloLavaderoServiceTest {

    @Mock
    private CicloLavaderoDAO dao;

    private CicloLavaderoService service;

    private static final JabonCatalogo SKIP  = new JabonCatalogo(1, "Skip");
    private static final JabonCatalogo LIDER = new JabonCatalogo(2, "Lider");

    @BeforeEach
    void setUp() {
        service = new CicloLavaderoService(dao);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new CicloLavaderoService(null));
    }

    // ── lanzarCiclo — validaciones ───────────────────────────────────────────

    @Test
    void lanzarCiclo_lavarropasNumeroMenorA1_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(0, SKIP, new BigDecimal("1.5"), false, false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_lavarropasNumeroMayorA13_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(14, SKIP, new BigDecimal("1.5"), false, false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_jabonNull_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, null, new BigDecimal("1.5"), false, false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_litrosJabonCero_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, SKIP, BigDecimal.ZERO, false, false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_litrosJabonNegativo_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, SKIP, new BigDecimal("-1"), false, false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_movimientosVacios_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, SKIP, new BigDecimal("1.5"), false, false, null, Collections.emptyList()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_cantidadCeroEnMovimiento_lanzaValidation() {
        List<ElementoCicloMovimiento> mov = List.of(new ElementoCicloMovimiento(1, 0));
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, SKIP, new BigDecimal("1.5"), false, false, null, mov));
        verifyNoInteractions(dao);
    }

    // ── lanzarCiclo — camino feliz ───────────────────────────────────────────

    @Test
    void lanzarCiclo_datosValidos_delegaADAO() {
        service.lanzarCiclo(1, SKIP, new BigDecimal("1.5"), false, false, null, movimientosValidos());
        verify(dao).lanzarCiclo(eq(1), eq(SKIP), any(), eq(false), eq(false), isNull(), anyList());
    }

    @Test
    void lanzarCiclo_conSuavizanteYPotenciadorYLitrosTotales_delegaADAO() {
        BigDecimal litrosTotales = new BigDecimal("30.00");
        service.lanzarCiclo(7, LIDER, new BigDecimal("2.0"), true, true, litrosTotales, movimientosValidos());
        verify(dao).lanzarCiclo(eq(7), eq(LIDER), any(), eq(true), eq(true), eq(litrosTotales), anyList());
    }

    // ── finalizarCiclo — validaciones ────────────────────────────────────────

    @Test
    void finalizarCiclo_idCeroONegativo_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.finalizarCiclo(0));
        verifyNoInteractions(dao);
    }

    // ── finalizarCiclo — camino feliz ────────────────────────────────────────

    @Test
    void finalizarCiclo_idValido_delegaADAO() {
        service.finalizarCiclo(5);
        verify(dao).finalizarCiclo(5);
    }

    // ── obtenerCiclosActivosPorLavarropas ────────────────────────────────────

    @Test
    void obtenerCiclosActivosPorLavarropas_delegaADAO() {
        when(dao.obtenerCiclosActivosPorLavarropas()).thenReturn(Collections.emptyMap());
        assertSame(Collections.emptyMap(), service.obtenerCiclosActivosPorLavarropas());
    }

    // ── obtenerElementosDisponiblesParaCiclo ─────────────────────────────────

    @Test
    void obtenerElementosDisponibles_delegaADAO() {
        when(dao.obtenerElementosDisponiblesParaCiclo()).thenReturn(Collections.emptyList());
        assertSame(Collections.emptyList(), service.obtenerElementosDisponiblesParaCiclo());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<ElementoCicloMovimiento> movimientosValidos() {
        return List.of(new ElementoCicloMovimiento(1, 3));
    }
}
