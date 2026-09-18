package com.example.features.equipos.dao;

import com.example.features.equipos.model.FiltroEquipos;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Traduce un {@link FiltroEquipos} al {@code WHERE} de las consultas paginadas del CDE.
 *
 * <h2>Qué es esto y qué NO es</h2>
 * Es una clase plana de armado de SQL, sin JDBC propio y sin estado — el patrón de
 * {@code AgrupadorInstanciasSalida} o {@code GuardaRefresco}, testeable en aislamiento.
 *
 * <p><b>No es una abstracción común entre {@code EquipoDAO} y {@code EquipoOtrosDAO}</b>, que es
 * justamente lo que este paso tiene prohibido extraer: las dos tablas tienen columnas, modelos y
 * modalidades distintas, y sólo comparten la <em>forma</em> de las consultas. Lo que se comparte
 * acá es otra cosa: la <b>semántica de los filtros</b>, que sí es una sola y tiene que seguir
 * siéndolo. Los tres métodos de abajo emiten SQL <em>distinto</em> sobre tablas distintas; lo que
 * garantizan en común es que "contiene", "entre fechas" y "en esta lista de estados" quieran decir
 * lo mismo en los tres.
 *
 * <h2>Por qué un solo lugar arma el WHERE de la página y el del conteo</h2>
 * No es estilo, es corrección. Si el conteo y la página usaran {@code WHERE} distintos, la UI
 * diría "127 resultados" y mostraría otra cosa, y nadie lo notaría hasta que un operador contara a
 * mano. Cada DAO llama al mismo método para sus dos consultas.
 *
 * <h2>Cero SQL construida con input del operador</h2>
 * Los fragmentos son constantes del código; los valores viajan siempre por {@code ?}. Lo único que
 * depende del input es <em>cuántos</em> {@code ?} hay en un {@code IN (…)}, que sale de
 * {@link #marcadores(int)} y no del texto.
 */
public final class FiltroEquiposSql {

    private FiltroEquiposSql() { }

    /**
     * Un filtro traducido: los {@code JOIN} que hacen falta, el {@code WHERE} (con la palabra
     * incluida, o vacío) y los valores que lo acompañan, en orden.
     *
     * <h2>⚠️ Por qué los JOIN viajan con el WHERE y no están fijos en la consulta</h2>
     * Las consultas del primer viaje proyectan <b>sólo el id</b>: los {@code JOIN} contra
     * {@code clientes}, {@code profesionales} e {@code instituciones} existen únicamente porque los
     * filtros de texto son sobre el <b>nombre</b>. Cuando esos campos están vacíos, el {@code JOIN}
     * no aporta nada — y no es gratis. Verificado con {@code EXPLAIN} sobre 3 000 equipos
     * (MySQL 8.0.43): con los tres {@code LEFT JOIN} puestos y sin ningún predicado que los use, el
     * optimizador deja de considerar {@code idx_equipos_fecha_ingreso} y resuelve el
     * {@code ORDER BY} con {@code type: ALL} + {@code Using filesort} sobre la tabla entera; sin
     * ellos, el mismo {@code ORDER BY … LIMIT 50} sale por {@code Backward index scan} tocando 50
     * filas.
     *
     * <p>Por eso los dos fragmentos salen <b>del mismo método</b>: un {@code WHERE} que nombra
     * {@code c.nombre} sin su {@code JOIN} no compila en el servidor, y un {@code JOIN} de más
     * cuesta el índice. Emitirlos juntos hace imposible que se desincronicen.
     */
    public record Condicion(String joins, String sql, List<Object> parametros) {

        public Condicion {
            parametros = List.copyOf(parametros);
        }

        /** Sin ninguna condición: la consulta va sin {@code JOIN} de filtro y sin {@code WHERE}. */
        public static final Condicion VACIA = new Condicion("", "", List.of());
    }

    // ── ortopedias ───────────────────────────────────────────────────────────

    /**
     * {@code WHERE} sobre {@code equipos e}, con {@code clientes c}, {@code profesionales p} e
     * {@code instituciones i} ya unidos por el llamador con esos alias.
     *
     * <p>Los cuatro filtros de texto van contra las columnas <b>ya unidas</b> y no contra los ids,
     * porque es lo que hace la pantalla: escribe un pedazo del nombre, no un número.
     *
     * <p>{@code tiposIngreso} se ignora: las ortopedias no tienen modalidad de ingreso. Es lo que
     * hacía el filtrado en memoria de Ver Equipos, que sólo se lo aplicaba a la grilla de
     * "otros" — transcripto en {@code EquipoDAOPaginacionTest.FiltradoDeReferencia}, que es lo
     * que sigue verificando esta equivalencia.
     */
    public static Condicion paraOrtopedias(FiltroEquipos filtro) {
        List<String> clausulas = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        StringBuilder joins = new StringBuilder();

        estados("e.estado", filtro, clausulas, parametros);
        if (contiene("c.nombre", filtro.cliente(), clausulas, parametros)) {
            joins.append(JOIN_CLIENTES_ORTOPEDIA);
        }
        if (contiene("p.nombre", filtro.profesional(), clausulas, parametros)) {
            joins.append(JOIN_PROFESIONALES);
        }
        contiene("e.paciente", filtro.paciente(), clausulas, parametros);
        if (contiene("i.nombre", filtro.institucion(), clausulas, parametros)) {
            joins.append(JOIN_INSTITUCIONES);
        }
        fechas("e.fecha_ingreso", filtro, clausulas, parametros);

        return armar(joins.toString(), clausulas, parametros);
    }

    private static final String JOIN_CLIENTES_ORTOPEDIA =
        "LEFT JOIN clientes c ON e.nro_cliente = c.id ";
    private static final String JOIN_PROFESIONALES =
        "LEFT JOIN profesionales p ON e.nro_profesional = p.id ";
    private static final String JOIN_INSTITUCIONES =
        "LEFT JOIN instituciones i ON e.nro_institucion = i.id ";

    /**
     * {@code clientes} para {@code equipo_otros}.
     *
     * <p>Es {@code LEFT} y no {@code INNER} aunque la FK sea {@code NOT NULL} con
     * {@code ON DELETE RESTRICT}: así, poner o sacar el filtro de cliente no puede cambiar
     * <em>qué</em> equipos entran, sólo por qué. Con un {@code INNER}, si alguna vez apareciera una
     * fila sin cliente, la grilla mostraría un conjunto distinto según si el campo está escrito o
     * no — un bug que se manifestaría como "escribí y se desfiltró de más".
     */
    private static final String JOIN_CLIENTES_OTROS =
        "LEFT JOIN clientes c ON eo.nro_cliente = c.id ";

    // ── otros ────────────────────────────────────────────────────────────────

    /**
     * {@code WHERE} sobre {@code equipo_otros eo} con {@code clientes c} unido, <b>tal como lo
     * aplica la grilla de "otros" de Ver Equipos</b>: estados, cliente, tipo de ingreso y fechas.
     *
     * <p><b>Profesional, paciente e institución no se aplican, y eso es deliberado.</b>
     * El filtrado en memoria de Ver Equipos se los aplicaba únicamente a la grilla de
     * ortopedias: escribir un profesional filtra la tabla de arriba y deja la de abajo intacta. Es
     * el comportamiento que el operador conoce y este paso no lo cambia. La otra pantalla del CDE
     * hace algo distinto con institución — ver {@link #paraOtrosEnUnionCde(FiltroEquipos)}.
     */
    public static Condicion paraOtros(FiltroEquipos filtro) {
        List<String> clausulas = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        String joins = "";

        estados("eo.estado", filtro, clausulas, parametros);
        if (contiene("c.nombre", filtro.cliente(), clausulas, parametros)) {
            joins = JOIN_CLIENTES_OTROS;
        }
        enLista("eo.tipo_ingreso", filtro.tiposIngreso(), clausulas, parametros);
        fechas("eo.fecha_ingreso", filtro, clausulas, parametros);

        return armar(joins, clausulas, parametros);
    }

    /**
     * El mismo {@code WHERE} que {@link #paraOtros(FiltroEquipos)} <b>más la asimetría de Estado de
     * Procesos</b>: con el campo institución escrito, ningún "otros" entra.
     *
     * <h2>De dónde sale {@code 1 = 0} y por qué no es un truco</h2>
     * El filtrado en memoria que esta pantalla hacía —el {@code CdeFilterStrategy} que el Paso 11
     * borró, transcripto en {@code CdeConsultaDAOTest.FiltradoDeReferencia}— iba por
     * {@code eq.getDescripcionSecundaria()}, que para
     * {@code EquipoOtros} devuelve <b>cadena vacía</b> por diseño
     * ({@code EquipoOtros.getDescripcionSecundaria()}), y lo pasa por
     * {@code TextFilterUtils.containsIgnoreCase(valor, filtro)}, que es verdadero cuando el filtro
     * está vacío y falso en cuanto tiene texto —{@code "".contains("x")} es {@code false}—. O sea
     * que hoy, en esa pantalla, <b>los "otros" aparecen sólo mientras el campo institución esté en
     * blanco</b>. {@code 1 = 0} es la traducción literal de esa constante, no una simplificación:
     * cualquier otra cosa cambiaría lo que el operador ve.
     *
     * <p>Efecto secundario bueno: MySQL y H2 reconocen la condición imposible y ni tocan la tabla
     * (el {@code EXPLAIN} lo dice: <em>Impossible WHERE</em>), así que la rama de la unión sale
     * gratis en vez de barrer {@code equipo_otros} para descartarlo todo.
     *
     * <p><b>Que esto viva acá y no en el controller es el punto del paso.</b> Si la asimetría se
     * quedara en memoria, filtraría los 50 de la página en vez de las 50 primeras de las que
     * matchean.
     */
    public static Condicion paraOtrosEnUnionCde(FiltroEquipos filtro) {
        Condicion base = paraOtros(filtro);
        if (!tieneTexto(filtro.institucion())) {
            return base;
        }
        String sql = base.sql().isEmpty() ? " WHERE 1 = 0" : base.sql() + " AND 1 = 0";
        return new Condicion(base.joins(), sql, base.parametros());
    }

    // ── piezas ───────────────────────────────────────────────────────────────

    /**
     * {@code IN (?, ?, …)} sobre la columna de estado del agregado.
     *
     * <p>Es la <b>columna</b> y no un {@code MIN(CASE …)} sobre los materiales: vale exactamente lo
     * mismo que {@code calcularEstado()}, y hay un test que lo sostiene fila por fila
     * ({@code EstadoPersistidoEsElCalculadoTest}). Replicar el cálculo acá duplicaría lógica de
     * dominio en SQL y, de paso, anularía {@code idx_equipos_estado} / {@code idx_otros_estado}
     * (V23).
     *
     * <p>Sin {@code UPPER()}/{@code LOWER()} alrededor de la columna, que también anularía el
     * índice: los valores que guarda son siempre {@code EstadoEquipo.getNombre()} y del combo
     * salen esos mismos strings.
     */
    private static void estados(String columna, FiltroEquipos filtro,
                                List<String> clausulas, List<Object> parametros) {
        enLista(columna, filtro.estados(), clausulas, parametros);
    }

    private static void enLista(String columna, List<String> valores,
                                List<String> clausulas, List<Object> parametros) {
        if (valores.isEmpty()) {
            return;
        }
        clausulas.add(columna + " IN (" + marcadores(valores.size()) + ")");
        parametros.addAll(valores);
    }

    /**
     * Substring insensible a mayúsculas, igual que el {@code contains} de la pantalla.
     *
     * <p>⚠️ <b>No usa índice</b>, y es aceptable: el predicado selectivo de estas consultas es la
     * fecha o el estado, que sí lo usan. Lo que no sería aceptable es que cambiara de significado:
     * {@code contains} de Java no conoce comodines, así que un {@code %} o un {@code _} tipeados
     * por el operador se escapan y se buscan tal cual (ver {@link #comoContiene(String)}).
     */
    private static boolean contiene(String columna, String texto,
                                    List<String> clausulas, List<Object> parametros) {
        if (!tieneTexto(texto)) {
            return false;
        }
        clausulas.add("LOWER(" + columna + ") LIKE ? ESCAPE '!'");
        parametros.add(comoContiene(texto));
        return true;
    }

    /**
     * Rango por día, los dos extremos inclusive, extremo nulo = abierto.
     *
     * <p>Dos cosas que la traducción tiene que respetar:</p>
     * <ul>
     *   <li><b>{@code hasta} es inclusivo por día, y en SQL eso es {@code < hasta + 1 día}.</b> La
     *       columna es un {@code TIMESTAMP}: con {@code <= hasta} se perderían los equipos
     *       ingresados ese mismo día después de medianoche, que son casi todos.</li>
     *   <li><b>Un equipo sin fecha pasa sólo si los dos extremos son nulos.</b>
     *       {@code fecha_ingreso} es nullable en las dos tablas (V1, V2). En SQL,
     *       {@code NULL >= ?} y {@code NULL < ?} son desconocidos y no pasan; sin extremos no hay
     *       cláusula y pasa. Coincide con el {@code cumpleFecha} que la pantalla tenía, que devolvía
     *       {@code desde == null && hasta == null} para fecha nula. La rama existe traducida, no
     *       borrada.</li>
     * </ul>
     */
    private static void fechas(String columna, FiltroEquipos filtro,
                               List<String> clausulas, List<Object> parametros) {
        if (filtro.desde() != null) {
            clausulas.add(columna + " >= ?");
            parametros.add(Timestamp.valueOf(filtro.desde().atStartOfDay()));
        }
        if (filtro.hasta() != null) {
            clausulas.add(columna + " < ?");
            parametros.add(Timestamp.valueOf(filtro.hasta().plusDays(1).atStartOfDay()));
        }
    }

    private static Condicion armar(String joins, List<String> clausulas, List<Object> parametros) {
        return clausulas.isEmpty()
            ? Condicion.VACIA
            : new Condicion(joins, " WHERE " + String.join(" AND ", clausulas), parametros);
    }

    // ── utilidades de SQL ────────────────────────────────────────────────────

    /** {@code ?, ?, …} — tantos marcadores como valores. Nunca los valores mismos. */
    public static String marcadores(int cuantos) {
        return String.join(", ", Collections.nCopies(cuantos, "?"));
    }

    /** Pone los parámetros desde el índice 1 y devuelve el índice del siguiente libre. */
    public static int aplicar(PreparedStatement ps, List<Object> parametros) throws SQLException {
        int indice = 1;
        for (Object parametro : parametros) {
            ps.setObject(indice++, parametro);
        }
        return indice;
    }

    /** Pone los parámetros desde {@code desde} y devuelve el índice del siguiente libre. */
    public static int aplicarDesde(PreparedStatement ps, int desde, List<?> parametros)
            throws SQLException {
        int indice = desde;
        for (Object parametro : parametros) {
            ps.setObject(indice++, parametro);
        }
        return indice;
    }

    public static boolean tieneTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    /**
     * El texto tipeado, como patrón de {@code LIKE} que lo busca <b>literalmente</b> en cualquier
     * posición. Escapa los comodines de SQL con {@code !}, el carácter que declara el
     * {@code ESCAPE} de {@link #contiene}. Sin esto, buscar "50%" traería cualquier cliente que
     * empiece con "50".
     */
    private static String comoContiene(String texto) {
        String escapado = texto.toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escapado + "%";
    }
}
