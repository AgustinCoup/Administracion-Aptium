package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CicloLavaderoTest {

    @Test
    void estaActivo_cuandoFechaFinEsNull() {
        CicloLavadero ciclo = new CicloLavadero(1, 3, TipoJabon.JABON_LIQUIDO,
                new BigDecimal("1.5"), false, null,
                LocalDateTime.now(), null, "ACTIVO");
        assertTrue(ciclo.estaActivo());
    }

    @Test
    void noEstaActivo_cuandoTieneFechaFin() {
        LocalDateTime fin = LocalDateTime.now();
        CicloLavadero ciclo = new CicloLavadero(1, 3, TipoJabon.JABON_LIQUIDO,
                new BigDecimal("1.5"), false, null,
                LocalDateTime.now().minusHours(1), fin, "FINALIZADO");
        assertFalse(ciclo.estaActivo());
    }

    @Test
    void getters_devuelvenCamposCorrectos() {
        LocalDateTime inicio = LocalDateTime.of(2025, 6, 1, 10, 0);
        BigDecimal litrosJabon = new BigDecimal("2.00");
        BigDecimal litrosTotales = new BigDecimal("30.00");

        CicloLavadero ciclo = new CicloLavadero(5, 7, TipoJabon.DESENGRASANTE,
                litrosJabon, true, litrosTotales, inicio, null, "ACTIVO");

        assertEquals(5, ciclo.getId());
        assertEquals(7, ciclo.getLavarropasNumero());
        assertEquals(TipoJabon.DESENGRASANTE, ciclo.getTipoJabon());
        assertEquals(0, litrosJabon.compareTo(ciclo.getLitrosJabon()));
        assertTrue(ciclo.isSuavizante());
        assertEquals(0, litrosTotales.compareTo(ciclo.getLitrosTotales()));
        assertEquals(inicio, ciclo.getFechaInicio());
        assertNull(ciclo.getFechaFin());
        assertEquals("ACTIVO", ciclo.getEstado());
    }

    @Test
    void getMateriales_devuelveListaDefensiva() {
        CicloLavadero ciclo = new CicloLavadero(1, 1, TipoJabon.JABON_LIQUIDO,
                new BigDecimal("1.0"), false, null,
                LocalDateTime.now(), null, "ACTIVO");
        ciclo.getMateriales().add(new ElementoCicloItem(1, 1, "x", 1, 0, "y"));
        assertEquals(0, ciclo.getMateriales().size());
    }
}
