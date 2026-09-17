package com.example.features.lavadero.controller.helpers;

import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.model.FiltroHistorial;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.service.HistorialLavaderoService;

/**
 * Qué está mirando la pantalla de Historial: un filtro, una página y —si ya se sabe— el total que
 * ese filtro devuelve.
 *
 * <h2>Por qué existe este tipo y no tres campos del controller</h2>
 * {@code RefrescadorPantallas} lee con un {@code Supplier} <b>sin parámetros</b>, y ese supplier
 * corre en el <b>hilo de fondo</b>. El filtro y la página, en cambio, son estado de controller: se
 * tocan sólo en el EDT (regla dura del repo). Leerlos desde el hilo de fondo sería leer estado del
 * EDT desde otro hilo; capturarlos al construir el lector congelaría la primera página para
 * siempre.
 *
 * <p>La salida es publicar un <b>valor inmutable</b>: el controller arma una
 * {@code ConsultaHistorial} nueva cada vez que algo cambia —en el EDT— y la deja en un campo
 * {@code volatile}; el lector lee esa referencia y nada más. Un record sin nada mutable adentro
 * hace que "leer la referencia" alcance: no hay forma de ver una consulta a medio armar.</p>
 *
 * <h2>El total se recalcula sólo cuando cambian los filtros</h2>
 * {@code totalConocido == null} significa "contá de nuevo". Ir de la página 3 a la 4 con el mismo
 * filtro no cambia el total, así que arrastra el que ya se leyó y se ahorra un {@code COUNT(*)}
 * por click. Un refresco pedido por el operador sí vuelve a contar: justamente puede haber
 * cambiado lo que hay.
 *
 * <p>Clase plana, sin Swing: por eso se puede testear el ciclo entero sin levantar una pantalla.</p>
 *
 * @param filtro        los cinco filtros de la pantalla
 * @param criterios     qué página y de qué tamaño
 * @param totalConocido total ya leído <b>con este mismo filtro</b>, o {@code null} para recontar
 */
public record ConsultaHistorial(FiltroHistorial filtro, CriteriosPagina criterios,
                                Long totalConocido) {

    /** La primera página de un filtro nuevo: total desconocido, hay que contar. */
    public static ConsultaHistorial primeraPagina(FiltroHistorial filtro) {
        return new ConsultaHistorial(filtro, CriteriosPagina.primera(), null);
    }

    /** La misma consulta en otra página, conservando el filtro y el total ya sabido. */
    public ConsultaHistorial enPagina(int numeroPagina) {
        return new ConsultaHistorial(filtro, criterios.conPagina(numeroPagina), totalConocido);
    }

    /** La misma página y el mismo filtro, pero contando de nuevo. Es lo que hace un refresco. */
    public ConsultaHistorial recontando() {
        return new ConsultaHistorial(filtro, criterios, null);
    }

    /** La misma consulta con el total que acaba de traer la lectura. */
    public ConsultaHistorial conTotal(long total) {
        return new ConsultaHistorial(filtro, criterios, total);
    }

    /**
     * Lee la página. <b>Corre en el hilo de fondo</b>: acá no se toca ningún componente de Swing
     * ni ningún campo del controller.
     */
    public Pagina<IngresoHistorial> leer(HistorialLavaderoService service) {
        return totalConocido == null
            ? service.obtenerPagina(filtro, criterios)
            : service.obtenerPagina(filtro, criterios, totalConocido);
    }
}
