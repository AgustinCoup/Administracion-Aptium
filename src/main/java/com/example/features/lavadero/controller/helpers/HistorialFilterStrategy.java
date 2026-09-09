package com.example.features.lavadero.controller.helpers;

import com.example.common.util.FilterStrategy;
import com.example.features.lavadero.model.IngresoHistorial;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Estrategia de filtrado para el Historial de Lavadero. Un método privado por filtro, misma
 * forma que {@link CicloFilterStrategy}. Todo se resuelve en memoria sobre el snapshot.
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>Cliente: vacío → pasa; si no, {@code contains} insensible a mayúsculas sobre el nombre.</li>
 *   <li>Estados: lista vacía → pasa; si no, {@code estado.name()} tiene que estar en la lista
 *       (insensible a mayúsculas).</li>
 *   <li>Fechas: {@code fechaIngreso.toLocalDate()} dentro de {@code [desde, hasta]}; extremo
 *       {@code null} = abierto; {@code fechaIngreso == null} sólo pasa si ambos extremos son
 *       {@code null}.</li>
 *   <li>Elemento: vacío → pasa; si no, algún nombre de {@code elementos()} contiene el texto
 *       (insensible a mayúsculas).</li>
 *   <li>Lavarropas: {@code null} → pasa; si no, {@code lavarropas().contains(n)}.</li>
 * </ul>
 */
public class HistorialFilterStrategy
        implements FilterStrategy<IngresoHistorial, HistorialFilterCriteria> {

    @Override
    public List<IngresoHistorial> filter(List<IngresoHistorial> source,
                                         HistorialFilterCriteria criteria) {
        if (source == null || source.isEmpty()) return List.of();

        return source.stream()
            .filter(i -> cumpleCliente(i, criteria.cliente()))
            .filter(i -> cumpleEstado(i, criteria.estados()))
            .filter(i -> cumpleFechas(i, criteria.desde(), criteria.hasta()))
            .filter(i -> cumpleElemento(i, criteria.elemento()))
            .filter(i -> cumpleLavarropas(i, criteria.lavarropas()))
            .collect(Collectors.toList());
    }

    // ── Filtros individuales ─────────────────────────────────────────────────

    private boolean cumpleCliente(IngresoHistorial ingreso, String filtro) {
        if (filtro == null || filtro.isBlank()) return true;
        String nombre = ingreso.clienteNombre();
        return nombre != null && nombre.toLowerCase().contains(filtro.toLowerCase());
    }

    private boolean cumpleEstado(IngresoHistorial ingreso, List<String> estados) {
        return estados.isEmpty()
            || estados.stream().anyMatch(e -> e.equalsIgnoreCase(ingreso.estado().name()));
    }

    private boolean cumpleFechas(IngresoHistorial ingreso, LocalDate desde, LocalDate hasta) {
        if (ingreso.fechaIngreso() == null) return desde == null && hasta == null;
        LocalDate dia = ingreso.fechaIngreso().toLocalDate();
        if (desde != null && dia.isBefore(desde)) return false;
        if (hasta != null && dia.isAfter(hasta))  return false;
        return true;
    }

    private boolean cumpleElemento(IngresoHistorial ingreso, String filtro) {
        if (filtro == null || filtro.isBlank()) return true;
        String texto = filtro.toLowerCase();
        return ingreso.elementos().stream()
            .anyMatch(e -> e != null && e.toLowerCase().contains(texto));
    }

    private boolean cumpleLavarropas(IngresoHistorial ingreso, Integer numero) {
        return numero == null || ingreso.lavarropas().contains(numero);
    }
}
