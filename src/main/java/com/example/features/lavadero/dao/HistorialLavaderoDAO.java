package com.example.features.lavadero.dao;

import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.dao.helpers.AgrupadorLineasHistorial;
import com.example.features.lavadero.dao.helpers.FilaHistorialCruda;
import com.example.features.lavadero.model.DestinoSalida;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
     */
    private static final String SQL_RESUMEN =
        "SELECT il.id, c.nombre AS cliente, il.fecha_ingreso, il.peso_total_kg, il.estado " +
        "FROM ingresos_lavadero il " +
        "JOIN clientes c ON c.id = il.cliente_id " +
        "ORDER BY il.fecha_ingreso DESC, il.id DESC";

    /** Bolsas por ingreso, sin ningún otro {@code JOIN} que pueda multiplicar el conteo. */
    private static final String SQL_BOLSAS =
        "SELECT ingreso_id, COUNT(*) AS cant_bolsas FROM bolsas_lavadero GROUP BY ingreso_id";

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
     * Todos los ingresos, del más reciente al más viejo. El filtrado es en memoria.
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
