package com.example.features.lotes.controller.helpers;

import com.example.common.util.FilterStrategy;
import com.example.common.util.TextFilterUtils;
import com.example.features.lotes.model.Lote;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Estrategia de filtrado para lotes.
 *
 * Reglas:
 * - ID: coincidencia parcial (insensible a mayúsculas) sobre {@code idNegocio}.
 * - Autoclaves: si la lista está vacía → sin filtro; si no, el autoclave del lote
 *   debe estar en la lista (comparación exacta insensible a mayúsculas).
 * - Estados: ídem que autoclaves.
 * - Fecha: la fecha de inicio del lote debe caer dentro del rango [desde, hasta].
 *   Cualquier extremo null se trata como abierto.
 */
public class LotesFilterStrategy implements FilterStrategy<Lote, LotesFilterCriteria> {

    @Override
    public List<Lote> filter(List<Lote> source, LotesFilterCriteria criteria) {
        if (source == null || source.isEmpty()) return List.of();

        String filtroId = criteria.getId().trim().toLowerCase(Locale.ROOT);

        return source.stream()
            .filter(lote -> cumpleId(lote, filtroId))
            .filter(lote -> cumpleAutoclave(lote, criteria.getAutoclaves()))
            .filter(lote -> cumpleEstado(lote, criteria.getEstados()))
            .filter(lote -> cumpleFechas(lote, criteria.getFechaDesde(), criteria.getFechaHasta()))
            .collect(Collectors.toList());
    }

    // ── Filtros individuales ─────────────────────────────────────────────────

    private boolean cumpleId(Lote lote, String filtroId) {
        return filtroId.isEmpty()
            || TextFilterUtils.containsIgnoreCase(lote.getIdNegocio(), filtroId);
    }

    /**
     * Lista vacía → sin filtro (mostrar todos).
     * Lista con elementos → el autoclave del lote debe coincidir con alguno (exacto, sin case).
     */
    private boolean cumpleAutoclave(Lote lote, List<String> autoclaves) {
        if (autoclaves.isEmpty()) return true;
        String nombre = lote.getAutoclaveNombre();
        if (nombre == null) return false;
        return autoclaves.stream()
            .anyMatch(a -> a.equalsIgnoreCase(nombre));
    }

    /**
     * Lista vacía → sin filtro.
     * Lista con elementos → el estado del lote debe coincidir con alguno (exacto, sin case).
     */
    private boolean cumpleEstado(Lote lote, List<String> estados) {
        if (estados.isEmpty()) return true;
        String estado = lote.getEstado();
        if (estado == null) estado = "ACTIVO";
        final String estadoFinal = estado;
        return estados.stream()
            .anyMatch(e -> e.equalsIgnoreCase(estadoFinal));
    }

    /**
     * Comprueba que la fecha de inicio del lote esté dentro del rango [desde, hasta].
     * Cualquier extremo null se trata como ilimitado.
     */
    private boolean cumpleFechas(Lote lote, LocalDate desde, LocalDate hasta) {
        if (lote.getFechaInicio() == null) return desde == null && hasta == null;
        LocalDate fechaLote = lote.getFechaInicio().toLocalDate();
        if (desde != null && fechaLote.isBefore(desde)) return false;
        if (hasta != null && fechaLote.isAfter(hasta))  return false;
        return true;
    }
}