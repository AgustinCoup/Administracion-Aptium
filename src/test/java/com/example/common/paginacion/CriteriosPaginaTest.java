package com.example.common.paginacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.common.constants.Constantes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que este record tiene que sostener no es su aritmética sino sus dos convenciones: que el
 * número de página es <b>base 1</b> —y no base 0, que es lo que asume quien viene del
 * {@code OFFSET}— y que un número de página absurdo tiene que dar una página vacía, no un
 * {@code OFFSET} negativo. Lo segundo es lo que pasa cuando el operador tiene abierta una página
 * que un refresco acaba de dejar sin filas.
 */
class CriteriosPaginaTest {

    @Test
    @DisplayName("la primera página arranca en 1 con el tamaño estándar de la app")
    void primera_esBase1() {
        CriteriosPagina criterios = CriteriosPagina.primera();

        assertEquals(1, criterios.numeroPagina());
        assertEquals(Constantes.Paginacion.TAMANIO_PAGINA, criterios.tamanioPagina());
        assertEquals(0L, criterios.offset(), "la página 1 no saltea nada");
    }

    @Test
    @DisplayName("el offset saltea las páginas anteriores, no la propia")
    void offset_salteaLasAnteriores() {
        assertEquals(0L, new CriteriosPagina(1, 50).offset());
        assertEquals(50L, new CriteriosPagina(2, 50).offset());
        assertEquals(100L, new CriteriosPagina(3, 50).offset());
    }

    /**
     * El motivo por el que {@code offset()} devuelve {@code long}: con {@code int}, este producto
     * desborda y da un offset <b>negativo</b>, que MySQL rechaza con un error de sintaxis en vez de
     * devolver la página vacía que corresponde.
     */
    @Test
    @DisplayName("un número de página absurdo da un offset grande, nunca negativo")
    void offset_noDesborda() {
        long offset = new CriteriosPagina(Integer.MAX_VALUE, 50).offset();

        assertEquals((Integer.MAX_VALUE - 1L) * 50L, offset);
        assertEquals(true, offset > 0, "un int habría desbordado a negativo");
    }

    @Test
    @DisplayName("cambiar de página conserva el tamaño")
    void conPagina_conservaElTamanio() {
        CriteriosPagina otra = new CriteriosPagina(1, 25).conPagina(4);

        assertEquals(4, otra.numeroPagina());
        assertEquals(25, otra.tamanioPagina());
    }

    @Test
    @DisplayName("pagina(n) usa el tamaño estándar")
    void pagina_usaElTamanioEstandar() {
        assertEquals(Constantes.Paginacion.TAMANIO_PAGINA, CriteriosPagina.pagina(7).tamanioPagina());
        assertEquals(7, CriteriosPagina.pagina(7).numeroPagina());
    }

    @Test
    @DisplayName("la página 0 se rechaza en la construcción, que es donde se ve el error")
    void pagina0_esRechazada() {
        assertThrows(IllegalArgumentException.class, () -> new CriteriosPagina(0, 50));
        assertThrows(IllegalArgumentException.class, () -> new CriteriosPagina(-1, 50));
    }

    @Test
    @DisplayName("un tamaño de página no positivo se rechaza")
    void tamanioNoPositivo_esRechazado() {
        assertThrows(IllegalArgumentException.class, () -> new CriteriosPagina(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CriteriosPagina(1, -50));
    }
}
