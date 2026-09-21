package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CicloLavaderoTest {

    private static final JabonCatalogo SKIP  = new JabonCatalogo(1, "Skip");
    private static final JabonCatalogo LIDER = new JabonCatalogo(2, "Lider");
    private static final InsumoCatalogo SUAVIZANTE  = new InsumoCatalogo(1, "Suavizante", true);
    private static final InsumoCatalogo POTENCIADOR = new InsumoCatalogo(2, "Potenciador", true);

    @Test
    void estaActivo_cuandoFechaFinEsNull() {
        CicloLavadero ciclo = new CicloLavadero(1, 3, TipoLavado.SUCIO, SKIP,
                new BigDecimal("1.5"), List.of(),
                LocalDateTime.now(), null);
        assertTrue(ciclo.estaActivo());
    }

    @Test
    void noEstaActivo_cuandoTieneFechaFin() {
        LocalDateTime fin = LocalDateTime.now();
        CicloLavadero ciclo = new CicloLavadero(1, 3, TipoLavado.SUCIO, SKIP,
                new BigDecimal("1.5"), List.of(),
                LocalDateTime.now().minusHours(1), fin);
        assertFalse(ciclo.estaActivo());
    }

    @Test
    void getEstado_esFinalizado_cuandoTieneFechaFin() {
        CicloLavadero ciclo = new CicloLavadero(1, 3, TipoLavado.SUCIO, SKIP,
                new BigDecimal("1.5"), List.of(),
                LocalDateTime.now().minusHours(1), LocalDateTime.now());
        assertEquals(CicloLavadero.ESTADO_FINALIZADO, ciclo.getEstado());
    }

    @Test
    void getters_devuelvenCamposCorrectos() {
        LocalDateTime inicio = LocalDateTime.of(2025, 6, 1, 10, 0);
        BigDecimal litrosJabon = new BigDecimal("2.00");

        CicloLavadero ciclo = new CicloLavadero(5, 7, TipoLavado.SUCIO, LIDER,
                litrosJabon, List.of(SUAVIZANTE, POTENCIADOR), inicio, null);

        assertEquals(5, ciclo.getId());
        assertEquals(7, ciclo.getLavarropasNumero());
        assertEquals(TipoLavado.SUCIO, ciclo.getTipoLavado());
        assertEquals(LIDER, ciclo.getJabon());
        assertEquals(0, litrosJabon.compareTo(ciclo.getLitrosJabon()));
        assertEquals(List.of(SUAVIZANTE, POTENCIADOR), ciclo.getInsumos());
        assertEquals(inicio, ciclo.getFechaInicio());
        assertNull(ciclo.getFechaFin());
        assertEquals(CicloLavadero.ESTADO_ACTIVO, ciclo.getEstado());
    }

    @Test
    void getMateriales_devuelveListaDefensiva() {
        CicloLavadero ciclo = new CicloLavadero(1, 1, TipoLavado.LIMPIO, SKIP,
                new BigDecimal("1.0"), List.of(),
                LocalDateTime.now(), null);
        ciclo.getMateriales().add(new ElementoCicloItem(1, 1, "x", 1, 0, "y"));
        assertEquals(0, ciclo.getMateriales().size());
    }

    @Test
    void getInsumos_noExponeElEstadoInterno() {
        List<InsumoCatalogo> origen = new ArrayList<>(List.of(SUAVIZANTE));
        CicloLavadero ciclo = new CicloLavadero(1, 1, TipoLavado.LIMPIO, SKIP,
                new BigDecimal("1.0"), origen, LocalDateTime.now(), null);

        origen.add(POTENCIADOR);
        assertThrows(UnsupportedOperationException.class,
                () -> ciclo.getInsumos().add(POTENCIADOR));

        assertEquals(List.of(SUAVIZANTE), ciclo.getInsumos());
    }

    @Test
    void insumosNull_seNormalizaAListaVacia() {
        CicloLavadero ciclo = new CicloLavadero(1, 1, TipoLavado.LIMPIO, SKIP,
                new BigDecimal("1.0"), null, LocalDateTime.now(), null);

        assertNotNull(ciclo.getInsumos());
        assertTrue(ciclo.getInsumos().isEmpty());
    }

    @Test
    void configuracionCiclo_insumosNull_seNormalizaAListaVacia() {
        ConfiguracionCiclo config = new ConfiguracionCiclo(TipoLavado.LIMPIO, SKIP,
                new BigDecimal("1.0"), null);

        assertEquals(List.of(), config.insumos());
    }
}
