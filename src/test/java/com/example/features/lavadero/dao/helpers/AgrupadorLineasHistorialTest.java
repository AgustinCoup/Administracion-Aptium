package com.example.features.lavadero.dao.helpers;

import com.example.features.lavadero.model.DestinoSalida;
import com.example.features.lavadero.model.LineaHistorial;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgrupadorLineasHistorialTest {

    private final AgrupadorLineasHistorial agrupador = new AgrupadorLineasHistorial();

    private static final LocalDateTime FIN_1   = LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final LocalDateTime FIN_2   = LocalDateTime.of(2026, 8, 20, 11, 0);
    private static final LocalDateTime LISTO   = LocalDateTime.of(2026, 8, 20, 15, 0);

    private FilaHistorialCruda fila(int instanciaId, int totalPartes, int lavarropas,
                                    LocalDateTime fechaFin, LocalDateTime fechaListo,
                                    DestinoSalida destino) {
        return new FilaHistorialCruda(instanciaId, totalPartes, "Equipo X", lavarropas,
            fechaFin, fechaListo, destino);
    }

    @Test
    void instanciaCompletaEnDosLavarropas_daUnaSolaLineaConLosDosNumeros() {
        List<FilaHistorialCruda> filas = List.of(
            fila(1, 2, 5, FIN_1, null, null),
            fila(1, 2, 3, FIN_2, null, null));

        List<LineaHistorial> lineas = agrupador.agrupar(filas);

        assertEquals(1, lineas.size());
        LineaHistorial linea = lineas.get(0);
        assertEquals("3, 5", linea.lavarropas());
        assertEquals(1, linea.cantidad(), "un equipo repartido es un equipo, no N");
        assertEquals(2, linea.totalPartes());
        assertTrue(linea.esFraccionDeEquipo());
    }

    @Test
    void instanciaCompleta_tomaLaFechaDeLaUltimaFraccionEnTerminar() {
        List<FilaHistorialCruda> filas = List.of(
            fila(1, 2, 1, FIN_1, null, null),
            fila(1, 2, 2, FIN_2, null, null));

        assertEquals(FIN_2, agrupador.agrupar(filas).get(0).fechaLavado());
    }

    @Test
    void instanciaConUnaParteEnCicloActivo_noEstaLavadaTodavia() {
        List<FilaHistorialCruda> filas = List.of(
            fila(1, 2, 1, FIN_1, null, null),
            fila(1, 2, 2, null, null, null));

        List<LineaHistorial> lineas = agrupador.agrupar(filas);

        assertEquals(1, lineas.size(), "la instancia incompleta se muestra igual, a diferencia de Salidas");
        assertNull(lineas.get(0).fechaLavado());
        assertEquals("1, 2", lineas.get(0).lavarropas());
    }

    @Test
    void instanciaYaDerivada_conservaSuDestinoYSuFechaListo() {
        List<FilaHistorialCruda> filas = List.of(
            fila(1, 2, 1, FIN_1, LISTO, DestinoSalida.CDE_OTROS),
            fila(1, 2, 2, FIN_2, LISTO, DestinoSalida.CDE_OTROS));

        List<LineaHistorial> lineas = agrupador.agrupar(filas);

        assertEquals(1, lineas.size(), "una instancia ya marcada se muestra, a diferencia de Salidas");
        assertEquals(DestinoSalida.CDE_OTROS, lineas.get(0).destino());
        assertEquals(LISTO, lineas.get(0).fechaListo());
    }

    @Test
    void dosInstanciasDistintas_noMezclanSusLavarropas() {
        List<FilaHistorialCruda> filas = List.of(
            fila(1, 2, 1, FIN_1, null, null),
            fila(1, 2, 2, FIN_2, null, null),
            fila(2, 2, 7, FIN_1, null, null),
            fila(2, 2, 8, FIN_2, null, null));

        List<LineaHistorial> lineas = agrupador.agrupar(filas);

        assertEquals(2, lineas.size());
        assertTrue(lineas.stream().anyMatch(l -> "1, 2".equals(l.lavarropas())));
        assertTrue(lineas.stream().anyMatch(l -> "7, 8".equals(l.lavarropas())));
    }

    @Test
    void listaVacia_daListaVacia() {
        assertTrue(agrupador.agrupar(List.of()).isEmpty());
    }
}
