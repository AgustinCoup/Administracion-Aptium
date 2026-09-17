package com.example.features.lavadero.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Los filtros del Historial de Lavadero, tal como viajan del controller al service y del service
 * al DAO, que los resuelve <b>en SQL</b>.
 *
 * <h2>Por qué no se reusa {@code HistorialFilterCriteria}</h2>
 * Aquél era el criterio de una estrategia de filtrado <em>en memoria</em>, y vivía en
 * {@code controller/helpers} junto a ella. Este vive en el modelo porque lo consume la capa de
 * datos: un DAO que importara un tipo de {@code controller} invertiría la dirección de las capas.
 *
 * <h2>Regla de oro: los cinco filtros van a SQL o ninguno</h2>
 * Filtrar en memoria una página traída con {@code LIMIT} filtra 50 de 5000, no las 50 primeras de
 * las que matchean. Por eso este record tiene exactamente los mismos campos que tenía el criterio
 * de UI: si alguno se quedara afuera, la paginación daría resultados incorrectos sin que ningún
 * test obvio lo note.
 *
 * <p>Semántica de cada campo — la que tenía {@code HistorialFilterStrategy}, preservada por el
 * test de equivalencia de {@code HistorialLavaderoDAOPaginacionTest}:</p>
 * <ul>
 *   <li>{@code cliente} / {@code elemento}: substring insensible a mayúsculas; vacío o
 *       {@code null} = sin filtro. Es un substring <b>literal</b>: un {@code %} tipeado por el
 *       operador se busca como {@code %}, no como comodín.</li>
 *   <li>{@code estados}: lista vacía = sin filtro; si no, el estado tiene que estar en la lista.</li>
 *   <li>{@code desde} / {@code hasta}: por día, ambos inclusive; extremo {@code null} = abierto.
 *       Un ingreso <b>sin</b> fecha pasa sólo si los dos extremos son {@code null}.</li>
 *   <li>{@code lavarropas}: número exacto de lavarropas que lavó algo del ingreso; {@code null} =
 *       sin filtro.</li>
 * </ul>
 */
public record FiltroHistorial(
        String cliente,
        List<String> estados,
        LocalDate desde,
        LocalDate hasta,
        String elemento,
        Integer lavarropas) {

    public FiltroHistorial {
        estados = estados == null ? List.of() : List.copyOf(estados);
    }

    /** Sin ningún filtro puesto: todos los ingresos. */
    public static FiltroHistorial sinFiltros() {
        return new FiltroHistorial(null, List.of(), null, null, null, null);
    }
}
