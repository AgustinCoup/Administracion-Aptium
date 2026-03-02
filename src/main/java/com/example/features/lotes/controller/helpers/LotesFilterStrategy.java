package com.example.features.lotes.controller.helpers;

import com.example.common.util.DateTimeDisplayUtils;
import com.example.common.util.FilterStrategy;
import com.example.common.util.TextFilterUtils;
import com.example.features.lotes.model.Lote;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class LotesFilterStrategy implements FilterStrategy<Lote, LotesFilterCriteria> {

    @Override
    public List<Lote> filter(List<Lote> source, LotesFilterCriteria criteria) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        String filtroId = normalize(criteria.getId());
        String filtroEquipo = normalize(criteria.getEquipo());
        String filtroFechaInicio = normalize(criteria.getFechaInicio());

        return source.stream()
            .filter(lote -> TextFilterUtils.containsIgnoreCase(lote.getIdNegocio(), filtroId))
            .filter(lote -> TextFilterUtils.containsIgnoreCase(lote.getAutoclaveNombre(), filtroEquipo))
            .filter(lote -> TextFilterUtils.containsIgnoreCase(DateTimeDisplayUtils.formatForFilter(lote.getFechaInicio()), filtroFechaInicio))
            .collect(Collectors.toList());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
