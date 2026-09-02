package com.example.features.lavadero.controller.helpers;

import java.time.LocalDate;
import java.util.List;

/**
 * Criterios de filtrado de la pantalla de Historial de Lavadero. Espejo de
 * {@link CicloFilterCriteria}, adaptado a los seis filtros de esta pantalla.
 *
 * <ul>
 *   <li>{@code cliente} / {@code elemento}: substring insensible a mayúsculas; vacío = sin filtro.</li>
 *   <li>{@code estados}: multi-selección (CheckableComboBox); lista vacía = sin filtro.</li>
 *   <li>{@code desde} / {@code hasta}: extremo {@code null} = abierto.</li>
 *   <li>{@code lavarropas}: número exacto; {@code null} = sin filtro.</li>
 * </ul>
 */
public record HistorialFilterCriteria(
        String cliente,
        List<String> estados,
        LocalDate desde,
        LocalDate hasta,
        String elemento,
        Integer lavarropas) {

    public HistorialFilterCriteria {
        estados = estados == null ? List.of() : List.copyOf(estados);
    }
}
