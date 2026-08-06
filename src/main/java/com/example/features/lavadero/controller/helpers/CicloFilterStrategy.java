package com.example.features.lavadero.controller.helpers;

import com.example.common.util.FilterStrategy;
import com.example.features.lavadero.model.CicloLavadero;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Estrategia de filtrado para ciclos de Lavadero.
 *
 * Reglas:
 * - Lavarropas: null → sin filtro; si no, coincidencia exacta del número.
 * - Estados: lista vacía → sin filtro; si no, el estado del ciclo debe estar en
 *   la lista (comparación exacta insensible a mayúsculas).
 * - Fecha: la fecha de fin del ciclo debe caer dentro del rango [desde, hasta].
 *   Cualquier extremo null se trata como abierto.
 */
public class CicloFilterStrategy implements FilterStrategy<CicloLavadero, CicloFilterCriteria> {

    @Override
    public List<CicloLavadero> filter(List<CicloLavadero> source, CicloFilterCriteria criteria) {
        if (source == null || source.isEmpty()) return List.of();

        return source.stream()
            .filter(c -> cumpleNumero(c, criteria.getNumeroLavarropas()))
            .filter(c -> cumpleEstado(c, criteria.getEstados()))
            .filter(c -> cumpleFechas(c, criteria.getFechaDesde(), criteria.getFechaHasta()))
            .collect(Collectors.toList());
    }

    // ── Filtros individuales ─────────────────────────────────────────────────

    private boolean cumpleNumero(CicloLavadero ciclo, Integer numero) {
        return numero == null || ciclo.getLavarropasNumero() == numero;
    }

    private boolean cumpleEstado(CicloLavadero ciclo, List<String> estados) {
        return estados.isEmpty()
            || estados.stream().anyMatch(e -> e.equalsIgnoreCase(ciclo.getEstado()));
    }

    /** Los ciclos activos no tienen fecha de fin: siempre pasan el filtro de fecha. */
    private boolean cumpleFechas(CicloLavadero ciclo, LocalDate desde, LocalDate hasta) {
        if (ciclo.estaActivo()) return true;
        LocalDate fechaFin = ciclo.getFechaFin().toLocalDate();
        if (desde != null && fechaFin.isBefore(desde)) return false;
        if (hasta != null && fechaFin.isAfter(hasta))  return false;
        return true;
    }
}
