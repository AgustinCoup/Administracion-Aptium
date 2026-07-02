package com.example.features.lavadero.dao;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class CicloLavaderoDAO {

    private static final Logger log = LoggerFactory.getLogger(CicloLavaderoDAO.class);

    private static final String SQL_INSERTAR_CICLO =
        "INSERT INTO ciclos_lavadero (lavarropas_numero, jabon_id, litros_jabon, suavizante, potenciador, litros_totales, fecha_inicio, estado) " +
        "VALUES (?, ?, ?, ?, ?, ?, NOW(), 'ACTIVO')";

    private static final String SQL_INSERTAR_ELEMENTO =
        "INSERT INTO elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad) VALUES (?, ?, ?)";

    private static final String SQL_ACTIVOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, cl.fecha_inicio " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "WHERE cl.fecha_fin IS NULL";

    private static final String SQL_FINALIZADOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.jabon_id, cj.nombre AS jabon_nombre, " +
        "       cl.litros_jabon, cl.suavizante, cl.potenciador, cl.litros_totales, " +
        "       cl.fecha_inicio, cl.fecha_fin " +
        "FROM ciclos_lavadero cl " +
        "JOIN catalogo_jabones cj ON cj.id = cl.jabon_id " +
        "WHERE cl.fecha_fin IS NOT NULL ORDER BY cl.fecha_fin DESC";

    private static final String SQL_TODOS =
        "SELECT cl.id, cl.lavarropas_numero, cl.jabon_id, cj.nombre AS jabon_nombre, " +
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
        "       COALESCE(SUM(eci.cantidad), 0) AS ya_procesada, " +
        "       c.nombre AS cliente, cel.categoria " +
        "FROM elementos_clasificacion_lavadero ecl " +
        "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id " +
        "JOIN ingresos_lavadero il            ON il.id  = ecl.ingreso_id " +
        "JOIN clientes c                      ON c.id   = il.cliente_id " +
        "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
        "WHERE il.estado = 'CLASIFICADO' " +
        "GROUP BY ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, c.nombre, cel.categoria " +
        "HAVING ya_procesada < ecl.cantidad " +
        "ORDER BY il.id, cel.nombre";

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
                lista.add(new CicloLavadero(
                    rs.getInt("id"),
                    rs.getInt("lavarropas_numero"),
                    new JabonCatalogo(rs.getInt("jabon_id"), rs.getString("jabon_nombre")),
                    rs.getBigDecimal("litros_jabon"),
                    rs.getBoolean("suavizante"),
                    rs.getBoolean("potenciador"),
                    rs.getBigDecimal("litros_totales"),
                    rs.getObject("fecha_inicio", LocalDateTime.class),
                    rs.getObject("fecha_fin",    LocalDateTime.class),
                    "FINALIZADO"
                ));
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
            log.error("Error al obtener todos los ciclos", e);
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

    public void lanzarCiclo(int lavarropasNumero, JabonCatalogo jabon, BigDecimal litrosJabon,
                             boolean suavizante, boolean potenciador, BigDecimal litrosTotales,
                             List<ElementoCicloMovimiento> movimientos) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            int cicloId = insertarCiclo(conn, lavarropasNumero, jabon, litrosJabon, suavizante, potenciador, litrosTotales);
            insertarMovimientos(conn, cicloId, movimientos);
            tx.commit();
        } catch (SQLException e) {
            log.error("Error al lanzar ciclo en lavarropas {}", lavarropasNumero, e);
            throw new RuntimeException("Error al lanzar ciclo", e);
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

    private int insertarCiclo(Connection conn, int lavarropasNumero, JabonCatalogo jabon,
                               BigDecimal litrosJabon, boolean suavizante, boolean potenciador,
                               BigDecimal litrosTotales) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_CICLO, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, lavarropasNumero);
            ps.setInt(2, jabon.getId());
            ps.setBigDecimal(3, litrosJabon);
            ps.setBoolean(4, suavizante);
            ps.setBoolean(5, potenciador);
            if (litrosTotales != null) ps.setBigDecimal(6, litrosTotales);
            else ps.setNull(6, Types.DECIMAL);
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
                ps.addBatch();
            }
            ps.executeBatch();
        }
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

    private void actualizarEstadoIngresosAfectados(Connection conn, Set<Integer> ingresoIds) throws SQLException {
        if (ingresoIds.isEmpty()) return;
        String sqlVerificar =
            "SELECT ecl.ingreso_id, " +
            "       SUM(ecl.cantidad) AS total, " +
            "       COALESCE(SUM(eci.cantidad), 0) AS procesado " +
            "FROM elementos_clasificacion_lavadero ecl " +
            "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
            "WHERE ecl.ingreso_id = ? " +
            "GROUP BY ecl.ingreso_id " +
            "HAVING procesado >= total";
        String sqlMarcar = "UPDATE ingresos_lavadero SET estado = 'LAVADO' WHERE id = ?";
        try (PreparedStatement psVerificar = conn.prepareStatement(sqlVerificar);
             PreparedStatement psMarcar   = conn.prepareStatement(sqlMarcar)) {
            for (int ingresoId : ingresoIds) {
                psVerificar.setInt(1, ingresoId);
                try (ResultSet rs = psVerificar.executeQuery()) {
                    if (rs.next()) {
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
            new JabonCatalogo(rs.getInt("jabon_id"), rs.getString("jabon_nombre")),
            rs.getBigDecimal("litros_jabon"),
            rs.getBoolean("suavizante"),
            rs.getBoolean("potenciador"),
            rs.getBigDecimal("litros_totales"),
            rs.getObject("fecha_inicio", LocalDateTime.class),
            null,
            "ACTIVO"
        );
    }

    private CicloLavadero mapearCicloCompleto(ResultSet rs) throws SQLException {
        LocalDateTime fechaFin = rs.getObject("fecha_fin", LocalDateTime.class);
        return new CicloLavadero(
            rs.getInt("id"),
            rs.getInt("lavarropas_numero"),
            new JabonCatalogo(rs.getInt("jabon_id"), rs.getString("jabon_nombre")),
            rs.getBigDecimal("litros_jabon"),
            rs.getBoolean("suavizante"),
            rs.getBoolean("potenciador"),
            rs.getBigDecimal("litros_totales"),
            rs.getObject("fecha_inicio", LocalDateTime.class),
            fechaFin,
            fechaFin == null ? "ACTIVO" : "FINALIZADO"
        );
    }
}
