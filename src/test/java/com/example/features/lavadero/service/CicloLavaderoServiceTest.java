package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.model.ConfiguracionCiclo;
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
            service.lanzarCiclo(0, configValida(), movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_lavarropasNumeroMayorA13_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(14, configValida(), movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_configNull_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, null, movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_jabonNull_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, config(null, new BigDecimal("1.5")), movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_litrosJabonCero_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, config(SKIP, BigDecimal.ZERO), movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_litrosJabonNegativo_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, config(SKIP, new BigDecimal("-1")), movimientosValidos()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_movimientosVacios_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, configValida(), Collections.emptyList()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarCiclo_cantidadCeroEnMovimiento_lanzaValidation() {
        List<ElementoCicloMovimiento> mov = List.of(new ElementoCicloMovimiento(1, 0));
        assertThrows(ValidationException.class, () ->
            service.lanzarCiclo(1, configValida(), mov));
        verifyNoInteractions(dao);
    }

    // ── lanzarCiclo — camino feliz ───────────────────────────────────────────

    @Test
    void lanzarCiclo_datosValidos_delegaADAO() {
        service.lanzarCiclo(1, configValida(), movimientosValidos());
        verify(dao).lanzarCiclo(eq(1), eq(configValida()), anyList());
    }

    @Test
    void lanzarCiclo_conSuavizanteYPotenciadorYLitrosTotales_delegaADAO() {
        ConfiguracionCiclo config = new ConfiguracionCiclo(
            LIDER, new BigDecimal("2.0"), true, true, new BigDecimal("30.00"));
        service.lanzarCiclo(7, config, movimientosValidos());
        verify(dao).lanzarCiclo(eq(7), eq(config), anyList());
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

    private ConfiguracionCiclo configValida() {
        return config(SKIP, new BigDecimal("1.5"));
    }

    private ConfiguracionCiclo config(JabonCatalogo jabon, BigDecimal litrosJabon) {
        return new ConfiguracionCiclo(jabon, litrosJabon, false, false, null);
    }
}
