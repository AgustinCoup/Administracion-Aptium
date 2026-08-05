package com.example.app.ui;

import com.example.features.autoclaves.model.Autoclave;
import com.example.features.lotes.model.Lote;
import java.util.List;

/**
 * El histórico completo de lotes, más los autoclaves que llenan su filtro.
 *
 * <p>Lo consume {@code Ver Lotes}, que es una pantalla de consulta: se lee al
 * abrirla, no en cada guardado. Va separado de {@link HistorialEquipos} porque
 * son dos pantallas distintas y ninguna necesita lo de la otra: mezclarlas haría
 * que abrir {@code Ver Lotes} leyera además el histórico entero de equipos.
 *
 * @param autoclaves    autoclaves configurados, para el filtro de la grilla
 * @param todosLosLotes histórico completo de lotes
 */
public record HistorialLotes(
    List<Autoclave> autoclaves,
    List<Lote>      todosLosLotes
) {

    public HistorialLotes {
        autoclaves    = List.copyOf(autoclaves);
        todosLosLotes = List.copyOf(todosLosLotes);
    }

    /** Snapshot vacío, para el primer pintado antes de que llegue la lectura real. */
    public static HistorialLotes vacio() {
        return new HistorialLotes(List.of(), List.of());
    }
}
