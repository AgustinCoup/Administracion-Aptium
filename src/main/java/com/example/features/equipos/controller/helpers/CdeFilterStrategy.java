package com.example.features.equipos.controller.helpers;

import com.example.common.util.FilterStrategy;
import com.example.common.util.TextFilterUtils;
import com.example.features.equipos.model.Equipo;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CdeFilterStrategy implements FilterStrategy<Equipo, CdeFilterCriteria> {

    @Override
    public List<Equipo> filter(List<Equipo> source, CdeFilterCriteria criteria) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        String filtroCliente = normalize(criteria.getCliente());
        String filtroInstitucion = normalize(criteria.getInstitucion());
        List<String> filtroEstados = criteria.getEstados();

        return source.stream()
            .filter(eq -> TextFilterUtils.containsIgnoreCase(eq.getClienteNombre(), filtroCliente))
            .filter(eq -> TextFilterUtils.containsIgnoreCase(eq.getInstitucionNombre(), filtroInstitucion))
            .filter(eq -> filtroEstados.isEmpty() || filtroEstados.contains(eq.calcularEstado().getNombre()))
            .collect(Collectors.toList());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
