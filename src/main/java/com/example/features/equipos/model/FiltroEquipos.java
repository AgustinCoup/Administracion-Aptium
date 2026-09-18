package com.example.features.equipos.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Los filtros de las dos pantallas de consulta del CDE —Ver Equipos y Estado de Procesos—, tal como
 * viajan del controller al service y del service al DAO, que los resuelve <b>en SQL</b>.
 *
 * <h2>Un solo record para las dos pantallas, y para las dos tablas</h2>
 * Ver Equipos ofrece los siete campos; Estado de Procesos ofrece tres (cliente, institución,
 * estados) y deja el resto sin poner. Partirlo en dos records no compraría nada: el segundo sería
 * un subconjunto exacto del primero, y {@link com.example.features.equipos.dao.CdeConsultaDAO}
 * —que une las dos tablas— necesitaría convertir entre ellos.
 *
 * <h2>Regla de oro: todos los filtros van a SQL o ninguno</h2>
 * Filtrar en memoria una página traída con {@code LIMIT} filtra 50 de 5000, no las 50 primeras de
 * las que matchean. Por eso este record tiene exactamente los campos que la UI ofrece hoy: si
 * alguno se quedara afuera, la paginación daría resultados incorrectos sin que ningún test obvio
 * lo note.
 *
 * <h2>⚠️ Tres campos no aplican a "otros", y eso es comportamiento, no un descuido</h2>
 * {@code equipo_otros} no tiene profesional, paciente ni institución. Lo que hace la UI hoy con
 * cada uno hay que <b>preservarlo exactamente</b>, porque es lo que el operador conoce, y las dos
 * pantallas no hacen lo mismo:
 *
 * <ul>
 *   <li><b>Ver Equipos</b> aplica profesional,
 *       paciente e institución <b>sólo</b> a la tabla de ortopedias; la de "otros" nunca los ve.
 *       O sea: escribir un profesional filtra la grilla de arriba y deja la de abajo intacta. Esos
 *       tres campos se <b>ignoran</b> en {@code EquipoOtrosDAO}.</li>
 *   <li><b>Estado de Procesos</b> aplicaba el de institución a las dos,
 *       vía {@code getDescripcionSecundaria()}, que para "otros" devuelve cadena vacía. Como
 *       {@code TextFilterUtils.containsIgnoreCase("", filtro)} sólo es verdadero con el filtro
 *       vacío, el efecto es: <b>los "otros" aparecen únicamente cuando el campo institución está
 *       en blanco</b>, y desaparecen en cuanto se escribe algo. Esa asimetría la reproduce
 *       {@code CdeConsultaDAO}, no {@code EquipoOtrosDAO}, porque es de esa pantalla.</li>
 * </ul>
 *
 * <p>Ninguna de las dos se "arregla" acá. Cambiar cualquiera de ellas es un cambio de
 * comportamiento visible que hay que decidir aparte; este paso mueve el filtrado a SQL y nada más.
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
