package com.example.common.paginacion;

import java.util.List;

/**
 * Parte una lista <b>ya leída y ya filtrada</b> en una {@link Pagina}.
 *
 * <h2>Esto NO alivia la base</h2>
 * Hay que decirlo explícito porque es el error fácil: la consulta que produjo la lista siguió
 * trayendo <b>todas</b> las filas, con el mismo trabajo de servidor, el mismo transporte y la misma
 * conexión ocupada el mismo tiempo. Paginar acá no cambia una sola de esas cosas.
 *
 * <p><b>Para qué sirve entonces:</b> para cortar el <em>pintado</em> a 50 filas. Las tablas de la
 * app repintan con {@code setRowCount(0)} más un {@code addRow} por fila, <b>en el EDT</b>; con
 * miles de filas eso congela la UI, y ninguna optimización de SQL lo toca. Ese problema es real y
 * este paginador lo arregla. El otro, no.
 *
 * <p>Por eso lo usan sólo las pantallas cuyo volumen tiene techo (Ver Lotes, Ver Ciclos). Donde el
 * volumen crece sin techo —Historial de Lavadero, CDE— la paginación va en SQL, con los filtros y
 * el orden también en SQL: filtrar en memoria una página traída con {@code LIMIT} devuelve 50 de
 * 5000 filtradas, no las 50 primeras de las que matchean, que es directamente un resultado
 * incorrecto.
 */
public final class PaginadorEnMemoria {

    private PaginadorEnMemoria() {
        throw new UnsupportedOperationException("Clase de utilidad no instanciable");
    }

    /**
     * Devuelve la página pedida de {@code todo}.
     *
     * <p><b>Pedir una página más allá del total devuelve una página vacía con el total correcto, y
     * no lanza.</b> No es un caso de borde teórico: el operador puede tener abierta la página 7
     * cuando otro borra filas, y en el siguiente refresco esa página ya no existe. Lanzar ahí
     * convertiría un cambio legítimo de otro operador en un error técnico.
     *
     * @param todo      lista completa ya filtrada y ya ordenada; no se modifica
     * @param criterios página y tamaño pedidos
     */
    public static <T> Pagina<T> paginar(List<T> todo, CriteriosPagina criterios) {
        long total = todo.size();
        long desde = criterios.offset();

        if (desde >= total) {
            return new Pagina<>(List.of(), criterios.numeroPagina(), criterios.tamanioPagina(), total);
        }

        int inicio = (int) desde;
        int fin = (int) Math.min(total, desde + criterios.tamanioPagina());
        // subList es una vista sobre `todo`; el constructor de Pagina la copia con List.copyOf.
        return new Pagina<>(todo.subList(inicio, fin),
                criterios.numeroPagina(), criterios.tamanioPagina(), total);
    }
}
