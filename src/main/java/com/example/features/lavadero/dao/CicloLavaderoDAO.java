package com.example.features.lavadero.dao;

import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.dao.helpers.LineaSobregirada;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.TipoLavado;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class CicloLavaderoDAO {

    private static final Logger log = LoggerFactory.getLogger(CicloLavaderoDAO.class);

    private static final String SQL_INSERTAR_CICLO =
        "INSERT INTO ciclos_lavadero (lavarropas_numero, jabon_id, litros_jabon, suavizante, potenciador, litros_totales, tipo_lavado, fecha_inicio, estado) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 'ACTIVO')";

    private static final String SQL_INSERTAR_ELEMENTO =
        "INSERT INTO elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad, instancia_equipo_id) VALUES (?, ?, ?, ?)";

    private static final String SQL_INSERTAR_INSTANCIA =
        "INSERT INTO instancias_equipo_ciclo (elemento_clasificacion_id, total_partes) VALUES (?, ?)";

    private static final String SQL_ACTIVOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.tipo_lavado, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, cl.fecha_inicio " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "WHERE cl.fecha_fin IS NULL";

    private static final String SQL_FINALIZADOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.tipo_lavado, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, " +
        "       cl.fecha_inicio, cl.fecha_fin " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "WHERE cl.fecha_fin IS NOT NULL ORDER BY cl.fecha_fin DESC";

    private static final String SQL_TODOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.tipo_lavado, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, " +
        "       cl.fecha_inicio, cl.fecha_fin " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "ORDER BY CASE WHEN cl.fecha_fin IS NULL THEN 0 ELSE 1 END, cl.fecha_fin DESC";

    private static final String SQL_ELEMENTOS_DE_CICLO =
        "SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, " +
        "       eci.cantidad AS en_ciclo, c.nombre AS cliente, cel.categoria " +
        "FROM elementos_ciclo_lavadero eci " +
        "JOIN elementos_clasificacion_lavadero ecl ON ecl.id = eci.elemento_clasificacion_id " +
        "JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id " +
        "JOIN ingresos_lavadero il                  ON il.id  = ecl.ingreso_id " +
        "JOIN clientes c                            ON c.id   = il.cliente_id " +
        "WHERE eci.ciclo_id = ? ORDER BY cel.nombre";

    private static final String SQL_DISPONIBLES =
        "SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, " +
        "       COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0) " +
        "         + COUNT(DISTINCT eci.instancia_equipo_id) AS ya_procesada, " +
        "       c.nombre AS cliente, cel.categoria " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id " +
        "JOIN ingresos_lavadero il            ON il.id  = ecl.ingreso_id " +
        "JOIN clientes c                      ON c.id   = il.cliente_id " +
        "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "WHERE il.estado = '" + EstadoIngresoLavadero.CLASIFICADO + "' " +
        "GROUP BY ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, c.nombre, cel.categoria " +
        "HAVING ya_procesada < ecl.cantidad " +
        "ORDER BY il.id, cel.nombre";

    /**
     * Detecta líneas de clasificación cuyo total procesado supera la cantidad clasificada
     * (decisión D del blueprint de fracciones de equipo). Misma fórmula que
     * {@link #SQL_DISPONIBLES}, pero sin el filtro {@code il.estado = 'CLASIFICADO'}: el dato
     * sucio puede estar en una línea ya avanzada a LAVADO/FINALIZADO. Sólo detección — no repara.
     */
    private static final String SQL_LINEAS_SOBREGIRADAS =
        "SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, " +
        "       COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0) " +
        "         + COUNT(DISTINCT eci.instancia_equipo_id) AS ya_procesada " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id " +
        "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "GROUP BY ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad " +
        "HAVING ya_procesada > ecl.cantidad " +
        "ORDER BY ecl.ingreso_id, cel.nombre";

    public Map<Integer, CicloLavadero> obtenerCiclosActivosPorLavarropas() {
        Map<Integer, CicloLavadero> mapa = new LinkedHashMap<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ACTIVOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CicloLavadero ciclo = mapearCiclo(rs);
                mapa.put(ciclo.getLavarropasNumero(), ciclo);
            }
        } catch (SQLException e) {
            log.error("Error al obtener ciclos activos", e);
        }
        return mapa;
    }

    public List<CicloLavadero> obtenerCiclosFinalizados() {
        List<CicloLavadero> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FINALIZADOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCicloCompleto(rs));
            }
        } catch (SQLException e) {
            log.error("Error al obtener ciclos finalizados", e);
        }
        return lista;
    }

    public List<CicloLavadero> obtenerTodosLosCiclos() {
        List<CicloLavadero> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_TODOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCicloCompleto(rs));
            }

        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener todos los ciclos", e);
        }
        return lista;
    }

    public List<ElementoCicloItem> obtenerElementosDisponiblesParaCiclo() {
        List<ElementoCicloItem> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DISPONIBLES);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ElementoCicloItem(
                    rs.getInt("id"),
                    rs.getInt("ingreso_id"),
                    rs.getString("nombre"),
                    rs.getInt("cantidad"),
                    rs.getInt("ya_procesada"),
                    rs.getString("cliente"),
                    rs.getString("categoria")
                ));
            }
        } catch (SQLException e) {
            log.error("Error al obtener elementos disponibles", e);
        }
        return lista;
    }

    public List<LineaSobregirada> detectarLineasSobregiradas() {
        List<LineaSobregirada> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LINEAS_SOBREGIRADAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new LineaSobregirada(
                    rs.getInt("id"),
                    rs.getInt("ingreso_id"),
                    rs.getString("nombre"),
                    rs.getInt("cantidad"),
                    rs.getInt("ya_procesada")
                ));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al detectar líneas de clasificación sobregiradas", e);
        }
        return lista;
    }

    /**
     * Lanza una tanda entera —las instancias de equipo repartido y todos los ciclos que las
     * consumen— en <b>una sola transacción</b>.
     *
     * <p>Ese alcance es el que hace consistente al reparto. Con una transacción por lavarropas,
     * un fallo a mitad de tanda dejaba una instancia con {@code total_partes = N} y menos de N
     * fracciones: {@code SQL_DISPONIBLES} ya la contaba como consumida (cuenta instancias
     * distintas, no fracciones) mientras que Salidas nunca la aceptaba como completa, así que el
     * equipo desaparecía de las dos pantallas y el ingreso no podía llegar a FINALIZADO.
     * Reintentar tampoco servía: acuñaba una segunda instancia para el mismo equipo.
     */
    public void lanzarTanda(List<LanzamientoCiclo> tanda) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            Map<Integer, Integer> instancias = crearInstancias(conn, tanda);
            for (LanzamientoCiclo ciclo : tanda) {
                int cicloId = insertarCiclo(conn, ciclo.lavarropasNumero(), ciclo.config());
                insertarMovimientos(conn, cicloId, movimientosDe(ciclo, instancias));
            }
            tx.commit();
        } catch (SQLException e) {
            log.error("Error al lanzar una tanda de {} ciclo(s)", tanda.size(), e);
            throw new DatabaseException("Error al lanzar los ciclos", e);
        }
    }

    public void finalizarCiclo(int cicloId) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            marcarFinalizado(conn, cicloId);
            Set<Integer> ingresoIds = obtenerIngresosAfectados(conn, cicloId);
            actualizarEstadoIngresosAfectados(conn, ingresoIds);
            tx.commit();
        } catch (SQLException e) {
            log.error("Error al finalizar ciclo {}", cicloId, e);
            throw new RuntimeException("Error al finalizar ciclo", e);
        }
    }

    // ── privados ─────────────────────────────────────────────────────────────

    private int insertarCiclo(Connection conn, int lavarropasNumero,
                               ConfiguracionCiclo config) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_CICLO, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, lavarropasNumero);
            ps.setInt(2, config.jabon().getId());
            ps.setBigDecimal(3, config.litrosJabon());
            ps.setBoolean(4, config.suavizante());
            ps.setBoolean(5, config.potenciador());
            if (config.litrosTotales() != null) ps.setBigDecimal(6, config.litrosTotales());
            else ps.setNull(6, Types.DECIMAL);
            ps.setString(7, config.tipoLavado().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void insertarMovimientos(Connection conn, int cicloId,
                                      List<ElementoCicloMovimiento> movimientos) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_ELEMENTO)) {
            for (ElementoCicloMovimiento m : movimientos) {
                ps.setInt(1, cicloId);
                ps.setInt(2, m.getElementoClasificacionId());
                ps.setInt(3, m.getCantidad());
                if (m.getInstanciaEquipoId() != null) ps.setInt(4, m.getInstanciaEquipoId());
                else ps.setNull(4, Types.INTEGER);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * {@code id de staging → id en la base}. Una sola instancia por id de staging, aunque sus
     * fracciones estén repartidas entre varios lavarropas de la tanda.
     */
    private Map<Integer, Integer> crearInstancias(Connection conn,
                                                   List<LanzamientoCiclo> tanda) throws SQLException {
        Map<Integer, Integer> instancias = new HashMap<>();
        for (LanzamientoCiclo ciclo : tanda) {
            for (LineaLanzamiento linea : ciclo.lineas()) {
                if (!linea.esFraccionDeEquipo()
                        || instancias.containsKey(linea.instanciaStagingId())) continue;
                instancias.put(linea.instanciaStagingId(),
                    insertarInstancia(conn, linea.elementoClasificacionId(), linea.totalPartes()));
            }
        }
        return instancias;
    }

    private int insertarInstancia(Connection conn, int elementoClasificacionId,
                                   int totalPartes) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_INSTANCIA, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, elementoClasificacionId);
            ps.setInt(2, totalPartes);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Cambia los ids de instancia de staging por los que quedaron en la base. */
    private static List<ElementoCicloMovimiento> movimientosDe(LanzamientoCiclo ciclo,
                                                               Map<Integer, Integer> instancias) {
        List<ElementoCicloMovimiento> movimientos = new ArrayList<>();
        for (LineaLanzamiento linea : ciclo.lineas()) {
            movimientos.add(new ElementoCicloMovimiento(
                linea.elementoClasificacionId(), linea.cantidad(),
                linea.esFraccionDeEquipo() ? instancias.get(linea.instanciaStagingId()) : null));
        }
        return movimientos;
    }

    private void marcarFinalizado(Connection conn, int cicloId) throws SQLException {
        String sql = "UPDATE ciclos_lavadero SET fecha_fin = NOW(), estado = 'FINALIZADO' WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cicloId);
            ps.executeUpdate();
        }
    }

    private Set<Integer> obtenerIngresosAfectados(Connection conn, int cicloId) throws SQLException {
        String sql = "SELECT DISTINCT ecl.ingreso_id " +
                     "FROM elementos_ciclo_lavadero eci " +
                     "JOIN elementos_clasificacion_lavadero ecl ON ecl.id = eci.elemento_clasificacion_id " +
                     "WHERE eci.ciclo_id = ?";
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /**
     * Cuánto clasificó un ingreso y cuánto de eso ya entró a algún ciclo.
     *
     * <p>Los dos términos se agregan en subconsultas independientes y no con un {@code JOIN}
     * entre las dos tablas: un {@code LEFT JOIN} repite la línea de clasificación una vez por
     * fila de ciclo, así que {@code SUM(ecl.cantidad)} contaba el total tantas veces como
     * ciclos hubiera y una línea lavada en dos tandas nunca alcanzaba su propio total. Mismo
     * patrón que {@code SalidaLavaderoDAO.SQL_TOTAL_Y_DERIVADO_DEL_INGRESO}.</p>
     *
     * <p>Lo procesado usa la fórmula de la decisión C del blueprint de fracciones de equipo (la
     * misma de {@link #SQL_DISPONIBLES}): las N fracciones de un equipo repartido consumen 1
     * unidad de su línea, no N. Sin ella, sacar el fan-out haría pasar a LAVADO un ingreso que
     * todavía tiene equipos sin lavar.</p>
     */
    private static final String SQL_TOTAL_Y_PROCESADO_DEL_INGRESO =
        "SELECT (SELECT COALESCE(SUM(ecl.cantidad), 0) " +
        "          FROM elementos_clasificacion_lavadero ecl " +
        "         WHERE ecl.ingreso_id = ?)                                        AS total, " +
        "       (SELECT COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL " +
        "                                 THEN eci.cantidad ELSE 0 END), 0) " +
        "             + COUNT(DISTINCT eci.instancia_equipo_id) " +
        "          FROM elementos_ciclo_lavadero eci " +
        "          JOIN elementos_clasificacion_lavadero ecl2 ON ecl2.id = eci.elemento_clasificacion_id " +
        "         WHERE ecl2.ingreso_id = ?)                                       AS procesado";

    private static final String SQL_MARCAR_LAVADO =
        "UPDATE ingresos_lavadero SET estado = '" + EstadoIngresoLavadero.LAVADO + "' WHERE id = ?";

    private void actualizarEstadoIngresosAfectados(Connection conn, Set<Integer> ingresoIds) throws SQLException {
        if (ingresoIds.isEmpty()) return;
        try (PreparedStatement psVerificar = conn.prepareStatement(SQL_TOTAL_Y_PROCESADO_DEL_INGRESO);
             PreparedStatement psMarcar   = conn.prepareStatement(SQL_MARCAR_LAVADO)) {
            for (int ingresoId : ingresoIds) {
                psVerificar.setInt(1, ingresoId);
                psVerificar.setInt(2, ingresoId);
                try (ResultSet rs = psVerificar.executeQuery()) {
                    if (rs.next() && rs.getInt("procesado") >= rs.getInt("total")) {
                        psMarcar.setInt(1, ingresoId);
                        psMarcar.executeUpdate();
                    }
                }
            }
        }
    }

    public List<ElementoCicloItem> obtenerElementosDeCiclo(int cicloId) {
        List<ElementoCicloItem> lista = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ELEMENTOS_DE_CICLO)) {
            ps.setInt(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ElementoCicloItem item = new ElementoCicloItem(
                        rs.getInt("id"),
                        rs.getInt("ingreso_id"),
                        rs.getString("nombre"),
                        rs.getInt("cantidad"),
                        0,
                        rs.getString("cliente"),
                        rs.getString("categoria")
                    );
                    item.setCantidadEnCiclo(rs.getInt("en_ciclo"));
                    lista.add(item);
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener elementos de ciclo {}", cicloId, e);
        }
        return lista;
    }

    private CicloLavadero mapearCiclo(ResultSet rs) throws SQLException {
        return new CicloLavadero(
            rs.getInt("id"),
            rs.getInt("lavarropas_numero"),
            TipoLavado.desdeBD(rs.getString("tipo_lavado")),
            new JabonCatalogo(rs.getInt("jabon_id"), rs.getString("jabon_nombre")),
            rs.getBigDecimal("litros_jabon"),
            rs.getBoolean("suavizante"),
            rs.getBoolean("potenciador"),
            rs.getBigDecimal("litros_totales"),
            rs.getObject("fecha_inicio", LocalDateTime.class),
            null
        );
    }

    private CicloLavadero mapearCicloCompleto(ResultSet rs) throws SQLException {
        LocalDateTime fechaFin = rs.getObject("fecha_fin", LocalDateTime.class);
        return new CicloLavadero(
            rs.getInt("id"),
            rs.getInt("lavarropas_numero"),
            TipoLavado.desdeBD(rs.getString("tipo_lavado")),
            new JabonCatalogo(rs.getInt("jabon_id"), rs.getString("jabon_nombre")),
            rs.getBigDecimal("litros_jabon"),
            rs.getBoolean("suavizante"),
            rs.getBoolean("potenciador"),
            rs.getBigDecimal("litros_totales"),
            rs.getObject("fecha_inicio", LocalDateTime.class),
            fechaFin
        );
    }
}
