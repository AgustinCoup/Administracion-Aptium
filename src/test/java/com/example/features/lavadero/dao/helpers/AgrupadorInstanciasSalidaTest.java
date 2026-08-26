package com.example.features.lavadero.dao.helpers;

import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.SalidaLista;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgrupadorInstanciasSalidaTest {

    private final AgrupadorInstanciasSalida agrupador = new AgrupadorInstanciasSalida();

    private static final LocalDateTime FIN_1 = LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final LocalDateTime FIN_2 = LocalDateTime.of(2026, 8, 20, 11, 0);
    private static final LocalDateTime FIN_3 = LocalDateTime.of(2026, 8, 20, 12, 0);
    private static final LocalDateTime FIN_4 = LocalDateTime.of(2026, 8, 20, 13, 0);

    private FilaInstanciaEquipo fila(int instanciaId, int totalPartes, int elementoCicloId,
                                      int lavarropas, LocalDateTime fechaFin, int yaMarcada) {
        return new FilaInstanciaEquipo(instanciaId, 1, totalPartes, elementoCicloId, lavarropas,
            fechaFin, 100, 1, "Cliente", "Equipo X", yaMarcada);
    }

    // ── agruparPendientes ────────────────────────────────────────────────────

    @Test
    void instanciaCompletaConLasCuatroPartesTerminadas_daUnResultadoConLavarropasOrdenados() {
        List<FilaInstanciaEquipo> filas = List.of(
            fila(1, 4, 10, 3, FIN_1, 0),
            fila(1, 4, 11, 1, FIN_2, 0),
            fila(1, 4, 12, 4, FIN_3, 0),
            fila(1, 4, 13, 2, FIN_4, 0));

        List<ElementoLavadoPendiente> resultado = agrupador.agruparPendientes(filas);

        assertEquals(1, resultado.size());
        ElementoLavadoPendiente item = resultado.get(0);
        assertEquals("1, 2, 3, 4", item.lavarropas());
        assertEquals(FIN_4, item.fechaFinCiclo());
        assertTrue(item.esInstanciaDeEquipo());
        assertEquals(1, item.instanciaEquipoId());
        assertNull(item.elementoCicloId());
    }

    @Test
    void instanciaConMenosPartesPresentesQueTotalPartes_noApareceEnElResultado() {
        List<FilaInstanciaEquipo> filas = List.of(
            fila(1, 4, 10, 1, FIN_1, 0),
            fila(1, 4, 11, 2, FIN_2, 0),
            fila(1, 4, 12, 3, FIN_3, 0));

        assertTrue(agrupador.agruparPendientes(filas).isEmpty());
    }

    @Test
    void instanciaCompletaConUnaParteEnCicloActivo_noApareceEnElResultado() {
        List<FilaInstanciaEquipo> filas = List.of(
            fila(1, 4, 10, 1, FIN_1, 0),
            fila(1, 4, 11, 2, FIN_2, 0),
            fila(1, 4, 12, 3, FIN_3, 0),
            fila(1, 4, 13, 4, null, 0));

        assertTrue(agrupador.agruparPendientes(filas).isEmpty());
    }

    @Test
    void instanciaCompletaYaMarcadaListo_noApareceEnElResultado() {
        List<FilaInstanciaEquipo> filas = List.of(
            fila(1, 4, 10, 1, FIN_1, 0),
            fila(1, 4, 11, 2, FIN_2, 0),
            fila(1, 4, 12, 3, FIN_3, 1),
            fila(1, 4, 13, 4, FIN_4, 0));

        assertTrue(agrupador.agruparPendientes(filas).isEmpty());
    }

    @Test
    void dosInstanciasDistintas_daDosResultadosSinMezclarLavarropas() {
        List<FilaInstanciaEquipo> filas = List.of(
            fila(1, 2, 10, 1, FIN_1, 0),
            fila(1, 2, 11, 2, FIN_2, 0),
            fila(2, 2, 20, 5, FIN_3, 0),
            fila(2, 2, 21, 6, FIN_4, 0));

        List<ElementoLavadoPendiente> resultado = agrupador.agruparPendientes(filas);

        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().anyMatch(r -> r.instanciaEquipoId() == 1 && "1, 2".equals(r.lavarropas())));
        assertTrue(resultado.stream().anyMatch(r -> r.instanciaEquipoId() == 2 && "5, 6".equals(r.lavarropas())));
    }

    @Test
    void listaVacia_daListaVacia() {
        assertTrue(agrupador.agruparPendientes(List.of()).isEmpty());
    }

    // ── agruparListas ────────────────────────────────────────────────────────

    private FilaInstanciaSalidaLista filaLista(int salidaId, int instanciaId, int lavarropas,
                                               LocalDateTime fechaFin, LocalDateTime fechaListo) {
        return new FilaInstanciaSalidaLista(salidaId, instanciaId, 1, fechaListo, lavarropas,
            fechaFin, 100, 1, "Cliente", "Equipo X");
    }

    @Test
    void salidaDeInstanciaConSusFraccionesEnDosLavarropas_daUnaFilaConLosDosLavarropas() {
        List<FilaInstanciaSalidaLista> filas = List.of(
            filaLista(50, 1, 2, FIN_1, FIN_3),
            filaLista(50, 1, 1, FIN_2, FIN_3));

        List<SalidaLista> resultado = agrupador.agruparListas(filas);

        assertEquals(1, resultado.size());
        SalidaLista salida = resultado.get(0);
        assertEquals(50, salida.salidaId());
        assertEquals(1, salida.instanciaEquipoId());
        assertNull(salida.elementoCicloId());
        assertEquals("1, 2", salida.lavarropas());
        assertEquals(FIN_2, salida.fechaFinCiclo());
        assertEquals(FIN_3, salida.fechaListo());
        assertTrue(salida.esInstanciaDeEquipo());
    }

    @Test
    void dosSalidasDeInstanciasDistintas_daDosResultados() {
        List<FilaInstanciaSalidaLista> filas = List.of(
            filaLista(50, 1, 1, FIN_1, FIN_3),
            filaLista(51, 2, 2, FIN_2, FIN_4));

        assertEquals(2, agrupador.agruparListas(filas).size());
    }

    @Test
    void listaVaciaDeListas_daListaVacia() {
        assertTrue(agrupador.agruparListas(List.of()).isEmpty());
    }
}
