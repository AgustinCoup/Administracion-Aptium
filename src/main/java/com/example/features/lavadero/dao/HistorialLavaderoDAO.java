package com.example.features.lavadero.dao;

import com.example.common.exception.DatabaseException;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.dao.helpers.AgrupadorLineasHistorial;
import com.example.features.lavadero.dao.helpers.FilaHistorialCruda;
import com.example.features.lavadero.model.DestinoSalida;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.FiltroHistorial;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.LineaHistorial;
import com.example.infrastructure.db.ConnectionPool;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JDBC de sólo lectura del historial de lavadero: la tabla maestra de ingresos y, bajo demanda,
 * la trazabilidad completa de uno solo. <b>No escribe nada</b> — no hay un
 * {@code INSERT}/{@code UPDATE}/{@code DELETE} en toda la clase.
 *
 * <p>Rehace recorridos casi idénticos a los de {@link SalidaLavaderoDAO}, con dos diferencias de
 * fondo: no filtra por estado (el historial muestra también lo ya derivado) y ancla el detalle en
 * la <b>clasificación</b> y no en {@code salidas_lavadero}, para no perder las líneas clasificadas
 * que todavía no se lavaron — que son justamente las que un ingreso {@code CLASIFICADO} tiene
 * para mostrar.</p>
 *
 * <p><b>Las cantidades del detalle cierran contra lo clasificado.</b> Es la propiedad que hace
 * confiable a un historial, y sale de que las tres consultas del detalle se reparten las líneas
 * sin superponerse: las tandas regulares ya lanzadas ({@link #SQL_DETALLE_REGULAR}), las
 * fracciones de equipo ({@link #SQL_DETALLE_INSTANCIA}) y el saldo todavía sin lanzar
 * ({@link #SQL_DETALLE_SIN_LANZAR}).</p>
 *
 * <p><b>Limitación aceptada:</b> el historial trabaja al grano de la <i>tanda</i>, no de la unidad
 * doblada. Si de una tanda de 5 se marcaron Listo 3, la línea muestra cantidad 5 con la
 * {@code fechaListo} puesta; y si además una parte ya se derivó, muestra ese destino para la tanda
 * entera. Abrirlo al grano de la unidad duplicaría el modelo de Salidas sin que nadie lo haya
 * pedido.</p>
 *
 * <p>Manejo de errores como {@link SalidaLavaderoDAO} y no como los DAOs viejos del lavadero: un
 * fallo de SQL sale como {@link DatabaseException}. Devolver una lista vacía le haría mostrar
 * "no hay historial" a una pantalla que en realidad no pudo leer.</p>
 */
public class HistorialLavaderoDAO {

    private final AgrupadorLineasHistorial agrupador = new AgrupadorLineasHistorial();

    /**
     * La tabla maestra: <b>exactamente una fila por ingreso</b>, de la más reciente a la más
     * vieja.
     *
     * <p>Ninguno de los tres agregados que muestra la pantalla —bolsas, elementos y lavarropas—
     * se calcula acá, y no es una omisión: colgar cualquiera de ellos de esta consulta la
     * convierte en un producto. Enganchar {@code elementos_clasificacion_lavadero →
     * elementos_ciclo_lavadero → ciclos_lavadero} con {@code LEFT JOIN} —que es lo que esta
     * consulta hacía— multiplica cada ingreso por (líneas clasificadas × ciclos que las lavaron):
     * un ingreso de 6 líneas lavadas en ~1,5 ciclos rinde ~9 filas para plegar a <b>una</b>, y el
     * costo crece con la historia entera sin techo. {@code bolsas_lavadero} inflaría además el
     * {@code COUNT}, que es el bug clásico de esta consulta.</p>
     *
     * <p>Cada agregado va en su propia consulta —{@link #SQL_BOLSAS},
     * {@link #SQL_ELEMENTOS_POR_INGRESO}, {@link #SQL_LAVARROPAS_POR_INGRESO}— y se cruza en
     * memoria por {@code ingreso_id}. Las tres son planas y se leen una sola vez por refresco.</p>
     *
     * <p><b>Por qué el cuerpo y el orden viven en constantes separadas.</b> {@link #SQL_RESUMEN}
     * termina en su propio {@code ORDER BY}: concatenarle un {@code WHERE} detrás produce SQL
     * inválido. Con el cuerpo y el orden separados, la consulta paginada se arma como
     * {@code cuerpo + where + orden + LIMIT ? OFFSET ?} y la del conteo como
     * {@code SQL_CONTAR_CUERPO + where}, con el <b>mismo</b> {@code where}.</p>
     *
     * <p>El {@code JOIN} con {@code clientes} no es opcional ni siquiera para contar: el filtro de
     * cliente es sobre {@code c.nombre}. Que las dos consultas tengan el mismo {@code FROM} es lo
     * que permite que un solo método arme el {@code WHERE} de las dos.</p>
     */
    private static final String SQL_RESUMEN_CUERPO =
        "SELECT il.id, c.nombre AS cliente, il.fecha_ingreso, il.peso_total_kg, il.estado " +
        "FROM ingresos_lavadero il " +
        "JOIN clientes c ON c.id = il.cliente_id";

    /** El orden de la maestra. Va <b>después</b> del {@code WHERE}; ver {@link #SQL_RESUMEN_CUERPO}. */
    private static final String SQL_RESUMEN_ORDEN =
        " ORDER BY il.fecha_ingreso DESC, il.id DESC";

    /** Cuántos ingresos matchean el filtro. Mismo {@code FROM} que {@link #SQL_RESUMEN_CUERPO}. */
    private static final String SQL_CONTAR_CUERPO =
        "SELECT COUNT(*) FROM ingresos_lavadero il JOIN clientes c ON c.id = il.cliente_id";

    /** La maestra completa, sin filtro ni paginación. La usa {@link #obtenerHistorial()}. */
    private static final String SQL_RESUMEN = SQL_RESUMEN_CUERPO + SQL_RESUMEN_ORDEN;

    /**
     * Bolsas por ingreso, sin ningún otro {@code JOIN} que pueda multiplicar el conteo. Partido
     * en cuerpo y agrupación por el mismo motivo que la maestra: el {@code WHERE} que acota a los
     * ids de una página va <b>entre</b> los dos.
     */
    private static final String SQL_BOLSAS_CUERPO =
        "SELECT ingreso_id, COUNT(*) AS cant_bolsas FROM bolsas_lavadero";

    private static final String SQL_BOLSAS_AGRUPACION = " GROUP BY ingreso_id";

    private static final String SQL_BOLSAS = SQL_BOLSAS_CUERPO + SQL_BOLSAS_AGRUPACION;

    /**
     * Qué elementos tiene clasificados cada ingreso. Alimenta sólo el filtro "Elemento:" de la
     * pantalla — ninguna columna de la tabla los muestra.
     */
    private static final String SQL_ELEMENTOS_POR_INGRESO =
        "SELECT DISTINCT ecl.ingreso_id, cel.nombre " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id";

    /**
     * En qué lavarropas se lavó cada ingreso. Los {@code JOIN} son internos: un ingreso
     * clasificado y todavía sin lanzar no aparece acá, que es exactamente lo que corresponde —su
     * conjunto de lavarropas queda vacío.
     */
    private static final String SQL_LAVARROPAS_POR_INGRESO =
        "SELECT DISTINCT ecl.ingreso_id, cl.lavarropas_numero " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "JOIN ciclos_lavadero cl           ON cl.id = eci.ciclo_id";

    /**
     * Tandas regulares ya lanzadas de un ingreso, una línea por fila de
     * {@code elementos_ciclo_lavadero}.
     *
     * <p>El {@code JOIN} contra {@code elementos_ciclo_lavadero} es interno y no externo a
     * propósito: lo clasificado y todavía sin lanzar lo emite {@link #SQL_DETALLE_SIN_LANZAR}, y
     * con un {@code LEFT JOIN} esas líneas saldrían por partida doble. Además, una línea
     * consumida entera por un {@code Equipo*} repartido no tiene ninguna fila regular, así que
     * un {@code LEFT JOIN} le inventaría una línea "sin lavar" que ya está contada como
     * fracción.</p>
     *
     * <p>Las salidas se agregan por tanda ({@code GROUP BY eci.id}) porque una tanda puede tener
     * más de una fila en {@code salidas_lavadero} — se deriva una parte y después se marca el
     * resto. Sin el {@code GROUP BY} esa tanda aparecería dos veces con la misma cantidad y el
     * detalle dejaría de cerrar.</p>
     */
    private static final String SQL_DETALLE_REGULAR =
        "SELECT cel.nombre AS elemento, eci.cantidad, cl.lavarropas_numero, cl.fecha_fin, " +
        "       MAX(sl.fecha_listo) AS fecha_listo, MAX(sl.destino) AS destino " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel  ON cel.id = ecl.elemento_id " +
        "JOIN elementos_ciclo_lavadero eci     ON eci.elemento_clasificacion_id = ecl.id " +
        "                                     AND eci.instancia_equipo_id IS NULL " +
        "JOIN ciclos_lavadero cl               ON cl.id  = eci.ciclo_id " +
        "LEFT JOIN salidas_lavadero sl         ON sl.elemento_ciclo_id = eci.id " +
        "WHERE ecl.ingreso_id = ? " +
        "GROUP BY eci.id, cel.nombre, eci.cantidad, cl.lavarropas_numero, cl.fecha_fin " +
        "ORDER BY cel.nombre";

    /**
     * Fracciones de {@code Equipo*} repartido, una fila por lavarropas; las une
     * {@link AgrupadorLineasHistorial}. Sin filtro por {@code fecha_fin}: el agrupador necesita
     * ver las fracciones de ciclos todavía activos para saber que la instancia entera no está
     * lavada.
     */
    private static final String SQL_DETALLE_INSTANCIA =
        "SELECT eci.instancia_equipo_id, ie.total_partes, cel.nombre AS elemento, " +
        "       cl.lavarropas_numero, cl.fecha_fin, sl.fecha_listo, sl.destino " +
        "FROM elementos_ciclo_lavadero eci " +
        "JOIN instancias_equipo_ciclo ie            ON ie.id  = eci.instancia_equipo_id " +
        "JOIN elementos_clasificacion_lavadero ecl  ON ecl.id = eci.elemento_clasificacion_id " +
        "JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id " +
        "JOIN ciclos_lavadero cl                    ON cl.id  = eci.ciclo_id " +
        "LEFT JOIN salidas_lavadero sl              ON sl.instancia_equipo_id = eci.instancia_equipo_id " +
        "WHERE ecl.ingreso_id = ? AND eci.instancia_equipo_id IS NOT NULL";

    /**
     * Lo que queda de cada línea de clasificación sin lanzar a ningún ciclo. Una línea se puede
     * lanzar a medias (10 clasificadas, 5 en un ciclo): sin esta consulta las otras 5
     * desaparecerían del detalle y las cantidades no cerrarían contra lo clasificado.
     *
     * <p>{@code ya_procesada} es la fórmula canónica de {@code CicloLavaderoDAO.SQL_DISPONIBLES}:
     * las fracciones de un equipo repartido consumen <b>una</b> unidad de su línea, no N, así que
     * se cuentan instancias distintas y no filas.</p>
     */
    private static final String SQL_DETALLE_SIN_LANZAR =
        "SELECT cel.nombre AS elemento, ecl.cantidad, " +
        "       COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0) " +
        "         + COUNT(DISTINCT eci.instancia_equipo_id) AS ya_procesada " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel   ON cel.id = ecl.elemento_id " +
        "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "WHERE ecl.ingreso_id = ? " +
        "GROUP BY ecl.id, cel.nombre, ecl.cantidad " +
        "HAVING ya_procesada < ecl.cantidad " +
        "ORDER BY cel.nombre";

    // ── tabla maestra ────────────────────────────────────────────────────────

    /**
     * Todos los ingresos, del más reciente al más viejo, <b>sin filtrar y sin paginar</b>.
     *
     * <p><b>La pantalla ya no lo usa</b> —lee por {@link #obtenerPagina(FiltroHistorial,
     * CriteriosPagina)}, con los filtros en SQL—, pero el método se queda: es la implementación de
     * referencia contra la que el test de equivalencia compara los cinco filtros traducidos. Sin
     * él no habría con qué demostrar que la traducción a SQL no cambió la semántica, que es
     * justamente lo que un error de traducción no hace visible.</p>
     *
     * <p>Cuatro consultas planas en vez de una con fan-out (ver {@link #SQL_RESUMEN}): la maestra
     * y los tres agregados, cruzados por {@code ingreso_id}. Un ingreso sin agregados —recién
     * ingresado, o clasificado y todavía sin lavar— queda con esos conjuntos vacíos, que es lo que
     * el {@code LEFT JOIN} daba antes.</p>
     *
     * <p><b>La maestra se lee primero, y no es indistinto.</b> Las cuatro consultas van en
     * conexiones distintas, así que entre la primera y la última otro operador sigue trabajando.
     * Con los agregados adelante, un ingreso clasificado en esa ventana aparecería como
     * {@code CLASIFICADO} y con la lista de elementos vacía —o sea, invisible para el filtro
     * "Elemento:"—, que es peor que no mostrarlo: la fila está y miente. Leyendo la maestra
     * primero, el desfase sobra para el otro lado: los agregados pueden traer filas de ingresos
     * que la maestra no tiene, y ésas simplemente no se usan.</p>
     */
    public List<IngresoHistorial> obtenerHistorial() {
        List<FilaResumen> maestra = leerResumen();

        Map<Integer, Integer>      bolsas     = contarBolsas();
        Map<Integer, Set<String>>  elementos  = agruparPorIngreso(
            SQL_ELEMENTOS_POR_INGRESO, rs -> rs.getString("nombre"),
            "Error al obtener los elementos clasificados de los ingresos de lavadero");
        Map<Integer, Set<Integer>> lavarropas = agruparPorIngreso(
            SQL_LAVARROPAS_POR_INGRESO, rs -> rs.getInt("lavarropas_numero"),
            "Error al obtener los lavarropas de los ingresos de lavadero");

        return maestra.stream()
            .map(fila -> new IngresoHistorial(
                fila.id(), fila.clienteNombre(), fila.fechaIngreso(), fila.pesoTotalKg(),
                bolsas.getOrDefault(fila.id(), 0), fila.estado(),
                elementos.getOrDefault(fila.id(), Set.of()),
                lavarropas.getOrDefault(fila.id(), Set.of())))
            .toList();
    }

    /** Un ingreso tal como sale de {@link #SQL_RESUMEN}, sin los agregados todavía. */
    private record FilaResumen(int id, String clienteNombre, LocalDateTime fechaIngreso,
                               BigDecimal pesoTotalKg, EstadoIngresoLavadero estado) {
    }

    private List<FilaResumen> leerResumen() {
        List<FilaResumen> filas = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RESUMEN);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                filas.add(new FilaResumen(
                    rs.getInt("id"),
                    rs.getString("cliente"),
                    fecha(rs, "fecha_ingreso"),
                    rs.getBigDecimal("peso_total_kg"),
                    EstadoIngresoLavadero.desdeBD(rs.getString("estado"))));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener el historial de lavadero", e);
        }
        return filas;
    }

    /** Cómo sacar el valor de una fila {@code (ingreso_id, valor)}. */
    @FunctionalInterface
    private interface ExtractorValor<T> {
        T extraer(ResultSet rs) throws SQLException;
    }

    /**
     * Pliega una consulta de la forma {@code (ingreso_id, valor)} a
     * {@code ingreso_id → conjunto de valores}. Los dos agregados tienen la misma forma y el
     * mismo cruce posterior; escribirlo dos veces sería la copia que este refactor vino a evitar.
     */
    private <T> Map<Integer, Set<T>> agruparPorIngreso(String sql, ExtractorValor<T> extractor,
                                                       String queFalla) {
        Map<Integer, Set<T>> agrupado = new HashMap<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                agrupado.computeIfAbsent(rs.getInt("ingreso_id"), k -> new LinkedHashSet<>())
                        .add(extractor.extraer(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException(queFalla, e);
        }
        return agrupado;
    }

    private Map<Integer, Integer> contarBolsas() {
        Map<Integer, Integer> conteo = new HashMap<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BOLSAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                conteo.put(rs.getInt("ingreso_id"), rs.getInt("cant_bolsas"));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al contar las bolsas de los ingresos de lavadero", e);
        }
        return conteo;
    }

    // ── tabla maestra paginada ───────────────────────────────────────────────

    /**
     * Una página de la tabla maestra, con <b>los cinco filtros resueltos en SQL</b> y el total de
     * ingresos que matchean.
     *
     * <h2>Los cinco filtros van a SQL o ninguno</h2>
     * Filtrar en memoria una página traída con {@code LIMIT} filtra 50 de 5000 en vez de las 50
     * primeras de las que matchean: el resultado es incorrecto y ningún test de "la página trae 50
     * filas" lo nota. Por eso {@link #where(FiltroHistorial)} traduce los cinco y no queda ninguno
     * en {@code IngresoHistorial}.
     *
     * <h2>Los tres agregados se acotan a los ids de la página</h2>
     * Ahí está el verdadero ahorro: sin el {@code IN (…)}, bolsas, elementos y lavarropas seguirían
     * barriendo la historia entera en cada refresco y la paginación ahorraría el transporte pero no
     * el trabajo del servidor.
     *
     * <h2>Una sola conexión para las cuatro consultas</h2>
     * {@link #obtenerHistorial()} toma cuatro checkouts; éste toma uno y se lo pasa a los métodos
     * privados. No las anida —son secuenciales—, así que sigue valiendo el invariante del que
     * depende la aritmética del semáforo de {@code ConnectionPool}: ninguna operación mantiene dos
     * conexiones abiertas a la vez.
     *
     * <p>Pedir una página más allá del total devuelve una página vacía con el total correcto y
     * <b>no</b> lanza: el operador puede tener abierta la página 7 cuando el filtro pasó a tener
     * dos.</p>
     */
    public Pagina<IngresoHistorial> obtenerPagina(FiltroHistorial filtro, CriteriosPagina criterios) {
        try (Connection conn = ConnectionPool.getConnection()) {
            return armarPagina(conn, filtro, criterios, contar(conn, filtro));
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener el historial de lavadero", e);
        }
    }

    /**
     * La misma página, pero con el total ya sabido: <b>no vuelve a contar</b>.
     *
     * <p>El total sólo cambia cuando cambian los <em>filtros</em>. Ir de la página 3 a la 4 con el
     * mismo filtro no necesita un {@code COUNT(*)} nuevo, y hacerlo igual duplica las consultas
     * que esta paginación vino a ahorrar. El llamador es responsable de pasar el total que leyó
     * con <b>este mismo filtro</b>: pasarle el de otro filtro hace que la UI dibuje pestañas que
     * no existen.
     *
     * @param totalConocido total leído antes con el mismo filtro, no negativo
     */
    public Pagina<IngresoHistorial> obtenerPagina(FiltroHistorial filtro, CriteriosPagina criterios,
                                                  long totalConocido) {
        if (totalConocido < 0) {
            throw new IllegalArgumentException("totalConocido no puede ser negativo: " + totalConocido);
        }
        try (Connection conn = ConnectionPool.getConnection()) {
            return armarPagina(conn, filtro, criterios, totalConocido);
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener el historial de lavadero", e);
        }
    }

    /** Cuántos ingresos matchean el filtro, con el <b>mismo</b> {@code WHERE} que la página. */
    public long contarHistorial(FiltroHistorial filtro) {
        try (Connection conn = ConnectionPool.getConnection()) {
            return contar(conn, filtro);
        } catch (SQLException e) {
            throw new DatabaseException("Error al contar el historial de lavadero", e);
        }
    }

    private Pagina<IngresoHistorial> armarPagina(Connection conn, FiltroHistorial filtro,
                                                 CriteriosPagina criterios, long total) {
        List<FilaResumen> maestra = leerResumenPaginado(conn, filtro, criterios);
        if (maestra.isEmpty()) {
            return new Pagina<>(List.of(), criterios.numeroPagina(), criterios.tamanioPagina(), total);
        }

        List<Integer> ids = maestra.stream().map(FilaResumen::id).toList();

        Map<Integer, Integer>      bolsas     = contarBolsas(conn, ids);
        Map<Integer, Set<String>>  elementos  = agruparPorIngreso(
            conn, SQL_ELEMENTOS_POR_INGRESO + whereIngresosEn("ecl.ingreso_id", ids), ids,
            rs -> rs.getString("nombre"),
            "Error al obtener los elementos clasificados de los ingresos de lavadero");
        Map<Integer, Set<Integer>> lavarropas = agruparPorIngreso(
            conn, SQL_LAVARROPAS_POR_INGRESO + whereIngresosEn("ecl.ingreso_id", ids), ids,
            rs -> rs.getInt("lavarropas_numero"),
            "Error al obtener los lavarropas de los ingresos de lavadero");

        List<IngresoHistorial> contenido = maestra.stream()
            .map(fila -> new IngresoHistorial(
                fila.id(), fila.clienteNombre(), fila.fechaIngreso(), fila.pesoTotalKg(),
                bolsas.getOrDefault(fila.id(), 0), fila.estado(),
                elementos.getOrDefault(fila.id(), Set.of()),
                lavarropas.getOrDefault(fila.id(), Set.of())))
            .toList();

        return new Pagina<>(contenido, criterios.numeroPagina(), criterios.tamanioPagina(), total);
    }

    private List<FilaResumen> leerResumenPaginado(Connection conn, FiltroHistorial filtro,
                                                  CriteriosPagina criterios) {
        Condicion condicion = where(filtro);
        String sql = SQL_RESUMEN_CUERPO + condicion.sql() + SQL_RESUMEN_ORDEN + " LIMIT ? OFFSET ?";

        List<FilaResumen> filas = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int siguiente = aplicar(ps, condicion.parametros());
            ps.setInt(siguiente, criterios.tamanioPagina());
            ps.setLong(siguiente + 1, criterios.offset());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(new FilaResumen(
                        rs.getInt("id"),
                        rs.getString("cliente"),
                        fecha(rs, "fecha_ingreso"),
                        rs.getBigDecimal("peso_total_kg"),
                        EstadoIngresoLavadero.desdeBD(rs.getString("estado"))));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener el historial de lavadero", e);
        }
        return filas;
    }

    private long contar(Connection conn, FiltroHistorial filtro) {
        Condicion condicion = where(filtro);
        try (PreparedStatement ps = conn.prepareStatement(SQL_CONTAR_CUERPO + condicion.sql())) {
            aplicar(ps, condicion.parametros());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al contar el historial de lavadero", e);
        }
    }

    private Map<Integer, Integer> contarBolsas(Connection conn, List<Integer> ids) {
        Map<Integer, Integer> conteo = new HashMap<>();
        String sql = SQL_BOLSAS_CUERPO + whereIngresosEn("ingreso_id", ids) + SQL_BOLSAS_AGRUPACION;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            aplicar(ps, List.copyOf(ids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    conteo.put(rs.getInt("ingreso_id"), rs.getInt("cant_bolsas"));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al contar las bolsas de los ingresos de lavadero", e);
        }
        return conteo;
    }

    private <T> Map<Integer, Set<T>> agruparPorIngreso(Connection conn, String sql, List<Integer> ids,
                                                       ExtractorValor<T> extractor, String queFalla) {
        Map<Integer, Set<T>> agrupado = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            aplicar(ps, List.copyOf(ids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    agrupado.computeIfAbsent(rs.getInt("ingreso_id"), k -> new LinkedHashSet<>())
                            .add(extractor.extraer(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(queFalla, e);
        }
        return agrupado;
    }

    // ── el WHERE, uno solo para la página y para el conteo ───────────────────

    /**
     * Un {@code WHERE} armado: el fragmento de SQL y los valores que lo acompañan, en orden.
     *
     * <p>El fragmento sale de constantes del código; los valores viajan siempre por {@code ?}.
     * Nunca se concatena input del operador en la sentencia.</p>
     */
    private record Condicion(String sql, List<Object> parametros) {
    }

    private static final String CLAUSULA_CLIENTE   = "LOWER(c.nombre) LIKE ? ESCAPE '!'";
    private static final String CLAUSULA_DESDE     = "il.fecha_ingreso >= ?";
    private static final String CLAUSULA_HASTA     = "il.fecha_ingreso < ?";

    private static final String CLAUSULA_ELEMENTO =
        "EXISTS (SELECT 1 FROM elementos_clasificacion_lavadero ecl " +
        "        JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id " +
        "        WHERE ecl.ingreso_id = il.id AND LOWER(cel.nombre) LIKE ? ESCAPE '!')";

    private static final String CLAUSULA_LAVARROPAS =
        "EXISTS (SELECT 1 FROM elementos_clasificacion_lavadero ecl " +
        "        JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "        JOIN ciclos_lavadero cl           ON cl.id = eci.ciclo_id " +
        "        WHERE ecl.ingreso_id = il.id AND cl.lavarropas_numero = ?)";

    /**
     * Traduce el filtro de la pantalla al {@code WHERE} de la consulta.
     *
     * <p><b>Que lo arme un solo método no es estilo, es corrección.</b> Lo usan la página y el
     * conteo; si divergieran, la UI diría "127 resultados" y mostraría otra cosa, y nadie lo
     * notaría hasta que alguien contara a mano.</p>
     *
     * <p>Detalles que la traducción tiene que respetar, uno por uno:</p>
     * <ul>
     *   <li><b>Cliente y elemento son substrings literales.</b> {@code contains} de Java no conoce
     *       comodines, así que un {@code %} o un {@code _} tipeados por el operador se escapan
     *       ({@link #comoContiene(String)}) y se buscan tal cual. Sin eso, buscar "50%" traería
     *       cualquier cliente que empiece con "50".</li>
     *   <li><b>{@code hasta} es inclusivo por día, y en SQL eso es {@code < hasta + 1 día}.</b> Con
     *       {@code <= hasta} se perderían los ingresos de ese mismo día después de medianoche, que
     *       son casi todos: la columna es un {@code TIMESTAMP}, no un {@code DATE}.</li>
     *   <li><b>Un ingreso sin fecha pasa sólo si los dos extremos son nulos.</b>
     *       {@code ingresos_lavadero.fecha_ingreso} <b>es</b> nullable (V7). En SQL,
     *       {@code NULL >= ?} y {@code NULL < ?} son desconocidos y no pasan; sin extremos no hay
     *       cláusula y pasa. La rama existe, traducida, no borrada.</li>
     *   <li><b>Los estados van en mayúsculas y sin {@code UPPER()} sobre la columna.</b> El filtro
     *       de la vieja estrategia comparaba con {@code equalsIgnoreCase}, pero la columna guarda
     *       siempre {@code EstadoIngresoLavadero.name()}: alcanza con normalizar el parámetro, y
     *       así el {@code IN} sigue usando {@code idx_ingresos_lav_estado}, que envolver la columna
     *       en una función anularía.</li>
     * </ul>
     */
    private static Condicion where(FiltroHistorial filtro) {
        List<String> clausulas = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();

        if (tieneTexto(filtro.cliente())) {
            clausulas.add(CLAUSULA_CLIENTE);
            parametros.add(comoContiene(filtro.cliente()));
        }
        if (!filtro.estados().isEmpty()) {
            clausulas.add("il.estado IN (" + marcadores(filtro.estados().size()) + ")");
            filtro.estados().forEach(estado -> parametros.add(estado.toUpperCase(Locale.ROOT)));
        }
        if (filtro.desde() != null) {
            clausulas.add(CLAUSULA_DESDE);
            parametros.add(Timestamp.valueOf(filtro.desde().atStartOfDay()));
        }
        if (filtro.hasta() != null) {
            clausulas.add(CLAUSULA_HASTA);
            parametros.add(Timestamp.valueOf(filtro.hasta().plusDays(1).atStartOfDay()));
        }
        if (tieneTexto(filtro.elemento())) {
            clausulas.add(CLAUSULA_ELEMENTO);
            parametros.add(comoContiene(filtro.elemento()));
        }
        if (filtro.lavarropas() != null) {
            clausulas.add(CLAUSULA_LAVARROPAS);
            parametros.add(filtro.lavarropas());
        }

        return clausulas.isEmpty()
            ? new Condicion("", List.of())
            : new Condicion(" WHERE " + String.join(" AND ", clausulas), parametros);
    }

    /** {@code WHERE <columna> IN (?, ?, …)} para acotar un agregado a los ids de una página. */
    private static String whereIngresosEn(String columna, List<Integer> ids) {
        return " WHERE " + columna + " IN (" + marcadores(ids.size()) + ")";
    }

    private static String marcadores(int cuantos) {
        return String.join(", ", Collections.nCopies(cuantos, "?"));
    }

    private static boolean tieneTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    /**
     * El texto tipeado, como patrón de {@code LIKE} que lo busca <b>literalmente</b> en cualquier
     * posición. Escapa los comodines de SQL con {@code !}, el carácter que declara el
     * {@code ESCAPE} de las dos cláusulas que la usan.
     */
    private static String comoContiene(String texto) {
        String escapado = texto.toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escapado + "%";
    }

    /** Pone los parámetros desde el índice 1 y devuelve el índice del siguiente libre. */
    private static int aplicar(PreparedStatement ps, List<Object> parametros) throws SQLException {
        int indice = 1;
        for (Object parametro : parametros) {
            ps.setObject(indice++, parametro);
        }
        return indice;
    }

    // ── detalle de un ingreso ────────────────────────────────────────────────

    /**
     * Trazabilidad completa de un ingreso: tandas regulares lanzadas, fracciones de equipo ya
     * agrupadas y el saldo clasificado que todavía no salió a ningún ciclo.
     */
    public List<LineaHistorial> findDetalle(int ingresoId) {
        List<LineaHistorial> lineas = new ArrayList<>();
        lineas.addAll(detalleRegular(ingresoId));
        lineas.addAll(agrupador.agrupar(filasDeInstancia(ingresoId)));
        lineas.addAll(detalleSinLanzar(ingresoId));
        return lineas;
    }

    private List<LineaHistorial> detalleRegular(int ingresoId) {
        List<LineaHistorial> lineas = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DETALLE_REGULAR)) {
            ps.setInt(1, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lineas.add(new LineaHistorial(
                        rs.getString("elemento"),
                        rs.getInt("cantidad"),
                        String.valueOf(rs.getInt("lavarropas_numero")),
                        null,
                        fecha(rs, "fecha_fin"),
                        fecha(rs, "fecha_listo"),
                        DestinoSalida.desdeBD(rs.getString("destino"))));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(
                "Error al obtener el detalle del ingreso de lavadero " + ingresoId, e);
        }
        return lineas;
    }

    private List<FilaHistorialCruda> filasDeInstancia(int ingresoId) {
        List<FilaHistorialCruda> filas = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DETALLE_INSTANCIA)) {
            ps.setInt(1, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(new FilaHistorialCruda(
                        rs.getInt("instancia_equipo_id"),
                        rs.getInt("total_partes"),
                        rs.getString("elemento"),
                        rs.getInt("lavarropas_numero"),
                        fecha(rs, "fecha_fin"),
                        fecha(rs, "fecha_listo"),
                        DestinoSalida.desdeBD(rs.getString("destino"))));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(
                "Error al obtener las fracciones de equipo del ingreso de lavadero " + ingresoId, e);
        }
        return filas;
    }

    /** Lo clasificado y todavía sin lanzar: sin lavarropas, sin fechas y sin destino. */
    private List<LineaHistorial> detalleSinLanzar(int ingresoId) {
        List<LineaHistorial> lineas = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DETALLE_SIN_LANZAR)) {
            ps.setInt(1, ingresoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lineas.add(new LineaHistorial(
                        rs.getString("elemento"),
                        rs.getInt("cantidad") - rs.getInt("ya_procesada"),
                        null, null, null, null, null));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(
                "Error al obtener lo pendiente de lavar del ingreso de lavadero " + ingresoId, e);
        }
        return lineas;
    }

    // ── privados ─────────────────────────────────────────────────────────────

    private static LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        Timestamp ts = rs.getTimestamp(columna);
        return ts != null ? ts.toLocalDateTime() : null;
    }

}
