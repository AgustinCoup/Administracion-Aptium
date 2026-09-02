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
import java.util.LinkedHashMap;
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
     * Filas crudas de la tabla maestra: un ingreso repetido una vez por cada combinación de
     * elemento clasificado y lavarropas que lo lavó. Se pliega en memoria a un
     * {@link IngresoHistorial} por id, y como los agregados son <b>conjuntos</b> la repetición
     * no molesta.
     *
     * <p>La cantidad de bolsas <b>no</b> sale de acá: va en {@link #SQL_BOLSAS}. Metida en estos
     * {@code LEFT JOIN}, {@code bolsas_lavadero} multiplicaría las filas y su {@code COUNT}
     * saldría inflado por la clasificación y los ciclos — el bug clásico de esta consulta.</p>
     */
    private static final String SQL_RESUMEN =
        "SELECT il.id, c.nombre AS cliente, il.fecha_ingreso, il.peso_total_kg, il.estado, " +
        "       cel.nombre AS elemento, cl.lavarropas_numero " +
        "FROM ingresos_lavadero il " +
        "JOIN clientes c                                ON c.id   = il.cliente_id " +
        "LEFT JOIN elementos_clasificacion_lavadero ecl ON ecl.ingreso_id = il.id " +
        "LEFT JOIN catalogo_elementos_lavadero cel      ON cel.id = ecl.elemento_id " +
        "LEFT JOIN elementos_ciclo_lavadero eci         ON eci.elemento_clasificacion_id = ecl.id " +
        "LEFT JOIN ciclos_lavadero cl                   ON cl.id  = eci.ciclo_id " +
        "ORDER BY il.fecha_ingreso DESC, il.id DESC";

    /** Bolsas por ingreso, sin ningún otro {@code JOIN} que pueda multiplicar el conteo. */
    private static final String SQL_BOLSAS =
        "SELECT ingreso_id, COUNT(*) AS cant_bolsas FROM bolsas_lavadero GROUP BY ingreso_id";

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

    /** Todos los ingresos, del más reciente al más viejo. El filtrado es en memoria. */
    public List<IngresoHistorial> obtenerHistorial() {
        Map<Integer, Integer> bolsasPorIngreso = contarBolsas();
        Map<Integer, Acumulador> porIngreso = new LinkedHashMap<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RESUMEN);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                Acumulador acumulador = porIngreso.get(id);
                if (acumulador == null) {
                    acumulador = new Acumulador(
                        id,
                        rs.getString("cliente"),
                        fecha(rs, "fecha_ingreso"),
                        rs.getBigDecimal("peso_total_kg"),
                        bolsasPorIngreso.getOrDefault(id, 0),
                        EstadoIngresoLavadero.desdeBD(rs.getString("estado")));
                    porIngreso.put(id, acumulador);
                }
                acumulador.agregarElemento(rs.getString("elemento"));
                acumulador.agregarLavarropas(entero(rs, "lavarropas_numero"));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener el historial de lavadero", e);
        }
        return porIngreso.values().stream().map(Acumulador::aIngreso).toList();
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

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    /**
     * Un ingreso a medio plegar: los datos fijos ya leídos y los dos conjuntos creciendo fila a
     * fila. Los {@code LEFT JOIN} dejan {@code null} en elemento y lavarropas cuando el ingreso
     * todavía no llegó a esa etapa, y esos nulos no entran a los conjuntos.
     */
    private static final class Acumulador {
        private final int id;
        private final String clienteNombre;
        private final LocalDateTime fechaIngreso;
        private final BigDecimal pesoTotalKg;
        private final int cantBolsas;
        private final EstadoIngresoLavadero estado;
        private final Set<String> elementos = new LinkedHashSet<>();
        private final Set<Integer> lavarropas = new LinkedHashSet<>();

        private Acumulador(int id, String clienteNombre, LocalDateTime fechaIngreso,
                           BigDecimal pesoTotalKg, int cantBolsas,
                           EstadoIngresoLavadero estado) {
            this.id = id;
            this.clienteNombre = clienteNombre;
            this.fechaIngreso = fechaIngreso;
            this.pesoTotalKg = pesoTotalKg;
            this.cantBolsas = cantBolsas;
            this.estado = estado;
        }

        private void agregarElemento(String elemento) {
            if (elemento != null) elementos.add(elemento);
        }

        private void agregarLavarropas(Integer numero) {
            if (numero != null) lavarropas.add(numero);
        }

        private IngresoHistorial aIngreso() {
            return new IngresoHistorial(id, clienteNombre, fechaIngreso, pesoTotalKg,
                cantBolsas, estado, elementos, lavarropas);
        }
    }
}
