package com.example.app.ui;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import java.util.List;

/**
 * El histórico completo de equipos, entregados incluidos.
 *
 * <p>Lo consumen las pantallas de consulta ({@code Ver Equipos} y {@code Estado de
 * procesos}), que filtran en Java sobre esta lista. Traerlo completo es lo que
 * hace que el filtro por ENTREGADO funcione: si estas pantallas comieran de
 * {@link DatosOperativos}, ese filtro quedaría vacío para siempre y nadie lo
 * notaría hasta que un usuario buscara un equipo entregado.
 *
 * <p>A diferencia de {@link DatosOperativos}, esto <b>no</b> se relee en cada
 * guardado: se lee cuando el usuario abre una de esas pantallas, que es cuando
 * los datos importan. Antes se leía siempre, incluso con las pantallas ocultas.
 *
 * @param equipos      histórico de equipos de ortopedia
 * @param equiposOtros histórico de equipos "otros"
 */
public record HistorialEquipos(
    List<Equipo>      equipos,
    List<EquipoOtros> equiposOtros
) {

    public HistorialEquipos {
        equipos      = List.copyOf(equipos);
        equiposOtros = List.copyOf(equiposOtros);
    }

    /** Snapshot vacío, para el primer pintado antes de que llegue la lectura real. */
    public static HistorialEquipos vacio() {
        return new HistorialEquipos(List.of(), List.of());
    }
}
