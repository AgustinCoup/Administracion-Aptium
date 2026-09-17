package com.example.features.equipos.service;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.dao.CdeConsultaDAO;
import com.example.features.equipos.model.FiltroEquipos;

import java.util.Objects;

/**
 * Service de la pantalla <b>Estado de Procesos</b>: la lista unificada de equipos de ortopedia y
 * "otros", paginada.
 *
 * <p>Valida y delega en {@link CdeConsultaDAO}; <b>cero JDBC</b> (regla del repo). Vive en su
 * propio paquete, fuera de {@code ortopedias} y de {@code otros}, por la misma razón que su DAO:
 * cruza las dos features y no pertenece a ninguna.
 */
public class CdeConsultaService {

    private final CdeConsultaDAO dao;

    public CdeConsultaService(CdeConsultaDAO dao) {
        this.dao = Objects.requireNonNull(dao, "CdeConsultaDAO no puede ser nulo");
    }

    /** Una página de la lista unificada, con sus materiales, y el total que matchea el filtro. */
    public Pagina<EquipoRegistrableInterface> obtenerPagina(FiltroEquipos filtro,
                                                            CriteriosPagina criterios) {
        exigirCriterios(filtro, criterios);
        return dao.obtenerPagina(filtro, criterios);
    }

    /**
     * La misma página con el total ya sabido, para cuando sólo cambió el número de página y no los
     * filtros: así no se repite el {@code COUNT(*)}.
     */
    public Pagina<EquipoRegistrableInterface> obtenerPagina(FiltroEquipos filtro,
                                                            CriteriosPagina criterios,
                                                            long totalConocido) {
        exigirCriterios(filtro, criterios);
        return dao.obtenerPagina(filtro, criterios, totalConocido);
    }

    /** Cuántos equipos matchean el filtro. Se pide sólo cuando cambian los filtros. */
    public long contar(FiltroEquipos filtro) {
        Objects.requireNonNull(filtro, "filtro");
        return dao.contar(filtro);
    }

    private static void exigirCriterios(FiltroEquipos filtro, CriteriosPagina criterios) {
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(criterios, "criterios");
    }
}
