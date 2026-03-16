package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.util.FilterStrategy;
import com.example.common.util.TextFilterUtils;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Estrategia de filtrado para la pantalla Ver CDE.
 *
 * Actualizada para trabajar con {@link EquipoRegistrableInterface}: usa
 * {@code getDescripcionSecundaria()} para el filtro de institución, lo que
 * funciona transparentemente para ortopedia (devuelve institución) y para
 * "otros" (devuelve cadena vacía, nunca bloquea el filtro a menos que el
 * usuario escriba algo en el campo).
 */
public class CdeFilterStrategy implements FilterStrategy<EquipoRegistrableInterface, CdeFilterCriteria> {

    @Override
    public List<EquipoRegistrableInterface> filter(List<EquipoRegistrableInterface> source, CdeFilterCriteria criteria) {
        if (source == null || source.isEmpty()) return List.of();

        String filtroCliente     = normalize(criteria.getCliente());
        String filtroInstitucion = normalize(criteria.getInstitucion());
        List<String> filtroEstados = criteria.getEstados();

        return source.stream()
            .filter(eq -> TextFilterUtils.containsIgnoreCase(eq.getClienteNombre(), filtroCliente))
            .filter(eq -> TextFilterUtils.containsIgnoreCase(eq.getDescripcionSecundaria(), filtroInstitucion))
            .filter(eq -> filtroEstados.isEmpty() || filtroEstados.contains(eq.calcularEstado().getNombre()))
            .collect(Collectors.toList());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}