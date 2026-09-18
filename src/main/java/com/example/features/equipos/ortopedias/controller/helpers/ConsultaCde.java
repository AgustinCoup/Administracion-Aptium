package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.service.CdeConsultaService;

import java.util.Objects;

/**
 * Qué está mirando <b>Estado de Procesos</b>: un filtro, una página y —si ya se sabe— el total que
 * ese filtro devuelve.
 *
 * <h2>Por qué existe este tipo y no tres campos del controller</h2>
 * Misma razón que {@code ConsultaHistorial} y {@code ConsultaEquipos}:
 * {@code RefrescadorPantallas} lee con un {@code Supplier} <b>sin parámetros</b> que corre en el
 * <b>hilo de fondo</b>, mientras que el filtro y la página son estado de controller y sólo se tocan
 * en el EDT (regla dura del repo). El controller publica un valor inmutable en un campo
 * {@code volatile} y el lector lee esa referencia; capturarla al construir el lector congelaría la
 * primera página para siempre.
 *
 * <h2>Reemplaza a {@code CdeFilterStrategy}, no lo envuelve</h2>
 * Esta pantalla filtraba en memoria sobre el histórico completo. Ahora los tres filtros —cliente,
 * institución y estados— viajan a la base dentro de {@link FiltroEquipos} y vuelven 50 filas ya
 * filtradas y ordenadas. Filtrar en memoria una página traída con {@code LIMIT} daría 50 de 5000 en
 * vez de las 50 primeras de las que matchean, así que no queda ningún filtro de vista: ni siquiera
 * el default que oculta los entregados (ver {@code PantallaVerCDEv2.aplicarFiltroInicial()}).
 *
 * <h2>El total se recalcula sólo cuando cambian los filtros</h2>
 * {@code totalConocido == null} significa "contá de nuevo". Ir de la página 3 a la 4 con el mismo
 * filtro no cambia el total, así que arrastra el que ya se leyó. Un refresco pedido por el operador
 * sí vuelve a contar: justamente puede haber cambiado lo que hay.
 *
 * <p>Clase plana, sin Swing: por eso se puede testear el ciclo entero sin levantar una pantalla.
 *
 * @param filtro        cliente, institución y estados de la pantalla
 * @param criterios     qué página y de qué tamaño
 * @param totalConocido total ya leído <b>con este mismo filtro</b>, o {@code null} para recontar
 */
public record ConsultaCde(FiltroEquipos filtro, CriteriosPagina criterios, Long totalConocido) {

    public ConsultaCde {
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(criterios, "criterios");
    }

    /** La primera página de un filtro nuevo: total desconocido, hay que contar. */
    public static ConsultaCde primeraPagina(FiltroEquipos filtro) {
        return new ConsultaCde(filtro, CriteriosPagina.primera(), null);
    }

    /** La misma consulta en otra página, conservando el filtro y el total ya sabido. */
    public ConsultaCde enPagina(int numeroPagina) {
        return new ConsultaCde(filtro, criterios.conPagina(numeroPagina), totalConocido);
    }

    /** La misma página y el mismo filtro, pero contando de nuevo. Es lo que hace un refresco. */
    public ConsultaCde recontando() {
        return new ConsultaCde(filtro, criterios, null);
    }

    /** La misma consulta con el total que acaba de traer la lectura. */
    public ConsultaCde conTotal(long total) {
        return new ConsultaCde(filtro, criterios, total);
    }

    /**
     * Lee la página. <b>Corre en el hilo de fondo</b>: acá no se toca ningún componente de Swing ni
     * ningún campo del controller.
     */
    public Pagina<EquipoRegistrableInterface> leer(CdeConsultaService service) {
        return totalConocido == null
            ? service.obtenerPagina(filtro, criterios)
            : service.obtenerPagina(filtro, criterios, totalConocido);
    }
}
