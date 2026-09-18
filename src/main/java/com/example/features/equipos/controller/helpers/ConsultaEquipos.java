package com.example.features.equipos.controller.helpers;

import com.example.common.paginacion.CriteriosPagina;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.service.EquipoOtrosService;

import java.util.Objects;

/**
 * Qué está mirando <b>Ver Equipos</b>: un filtro compartido por sus dos grillas, la página de cada
 * una y —si ya se sabe— el total que ese filtro devuelve en cada una.
 *
 * <h2>Por qué existe este tipo y no cinco campos del controller</h2>
 * Misma razón que {@code ConsultaHistorial}: {@code RefrescadorPantallas} lee con un
 * {@code Supplier} <b>sin parámetros</b> que corre en el <b>hilo de fondo</b>, y el filtro y las
 * páginas son estado de controller, que sólo se toca en el EDT (regla dura del repo). Leerlos desde
 * el hilo de fondo sería leer estado del EDT desde otro hilo; capturarlos al construir el lector
 * congelaría la primera página para siempre.
 *
 * <p>La salida es publicar un <b>valor inmutable</b>: el controller arma una {@code ConsultaEquipos}
 * nueva cada vez que algo cambia —en el EDT— y la deja en un campo {@code volatile}; el lector lee
 * esa referencia y nada más.
 *
 * <h2>Un filtro para las dos tablas, dos páginas</h2>
 * La pantalla tiene <b>un</b> panel de filtros y <b>dos</b> grillas, así que el filtro es uno solo
 * (los campos que no aplican a "otros" los ignora su DAO — ver {@link FiltroEquipos}) y las páginas
 * son dos, independientes: pasar a la página 3 de ortopedias no mueve la de "otros".
 *
 * <p><b>Una lectura trae las dos páginas, incluso la de la pestaña que no se ve.</b> Es lo que hace
 * que las dos grillas describan siempre el mismo filtro, que es como funciona hoy, y lo que permite
 * que cambiar de pestaña no cueste ninguna consulta. El precio es leer una página de 50 de más por
 * refresco; el alivio real —dejar de traer el histórico completo de las dos tablas, con sus
 * materiales— no se toca.
 *
 * <h2>El total se recalcula sólo cuando cambian los filtros</h2>
 * {@code null} en un total significa "contá de nuevo". Ir de la página 3 a la 4 con el mismo filtro
 * no cambia ningún total, así que arrastra los dos y se ahorra un {@code COUNT(*)} por click. Un
 * refresco pedido por el operador sí vuelve a contar: justamente puede haber cambiado lo que hay.
 *
 * <p>Clase plana, sin Swing: por eso se puede testear el ciclo entero sin levantar una pantalla.
 *
 * @param filtro          los filtros de la pantalla, comunes a las dos grillas
 * @param ortopedias      qué página de la grilla de ortopedias
 * @param totalOrtopedias total ya leído de ortopedias <b>con este mismo filtro</b>, o {@code null}
 * @param otros           qué página de la grilla de "otros"
 * @param totalOtros      total ya leído de "otros" <b>con este mismo filtro</b>, o {@code null}
 */
public record ConsultaEquipos(FiltroEquipos filtro,
                              CriteriosPagina ortopedias, Long totalOrtopedias,
                              CriteriosPagina otros, Long totalOtros) {

    public ConsultaEquipos {
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(ortopedias, "ortopedias");
        Objects.requireNonNull(otros, "otros");
    }

    /** La primera página de las dos grillas con un filtro nuevo: totales desconocidos. */
    public static ConsultaEquipos primeraPagina(FiltroEquipos filtro) {
        return new ConsultaEquipos(filtro,
            CriteriosPagina.primera(), null,
            CriteriosPagina.primera(), null);
    }

    /** Otra página de ortopedias. No toca la de "otros" ni ninguno de los dos totales. */
    public ConsultaEquipos ortopediasEnPagina(int numeroPagina) {
        return new ConsultaEquipos(filtro,
            ortopedias.conPagina(numeroPagina), totalOrtopedias, otros, totalOtros);
    }

    /** Otra página de "otros". No toca la de ortopedias ni ninguno de los dos totales. */
    public ConsultaEquipos otrosEnPagina(int numeroPagina) {
        return new ConsultaEquipos(filtro,
            ortopedias, totalOrtopedias, otros.conPagina(numeroPagina), totalOtros);
    }

    /** Las mismas páginas y el mismo filtro, contando de nuevo. Es lo que hace un refresco. */
    public ConsultaEquipos recontando() {
        return new ConsultaEquipos(filtro, ortopedias, null, otros, null);
    }

    /** La misma consulta con los totales que acaba de traer la lectura. */
    public ConsultaEquipos conTotales(long deOrtopedias, long deOtros) {
        return new ConsultaEquipos(filtro, ortopedias, deOrtopedias, otros, deOtros);
    }

    /**
     * Lee las dos páginas. <b>Corre en el hilo de fondo</b>: acá no se toca ningún componente de
     * Swing ni ningún campo del controller.
     */
    public PaginasEquipos leer(EquipoService ortopediaService, EquipoOtrosService otrosService) {
        return new PaginasEquipos(
            totalOrtopedias == null
                ? ortopediaService.obtenerPagina(filtro, ortopedias)
                : ortopediaService.obtenerPagina(filtro, ortopedias, totalOrtopedias),
            totalOtros == null
                ? otrosService.obtenerPagina(filtro, otros)
                : otrosService.obtenerPagina(filtro, otros, totalOtros));
    }
}
