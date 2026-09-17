package com.example.common.paginacion;

import java.util.List;

/**
 * Una página de resultados junto con el total de filas que la consulta completa habría devuelto.
 *
 * <p>Clase plana: sin Swing y sin JDBC. La construyen tanto los DAO que paginan en SQL
 * (Pasos 8 y 10 del plan) como {@link PaginadorEnMemoria} (Paso 12), y la consumen los
 * controllers y {@code PanelPaginacion} sin distinguir de dónde salió.
 *
 * <h2>Por qué el total viaja <em>dentro</em> de la página</h2>
 * La UI necesita saber cuántas pestañas dibujar en el mismo momento en que pinta las filas.
 * Devolverlo aparte obliga a coordinar dos llamadas y abre la puerta a que el conteo y el
 * contenido salgan de filtros distintos — la UI diría "127 resultados" y mostraría otra cosa.
 *
 * <p>Lo que <b>no</b> implica: que el total se recalcule en cada cambio de página. El total sólo
 * cambia cuando cambian los <em>filtros</em>; ir de la página 3 a la 4 lo arrastra tal cual.
 *
 * <h2>Por qué {@code LIMIT ? OFFSET ?} y no keyset</h2>
 * El pedido es de <b>pestañas numeradas</b>: saltar a la página 7 y saber cuántas hay. Un cursor
 * de keyset (…{@code WHERE (fecha, id) < (?, ?) ORDER BY … LIMIT ?}) sólo sabe "siguiente" y
 * "anterior": no puede posicionarse en la página 7 sin recorrer las seis anteriores, y no sabe
 * cuántas páginas existen. Con {@code ORDER BY} indexado y volúmenes de miles de filas —no de
 * millones— el OFFSET profundo no duele.
 *
 * <p><b>Salida de emergencia, escrita acá para que nadie tenga que redescubrirla:</b> si algún día
 * el OFFSET profundo sí duele (el servidor lee y descarta {@code OFFSET} filas antes de devolver
 * las 50), el reemplazo es <b>keyset/cursor</b> sobre la misma clave del {@code ORDER BY}. El costo
 * de migrar es perder las pestañas numeradas y el total: hay que cambiar también la UI, no sólo el
 * SQL. Por eso no se hace antes de que la medición lo pida.
 *
 * @param contenido    filas de esta página; se copia en el constructor (inmutable)
 * @param numeroPagina número de página <b>base 1</b>: la primera página es la 1, no la 0. Es lo que
 *                     ve el operador, y evita la conversión silenciosa en la UI
 * @param tamanioPagina filas por página pedidas (no las devueltas: la última página trae menos)
 * @param totalFilas   filas que devolvería la consulta sin paginar, con los <em>mismos</em> filtros
 * @param <T>          tipo de fila
 */
public record Pagina<T>(List<T> contenido, int numeroPagina, int tamanioPagina, long totalFilas) {

    public Pagina {
        if (numeroPagina < 1) {
            throw new IllegalArgumentException("numeroPagina es base 1, recibido: " + numeroPagina);
        }
        if (tamanioPagina < 1) {
            throw new IllegalArgumentException("tamanioPagina debe ser positivo, recibido: " + tamanioPagina);
        }
        if (totalFilas < 0) {
            throw new IllegalArgumentException("totalFilas no puede ser negativo, recibido: " + totalFilas);
        }
        contenido = List.copyOf(contenido);
    }

    /**
     * Cantidad de páginas que hay que dibujar.
     *
     * <p><b>Una lista vacía tiene 1 página, no 0.</b> Una UI que dibuja "página 1 de 0" es un bug
     * visible, y "página 0 de 0" es peor. Sin resultados hay una única página, vacía.
     */
    public int totalPaginas() {
        if (totalFilas <= 0) {
            return 1;
        }
        return (int) ((totalFilas + tamanioPagina - 1) / tamanioPagina);
    }

    public boolean estaVacia() {
        return contenido.isEmpty();
    }

    public boolean esPrimera() {
        return numeroPagina <= 1;
    }

    public boolean esUltima() {
        return numeroPagina >= totalPaginas();
    }

    /**
     * Número de la primera fila de esta página dentro del total, base 1. Cero si está vacía.
     * Es la X de "Mostrando X-Y de Z".
     */
    public long primeraFila() {
        return estaVacia() ? 0 : (long) (numeroPagina - 1) * tamanioPagina + 1;
    }

    /**
     * Número de la última fila de esta página dentro del total, base 1. Cero si está vacía.
     * Sale del contenido real, no del tamaño pedido: la última página trae menos.
     */
    public long ultimaFila() {
        return estaVacia() ? 0 : primeraFila() + contenido.size() - 1;
    }

    /**
     * Envuelve una lista completa como página única, sin paginar.
     *
     * <p>Para las pantallas que todavía leen todo de una y para la paginación en memoria cuando el
     * volumen no llega a una página. No alivia nada: es sólo la forma de que un llamador que ya
     * habla {@code Pagina} no tenga que tratar el caso "sin paginación" aparte.
     */
    public static <T> Pagina<T> unica(List<T> todo) {
        return new Pagina<>(todo, 1, Math.max(1, todo.size()), todo.size());
    }
}
