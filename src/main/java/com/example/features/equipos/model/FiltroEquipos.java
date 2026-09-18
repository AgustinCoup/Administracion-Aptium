package com.example.features.equipos.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Los filtros de Ver Equipos, tal como viajan del controller al service y del service al DAO, que
 * los resuelve <b>en SQL</b>.
 *
 * <h2>Un solo record para las dos tablas</h2>
 * Las dos grillas ofrecen los mismos siete campos y cada DAO aplica los que le corresponden; ver
 * abajo los tres que "otros" ignora.
 *
 * <h2>Regla de oro: todos los filtros van a SQL o ninguno</h2>
 * Filtrar en memoria una página traída con {@code LIMIT} filtra 50 de 5000, no las 50 primeras de
 * las que matchean. Por eso este record tiene exactamente los campos que la UI ofrece hoy: si
 * alguno se quedara afuera, la paginación daría resultados incorrectos sin que ningún test obvio
 * lo note.
 *
 * <h2>⚠️ Tres campos no aplican a "otros", y eso es comportamiento, no un descuido</h2>
 * {@code equipo_otros} no tiene profesional, paciente ni institución, y Ver Equipos aplica esos
 * tres <b>sólo</b> a la grilla de ortopedias: escribir un profesional filtra la de arriba y deja la
 * de abajo intacta. Se <b>ignoran</b> en {@code EquipoOtrosDAO}.
 *
 * <p>Eso no se "arregla" acá: es lo que el operador conoce, y cambiarlo es un cambio de
 * comportamiento visible que hay que decidir aparte.
 *
 * <h2>Semántica de cada campo</h2>
 * <ul>
 *   <li>{@code cliente}, {@code profesional}, {@code paciente}, {@code institucion}: substring
 *       insensible a mayúsculas; vacío o {@code null} = sin filtro. Substring <b>literal</b>: un
 *       {@code %} tipeado por el operador se busca como {@code %}, no como comodín.</li>
 *   <li>{@code estados}: lista vacía = sin filtro; si no, el estado del equipo tiene que estar en
 *       la lista. El estado es el de la <b>columna</b>, que es el mismo valor que
 *       {@code calcularEstado()} — ver {@code EstadoPersistidoEsElCalculadoTest}.</li>
 *   <li>{@code tiposIngreso}: lista vacía = sin filtro. Sólo aplica a "otros"; en ortopedias se
 *       ignora, igual que hoy.</li>
 *   <li>{@code desde} / {@code hasta}: por día, ambos inclusive; extremo {@code null} = abierto.
 *       Un equipo <b>sin</b> fecha pasa sólo si los dos extremos son {@code null} — y
 *       {@code fecha_ingreso} <b>es</b> nullable en las dos tablas (V1 y V2: {@code TIMESTAMP
 *       DEFAULT CURRENT_TIMESTAMP}, sin {@code NOT NULL}), así que la rama existe de verdad.</li>
 * </ul>
 */
public record FiltroEquipos(
        List<String> estados,
        String cliente,
        String profesional,
        String paciente,
        String institucion,
        List<String> tiposIngreso,
        LocalDate desde,
        LocalDate hasta) {

    public FiltroEquipos {
        estados      = estados == null      ? List.of() : List.copyOf(estados);
        tiposIngreso = tiposIngreso == null ? List.of() : List.copyOf(tiposIngreso);
    }

    /** Sin ningún filtro puesto: todos los equipos. */
    public static FiltroEquipos sinFiltros() {
        return new FiltroEquipos(List.of(), null, null, null, null, List.of(), null, null);
    }
}
