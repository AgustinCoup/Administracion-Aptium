package com.example.common.paginacion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginadorEnMemoriaTest {

    private static final int TAMANIO = 50;

    private static Pagina<Integer> paginar(int cantidadDeFilas, int numeroPagina) {
        return PaginadorEnMemoria.paginar(filas(cantidadDeFilas),
                new CriteriosPagina(numeroPagina, TAMANIO));
    }

    // --- Los bordes de 50 ---

    @Test
    void sinFilas_devuelvePaginaVaciaConUnaSolaPagina() {
        Pagina<Integer> pagina = paginar(0, 1);

        assertTrue(pagina.estaVacia());
        assertEquals(0, pagina.totalFilas());
        assertEquals(1, pagina.totalPaginas());
    }

    @Test
    void unaFila_entraEnLaPrimeraPagina() {
        Pagina<Integer> pagina = paginar(1, 1);

        assertEquals(List.of(0), pagina.contenido());
        assertEquals(1, pagina.totalPaginas());
    }

    @Test
    void cuarentaYNueveFilas_unaSolaPaginaIncompleta() {
        Pagina<Integer> pagina = paginar(49, 1);

        assertEquals(49, pagina.contenido().size());
        assertEquals(1, pagina.totalPaginas());
        assertEquals(49, pagina.ultimaFila());
    }

    @Test
    void cincuentaFilas_unaSolaPaginaExacta() {
        Pagina<Integer> pagina = paginar(50, 1);

        assertEquals(50, pagina.contenido().size());
        assertEquals(1, pagina.totalPaginas());
    }

    @Test
    void cincuentaYUnaFilas_laSegundaPaginaTraeLaFilaQueSobra() {
        Pagina<Integer> primera = paginar(51, 1);
        Pagina<Integer> segunda = paginar(51, 2);

        assertEquals(2, primera.totalPaginas());
        assertEquals(50, primera.contenido().size());
        assertEquals(List.of(50), segunda.contenido());
        assertEquals(51, segunda.primeraFila());
        assertEquals(51, segunda.ultimaFila());
    }

    // --- Última página incompleta y orden conservado ---

    @Test
    void ultimaPaginaIncompleta_traeElResto_yEnElMismoOrden() {
        Pagina<Integer> pagina = paginar(127, 3);

        assertEquals(27, pagina.contenido().size());
        assertEquals(100, pagina.contenido().get(0));
        assertEquals(126, pagina.contenido().get(26));
        assertEquals(127, pagina.totalFilas());
        assertEquals(3, pagina.totalPaginas());
    }

    // --- Página fuera de rango: no lanza ---

    @Test
    void paginaMasAllaDelTotal_devuelvePaginaVaciaConElTotalCorrecto_yNoLanza() {
        // El operador tenía abierta la página 7 y otro operador borró filas.
        Pagina<Integer> pagina = paginar(20, 7);

        assertTrue(pagina.estaVacia());
        assertEquals(20, pagina.totalFilas());
        assertEquals(1, pagina.totalPaginas());
        assertEquals(7, pagina.numeroPagina());
    }

    @Test
    void paginaJustoDespuesDeLaUltima_devuelvePaginaVacia_yNoLanza() {
        Pagina<Integer> pagina = paginar(100, 3);

        assertTrue(pagina.estaVacia());
        assertEquals(100, pagina.totalFilas());
        assertEquals(2, pagina.totalPaginas());
    }

    @Test
    void paginaFueraDeRangoSobreListaVacia_noLanza() {
        assertDoesNotThrow(() -> paginar(0, 12));
    }

    // --- Inmutabilidad: la página no es una vista de la lista de origen ---

    @Test
    void laPaginaNoEsUnaVistaDeLaListaDeOrigen() {
        List<Integer> origen = new ArrayList<>(filas(10));
        Pagina<Integer> pagina = PaginadorEnMemoria.paginar(origen, new CriteriosPagina(1, 5));

        origen.clear();

        assertEquals(5, pagina.contenido().size());
        assertEquals(0, pagina.contenido().get(0));
    }

    @Test
    void noModificaLaListaDeOrigen() {
        List<Integer> origen = new ArrayList<>(filas(10));

        PaginadorEnMemoria.paginar(origen, new CriteriosPagina(2, 5));

        assertEquals(10, origen.size());
    }

    // --- CriteriosPagina ---

    @Test
    void offset_deLaPrimeraPagina_esCero() {
        assertEquals(0, CriteriosPagina.primera().offset());
    }

    @Test
    void offset_saltaLasPaginasAnteriores() {
        assertEquals(100, new CriteriosPagina(3, 50).offset());
    }

    @Test
    void offset_conUnNumeroDePaginaAbsurdo_noDesbordaANegativo() {
        assertTrue(new CriteriosPagina(Integer.MAX_VALUE, 50).offset() > 0);
    }

    @Test
    void conPagina_conservaElTamanio() {
        CriteriosPagina criterios = new CriteriosPagina(1, 25).conPagina(4);

        assertEquals(4, criterios.numeroPagina());
        assertEquals(25, criterios.tamanioPagina());
    }

    private static List<Integer> filas(int cantidad) {
        List<Integer> filas = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            filas.add(i);
        }
        return filas;
    }
}
