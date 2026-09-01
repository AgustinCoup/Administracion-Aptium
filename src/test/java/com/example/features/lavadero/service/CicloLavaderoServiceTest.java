package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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

    // ── lanzarTanda — validaciones ───────────────────────────────────────────

    @Test
    void lanzarTanda_lavarropasNumeroMenorA1_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.lanzarTanda(tandaDe(ciclo(0, configValida()))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_lavarropasNumeroMayorA13_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.lanzarTanda(tandaDe(ciclo(14, configValida()))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_configNull_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.lanzarTanda(tandaDe(ciclo(1, null))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_jabonNull_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarTanda(tandaDe(ciclo(1, config(null, new BigDecimal("1.5"))))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_tipoLavadoNull_lanzaValidation() {
        ConfiguracionCiclo sinTipo = new ConfiguracionCiclo(
            null, SKIP, new BigDecimal("1.5"), false, false, null);
        assertThrows(ValidationException.class, () -> service.lanzarTanda(tandaDe(ciclo(1, sinTipo))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_litrosJabonCero_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarTanda(tandaDe(ciclo(1, config(SKIP, BigDecimal.ZERO)))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_litrosJabonNegativo_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarTanda(tandaDe(ciclo(1, config(SKIP, new BigDecimal("-1"))))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_vacia_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.lanzarTanda(Collections.emptyList()));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_cicloSinLineas_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.lanzarTanda(tandaDe(new LanzamientoCiclo(1, configValida(), Collections.emptyList()))));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_cantidadCeroEnUnaLinea_lanzaValidation() {
        LanzamientoCiclo ciclo = new LanzamientoCiclo(1, configValida(),
            List.of(new LineaLanzamiento(1, 0)));
        assertThrows(ValidationException.class, () -> service.lanzarTanda(tandaDe(ciclo)));
        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_totalPartesCeroEnUnaFraccion_lanzaValidation() {
        LanzamientoCiclo ciclo = new LanzamientoCiclo(1, configValida(),
            List.of(new LineaLanzamiento(1, 1, 7, 0)));
        assertThrows(ValidationException.class, () -> service.lanzarTanda(tandaDe(ciclo)));
        verifyNoInteractions(dao);
    }

    /** Es la razón de validar la tanda entera antes de escribir: un ciclo malo la frena toda. */
    @Test
    void lanzarTanda_unSoloCicloInvalido_noEscribeNingunoDeLosOtros() {
        LanzamientoCiclo valido  = ciclo(1, configValida());
        LanzamientoCiclo sinJabon = ciclo(2, config(null, new BigDecimal("1.5")));

        assertThrows(ValidationException.class, () -> service.lanzarTanda(List.of(valido, sinJabon)));

        verifyNoInteractions(dao);
    }

    @Test
    void lanzarTanda_elMensajeDiceQueLavarropasFalta() {
        LanzamientoCiclo sinJabon = ciclo(5, config(null, new BigDecimal("1.5")));

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.lanzarTanda(tandaDe(sinJabon)));

        assertTrue(e.getValidationErrors().stream().anyMatch(msg -> msg.contains("Lavarropas #5")),
            "en una tanda el mensaje tiene que ubicar el lavarropas: " + e.getValidationErrors());
    }

    // ── lanzarTanda — camino feliz ───────────────────────────────────────────

    @Test
    void lanzarTanda_datosValidos_delegaADAO() {
        List<LanzamientoCiclo> tanda = tandaDe(ciclo(1, configValida()));
        service.lanzarTanda(tanda);
        verify(dao).lanzarTanda(tanda);
    }

    @Test
    void lanzarTanda_conSuavizanteYPotenciadorYLitrosTotales_delegaADAO() {
        ConfiguracionCiclo config = new ConfiguracionCiclo(
            TipoLavado.PODRIDO, LIDER, new BigDecimal("2.0"), true, true, new BigDecimal("30.00"));
        List<LanzamientoCiclo> tanda = tandaDe(ciclo(7, config));
        service.lanzarTanda(tanda);
        verify(dao).lanzarTanda(tanda);
    }

    @Test
    void lanzarTanda_variosLavarropasCompartiendoInstancia_delegaLaTandaEntera() {
        List<LanzamientoCiclo> tanda = List.of(
            new LanzamientoCiclo(1, configValida(), List.of(new LineaLanzamiento(1, 1, 7, 2))),
            new LanzamientoCiclo(2, configValida(), List.of(new LineaLanzamiento(1, 1, 7, 2))));

        service.lanzarTanda(tanda);

        verify(dao).lanzarTanda(tanda);
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

    private List<LanzamientoCiclo> tandaDe(LanzamientoCiclo ciclo) {
        return List.of(ciclo);
    }

    private LanzamientoCiclo ciclo(int lavarropasNumero, ConfiguracionCiclo config) {
        return new LanzamientoCiclo(lavarropasNumero, config, List.of(new LineaLanzamiento(1, 3)));
    }

    private ConfiguracionCiclo configValida() {
        return config(SKIP, new BigDecimal("1.5"));
    }

    private ConfiguracionCiclo config(JabonCatalogo jabon, BigDecimal litrosJabon) {
        return new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, litrosJabon, false, false, null);
    }
}
