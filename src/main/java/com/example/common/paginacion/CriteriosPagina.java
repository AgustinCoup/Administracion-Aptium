package com.example.common.paginacion;

import com.example.common.constants.Constantes;

/**
 * Qué página se está pidiendo. Es lo que viaja del controller al service y del service al DAO.
 *
 * <p>Clase plana: sin Swing y sin JDBC. Deliberadamente <b>no</b> lleva los filtros: los filtros
 * son de cada pantalla (el historial tiene cinco, el CDE otros) y mezclarlos acá obligaría a un
 * tipo genérico que no aporta nada. Un DAO paginado recibe dos cosas: su filtro y estos criterios.
 *
 * @param numeroPagina  página pedida, <b>base 1</b> (ver {@link Pagina})
 * @param tamanioPagina filas por página
 */
public record CriteriosPagina(int numeroPagina, int tamanioPagina) {

    public CriteriosPagina {
        if (numeroPagina < 1) {
            throw new IllegalArgumentException("numeroPagina es base 1, recibido: " + numeroPagina);
        }
        if (tamanioPagina < 1) {
            throw new IllegalArgumentException("tamanioPagina debe ser positivo, recibido: " + tamanioPagina);
        }
    }

    /** Primera página con el tamaño estándar de la app ({@link Constantes.Paginacion#TAMANIO_PAGINA}). */
    public static CriteriosPagina primera() {
        return new CriteriosPagina(1, Constantes.Paginacion.TAMANIO_PAGINA);
    }

    /** Página {@code numeroPagina} con el tamaño estándar de la app. */
    public static CriteriosPagina pagina(int numeroPagina) {
        return new CriteriosPagina(numeroPagina, Constantes.Paginacion.TAMANIO_PAGINA);
    }

    /** Los mismos criterios en otra página. Conserva el tamaño, que es lo que se quiere al navegar. */
    public CriteriosPagina conPagina(int otroNumeroPagina) {
        return new CriteriosPagina(otroNumeroPagina, tamanioPagina);
    }

    /**
     * Filas a saltear: el {@code OFFSET} de la consulta.
     *
     * <p>Es {@code long} porque {@code (numeroPagina - 1) * tamanioPagina} desborda un {@code int}
     * con números de página absurdos, y un número de página absurdo tiene que dar una página vacía
     * —el operador puede tener abierta una página que ya no existe—, no un offset negativo.
     */
    public long offset() {
        return (long) (numeroPagina - 1) * tamanioPagina;
    }
}
