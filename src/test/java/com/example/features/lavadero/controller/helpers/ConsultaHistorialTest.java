package com.example.features.lavadero.controller.helpers;

import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.model.FiltroHistorial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConsultaHistorialTest {

    private static final int TAMANIO = CriteriosPagina.primera().tamanioPagina();

    private static ConsultaHistorial consultaEnPagina(int numeroPagina) {
        return ConsultaHistorial.primeraPagina(FiltroHistorial.sinFiltros()).enPagina(numeroPagina);
    }

    private static Pagina<Object> leida(int numeroPagina, long total) {
        return new Pagina<>(List.of(), numeroPagina, TAMANIO, total);
    }

    @Test
    @DisplayName("una página más allá de la última se reubica en la última, con el total recién leído")
    void paginaMasAllaDeLaUltima_reubicaEnLaUltima() {
        // Había 101 filas (3 páginas); el operador borró la única de la 3 y quedan 100 (2 páginas).
        ConsultaHistorial consulta = consultaEnPagina(3);

        Optional<ConsultaHistorial> reubicada = consulta.reubicadaSi(leida(3, 100));

        assertTrue(reubicada.isPresent());
        assertEquals(2, reubicada.get().criterios().numeroPagina());
        assertEquals(100L, reubicada.get().totalConocido());
        assertEquals(consulta.filtro(), reubicada.get().filtro());
    }

    @Test
    @DisplayName("una página dentro de rango no se reubica")
    void paginaDentroDeRango_noReubica() {
        assertTrue(consultaEnPagina(2).reubicadaSi(leida(2, 100)).isEmpty());
        assertTrue(consultaEnPagina(1).reubicadaSi(leida(1, 100)).isEmpty());
    }

    @Test
    @DisplayName("la última página exacta no se reubica")
    void ultimaPagina_noReubica() {
        assertTrue(consultaEnPagina(3).reubicadaSi(leida(3, 101)).isEmpty());
    }

    @Test
    @DisplayName("sin filas queda en la primera página, y estar en la primera no es reubicar")
    void totalCero_quedaEnLaPrimera() {
        assertTrue(consultaEnPagina(1).reubicadaSi(leida(1, 0)).isEmpty());

        Optional<ConsultaHistorial> reubicada = consultaEnPagina(4).reubicadaSi(leida(4, 0));

        assertTrue(reubicada.isPresent());
        assertEquals(1, reubicada.get().criterios().numeroPagina());
        assertEquals(0L, reubicada.get().totalConocido());
    }
}
