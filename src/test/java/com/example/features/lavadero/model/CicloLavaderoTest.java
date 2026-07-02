package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CicloLavaderoTest {

    private static final JabonCatalogo SKIP  = new JabonCatalogo(1, "Skip");
    private static final JabonCatalogo LIDER = new JabonCatalogo(2, "Lider");

    @Test
    void estaActivo_cuandoFechaFinEsNull() {
        CicloLavadero ciclo = new CicloLavadero(1, 3, SKIP,
                new BigDecimal("1.5"), false, false, null,
                LocalDateTime.now(), null, "ACTIVO");
        assertTrue(ciclo.estaActivo());
    }

    @Test
    void noEstaActivo_cuandoTieneFechaFin() {
        LocalDateTime fin = LocalDateTime.now();
        CicloLavadero ciclo = new CicloLavadero(1, 3, SKIP,
                new BigDecimal("1.5"), false, false, null,
                LocalDateTime.now().minusHours(1), fin, "FINALIZADO");
        assertFalse(ciclo.estaActivo());
    }

    @Test
    void getters_devuelvenCamposCorrectos() {
        LocalDateTime inicio = LocalDateTime.of(2025, 6, 1, 10, 0);
        BigDecimal litrosJabon = new BigDecimal("2.00");
        BigDecimal litrosTotales = new BigDecimal("30.00");

        CicloLavadero ciclo = new CicloLavadero(5, 7, LIDER,
                litrosJabon, true, true, litrosTotales, inicio, null, "ACTIVO");

        assertEquals(5, ciclo.getId());
        assertEquals(7, ciclo.getLavarropasNumero());
        assertEquals(LIDER, ciclo.getJabon());
        assertEquals(0, litrosJabon.compareTo(ciclo.getLitrosJabon()));
        assertTrue(ciclo.isSuavizante());
        assertTrue(ciclo.isPotenciador());
        assertEquals(0, litrosTotales.compareTo(ciclo.getLitrosTotales()));
        assertEquals(inicio, ciclo.getFechaInicio());
        assertNull(ciclo.getFechaFin());
        assertEquals("ACTIVO", ciclo.getEstado());
    }

    @Test
    void getMateriales_devuelveListaDefensiva() {
        CicloLavadero ciclo = new CicloLavadero(1, 1, SKIP,
                new BigDecimal("1.0"), false, false, null,
                LocalDateTime.now(), null, "ACTIVO");
        ciclo.getMateriales().add(new ElementoCicloItem(1, 1, "x", 1, 0, "y"));
        assertEquals(0, ciclo.getMateriales().size());
    }
}
