package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.TipoJabon;
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
            service.lanzarCiclo(0, TipoJabon.JABON_LIQUIDO, new BigDecimal("1.5"), false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_lavarropasNumeroMayorA13_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(14, TipoJabon.JABON_LIQUIDO, new BigDecimal("1.5"), false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_tipoJabonNull_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, null, new BigDecimal("1.5"), false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_litrosJabonCero_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, TipoJabon.JABON_LIQUIDO, BigDecimal.ZERO, false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_litrosJabonNegativo_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, TipoJabon.JABON_LIQUIDO, new BigDecimal("-1"), false, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_movimientosVacios_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, TipoJabon.JABON_LIQUIDO, new BigDecimal("1.5"), false, null, Collections.emptyList()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_cantidadCeroEnMovimiento_lanzaValidation() {
        List<ElementoCicloMovimiento> mov = List.of(new ElementoCicloMovimiento(1, 0));
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, TipoJabon.JABON_LIQUIDO, new BigDecimal("1.5"), false, null, mov));
        verifyNoInteractions(dao);
    }

    // ── lanzarCiclo — camino feliz ───────────────────────────────────────────

    @Test
    void lanzarCiclo_datosValidos_delegaADAO() {
        service.lanzarCiclo(1, TipoJabon.JABON_LIQUIDO, new BigDecimal("1.5"), false, null, movimientosValidos());
        verify(dao).lanzarCiclo(eq(1), eq(TipoJabon.JABON_LIQUIDO), any(), eq(false), isNull(), anyList());
    }

    @Test
    void lanzarCiclo_conSuavizanteYLitrosTotales_delegaADAO() {
        BigDecimal litrosTotales = new BigDecimal("30.00");
        service.lanzarCiclo(7, TipoJabon.DESENGRASANTE, new BigDecimal("2.0"), true, litrosTotales, movimientosValidos());
        verify(dao).lanzarCiclo(eq(7), eq(TipoJabon.DESENGRASANTE), any(), eq(true), eq(litrosTotales), anyList());
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
