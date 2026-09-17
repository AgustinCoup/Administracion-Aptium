package com.example.common.paginacion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginaTest {

    private static Pagina<String> conTotal(long totalFilas) {
        return new Pagina<>(List.of("fila"), 1, 50, totalFilas);
    }

    // --- totalPaginas: los bordes de 50 son donde esto se rompe ---

    @Test
    void totalPaginas_sinFilas_esUnaYNoCero() {
        // "Página 1 de 0" es un bug visible: sin resultados hay una página, vacía.
        Pagina<String> vacia = new Pagina<>(List.of(), 1, 50, 0);

        assertEquals(1, vacia.totalPaginas());
        assertTrue(vacia.estaVacia());
    }

    @Test
    void totalPaginas_unaFila_esUna() {
        assertEquals(1, conTotal(1).totalPaginas());
    }

    @Test
    void totalPaginas_cuarentaYNueveFilas_esUna() {
        assertEquals(1, conTotal(49).totalPaginas());
    }

    @Test
    void totalPaginas_exactamenteCincuenta_esUnaYNoDos() {
        assertEquals(1, conTotal(50).totalPaginas());
    }

    @Test
    void totalPaginas_cincuentaYUna_esDos() {
        assertEquals(2, conTotal(51).totalPaginas());
    }

    @Test
    void totalPaginas_multiploExacto_noAgregaUnaPaginaDeMas() {
        assertEquals(4, conTotal(200).totalPaginas());
        assertEquals(5, conTotal(201).totalPaginas());
    }

    // --- Inmutabilidad ---

    @Test
    void contenido_noCambiaAlMutarLaListaDeOrigen() {
        List<String> origen = new ArrayList<>(List.of("a", "b"));
        Pagina<String> pagina = new Pagina<>(origen, 1, 50, 2);

        origen.add("c");
        origen.set(0, "modificado");

        assertEquals(List.of("a", "b"), pagina.contenido());
    }

    @Test
    void contenido_noSePuedeModificarDesdeLaPagina() {
        Pagina<String> pagina = new Pagina<>(List.of("a"), 1, 50, 1);

        assertThrows(UnsupportedOperationException.class, () -> pagina.contenido().add("b"));
    }

    // --- Rango visible ("Mostrando X-Y de Z") ---

    @Test
    void rango_primeraPaginaCompleta() {
        Pagina<Integer> pagina = new Pagina<>(filas(50), 1, 50, 127);

        assertEquals(1, pagina.primeraFila());
        assertEquals(50, pagina.ultimaFila());
    }

    @Test
    void rango_ultimaPaginaIncompleta_saleDelContenidoRealNoDelTamanio() {
        Pagina<Integer> pagina = new Pagina<>(filas(27), 3, 50, 127);

        assertEquals(101, pagina.primeraFila());
        assertEquals(127, pagina.ultimaFila());
    }

    @Test
    void rango_paginaVacia_esCeroACero() {
        Pagina<Integer> pagina = new Pagina<>(List.of(), 1, 50, 0);

        assertEquals(0, pagina.primeraFila());
        assertEquals(0, pagina.ultimaFila());
    }

    // --- Bordes de navegación ---

    @Test
    void esPrimeraYEsUltima_conUnaSolaPagina_sonLasDos() {
        Pagina<String> pagina = new Pagina<>(List.of("a"), 1, 50, 1);

        assertTrue(pagina.esPrimera());
        assertTrue(pagina.esUltima());
    }

    @Test
    void esUltima_enLaPaginaDelMedio_esFalso() {
        Pagina<Integer> pagina = new Pagina<>(filas(50), 2, 50, 127);

        assertFalse(pagina.esPrimera());
        assertFalse(pagina.esUltima());
    }

    @Test
    void esUltima_enUnaPaginaMasAllaDelTotal_esVerdadero() {
        // El operador tenía abierta la página 7 y otro borró filas: la barra no ofrece "siguiente".
        Pagina<Integer> pagina = new Pagina<>(List.of(), 7, 50, 20);

        assertTrue(pagina.esUltima());
    }

    // --- unica() ---

    @Test
    void unica_envuelveTodoEnUnaSolaPagina() {
        Pagina<Integer> pagina = Pagina.unica(filas(137));

        assertEquals(1, pagina.numeroPagina());
        assertEquals(1, pagina.totalPaginas());
        assertEquals(137, pagina.totalFilas());
        assertEquals(137, pagina.contenido().size());
    }

    @Test
    void unica_conListaVacia_noRompePorTamanioCero() {
        Pagina<Integer> pagina = Pagina.unica(List.of());

        assertEquals(1, pagina.totalPaginas());
        assertTrue(pagina.estaVacia());
    }

    // --- Validación del constructor ---

    @Test
    void numeroPaginaCero_esRechazado_porqueLaNumeracionEsBaseUno() {
        assertThrows(IllegalArgumentException.class,
                () -> new Pagina<>(List.of(), 0, 50, 0));
    }

    @Test
    void tamanioPaginaCero_esRechazado_porqueDivideAlCalcularElTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> new Pagina<>(List.of(), 1, 0, 0));
    }

    @Test
    void totalFilasNegativo_esRechazado() {
        assertThrows(IllegalArgumentException.class,
                () -> new Pagina<>(List.of(), 1, 50, -1));
    }

    private static List<Integer> filas(int cantidad) {
        List<Integer> filas = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            filas.add(i);
        }
        return filas;
    }
}
