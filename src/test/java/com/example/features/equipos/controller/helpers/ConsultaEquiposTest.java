package com.example.features.equipos.controller.helpers;

import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConsultaEquiposTest {

    private static final int TAMANIO = CriteriosPagina.primera().tamanioPagina();

    private static ConsultaEquipos consulta(int paginaOrtopedias, int paginaOtros) {
        return ConsultaEquipos.primeraPagina(FiltroEquipos.sinFiltros())
            .ortopediasEnPagina(paginaOrtopedias)
            .otrosEnPagina(paginaOtros);
    }

    /** Lo que leyó la base para las dos grillas: (página, total) de ortopedias y de otros. */
    private static PaginasEquipos leidas(int paginaOrt, long totalOrt, int paginaOtros, long totalOtros) {
        return new PaginasEquipos(
            new Pagina<Equipo>(List.of(), paginaOrt, TAMANIO, totalOrt),
            new Pagina<EquipoOtros>(List.of(), paginaOtros, TAMANIO, totalOtros));
    }

    @Test
    @DisplayName("ortopedias más allá de la última se reubica en la última; la página de otros no se mueve")
    void ortopediasMasAllaDeLaUltima_reubicaSoloOrtopedias() {
        ConsultaEquipos consulta = consulta(3, 2);

        Optional<ConsultaEquipos> reubicada =
            consulta.reubicadaSi(leidas(3, 100, 2, 80));

        assertTrue(reubicada.isPresent());
        assertEquals(2, reubicada.get().ortopedias().numeroPagina());
        assertEquals(2, reubicada.get().otros().numeroPagina());
        assertEquals(100L, reubicada.get().totalOrtopedias());
        assertEquals(80L, reubicada.get().totalOtros());
    }

    @Test
    @DisplayName("otros más allá de la última se reubica en la última; la página de ortopedias no se mueve")
    void otrosMasAllaDeLaUltima_reubicaSoloOtros() {
        ConsultaEquipos consulta = consulta(2, 5);

        Optional<ConsultaEquipos> reubicada =
            consulta.reubicadaSi(leidas(2, 100, 5, 120));

        assertTrue(reubicada.isPresent());
        assertEquals(2, reubicada.get().ortopedias().numeroPagina());
        assertEquals(3, reubicada.get().otros().numeroPagina());
    }

    @Test
    @DisplayName("las dos grillas fuera de rango se reubican, cada una en su última")
    void lasDosFueraDeRango_seReubicanPorSeparado() {
        Optional<ConsultaEquipos> reubicada =
            consulta(4, 6).reubicadaSi(leidas(4, 100, 6, 0));

        assertTrue(reubicada.isPresent());
        assertEquals(2, reubicada.get().ortopedias().numeroPagina());
        assertEquals(1, reubicada.get().otros().numeroPagina());
    }

    @Test
    @DisplayName("dos páginas dentro de rango no se reubican")
    void paginasDentroDeRango_noReubica() {
        assertTrue(consulta(2, 3).reubicadaSi(leidas(2, 100, 3, 101)).isEmpty());
        assertTrue(consulta(1, 1).reubicadaSi(leidas(1, 0, 1, 5)).isEmpty());
    }

    @Test
    @DisplayName("sin filas la grilla queda en la primera página")
    void totalCero_quedaEnLaPrimera() {
        Optional<ConsultaEquipos> reubicada =
            consulta(2, 1).reubicadaSi(leidas(2, 0, 1, 0));

        assertTrue(reubicada.isPresent());
        assertEquals(1, reubicada.get().ortopedias().numeroPagina());
    }
}
