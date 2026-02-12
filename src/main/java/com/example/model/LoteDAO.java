package com.example.model;

import com.example.database.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoteDAO {

    private static final Logger log = LoggerFactory.getLogger(LoteDAO.class);

    public Map<String, Lote> obtenerLotesActivosPorAutoclave() {
        Map<String, Lote> activos = new HashMap<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin " +
                     "FROM lotes WHERE fecha_fin IS NULL";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Lote lote = mapLoteConMateriales(rs);
                activos.put(lote.getAutoclaveNombre(), lote);
            }
        } catch (SQLException e) {
            log.error("Error al obtener lotes activos", e);
        }

        return activos;
    }

    public List<LoteMaterialInfo> obtenerMaterialesPorLote(int loteId) {
        List<LoteMaterialInfo> materiales = new ArrayList<>();
        String sql = "SELECT em.id, em.equipo_id, em.codigo_catalogo, em.descripcion_copia, " +
                     "em.cantidad, cd.volumen " +
                     "FROM equipo_materiales em " +
                     "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
                     "WHERE em.lote_id = ? ORDER BY em.id";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int volumen = rs.getInt("volumen");
                    materiales.add(new LoteMaterialInfo(
                        rs.getInt("id"),
                        rs.getInt("equipo_id"),
                        rs.getInt("codigo_catalogo"),
                        rs.getString("descripcion_copia"),
                        rs.getInt("cantidad"),
                        volumen
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener materiales del lote: {}", loteId, e);
        }

        return materiales;
    }

    public Lote lanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                           List<LoteMovimiento> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) {
            return null;
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            int anio = java.time.LocalDate.now().getYear();
            int secuencia = obtenerSiguienteSecuencia(conn, anio);
            String idNegocio = String.valueOf(anio) + secuencia;

            int loteId = insertarLote(conn, idNegocio, anio, secuencia, autoclaveNombre,
                                      capacidadTotal, capacidadUsada);

            Map<Integer, Boolean> equiposAfectados = new HashMap<>();

            for (LoteMovimiento mov : movimientos) {
                equiposAfectados.put(mov.getEquipoId(), true);
                aplicarMovimientoLote(conn, loteId, mov);
            }

            for (Integer equipoId : equiposAfectados.keySet()) {
                recalcularEstadoEquipo(conn, equipoId);
            }

            conn.commit();
            return obtenerLotePorId(conn, loteId);
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error al hacer rollback", ex); }
            }
            log.error("Error al lanzar lote para autoclave: {}", autoclaveNombre, e);
            return null;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
            }
        }
    }

    public boolean finalizarLote(int loteId) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            String sqlActualizarLote = "UPDATE lotes SET fecha_fin = CURRENT_TIMESTAMP " +
                                       "WHERE id = ? AND fecha_fin IS NULL";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlActualizarLote)) {
                pstmt.setInt(1, loteId);
                int filas = pstmt.executeUpdate();
                if (filas == 0) {
                    conn.rollback();
                    return false;
                }
            }

            String sqlSelect = "SELECT id, equipo_id, cantidad, estado FROM equipo_materiales " +
                               "WHERE lote_id = ? FOR UPDATE";
            String sqlUpdate = "UPDATE equipo_materiales SET estado = ? WHERE id = ?";
            String sqlMovimiento = "INSERT INTO material_movimientos " +
                                   "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                                   "VALUES (?, ?, ?, ?, ?)";

            Map<Integer, Boolean> equiposAfectados = new HashMap<>();

            try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
                pstmt.setInt(1, loteId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId = rs.getInt("id");
                        int equipoId = rs.getInt("equipo_id");
                        int cantidad = rs.getInt("cantidad");
                        String estadoActual = rs.getString("estado");

                        equiposAfectados.put(equipoId, true);

                        try (PreparedStatement update = conn.prepareStatement(sqlUpdate)) {
                            update.setString(1, EstadoEquipo.ESTERILIZADO.getNombre());
                            update.setInt(2, materialId);
                            update.executeUpdate();
                        }

                        try (PreparedStatement mov = conn.prepareStatement(sqlMovimiento)) {
                            mov.setInt(1, materialId);
                            mov.setInt(2, equipoId);
                            mov.setInt(3, cantidad);
                            mov.setString(4, estadoActual);
                            mov.setString(5, EstadoEquipo.ESTERILIZADO.getNombre());
                            mov.executeUpdate();
                        }
                    }
                }
            }

            for (Integer equipoId : equiposAfectados.keySet()) {
                recalcularEstadoEquipo(conn, equipoId);
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error al hacer rollback", ex); }
            }
            log.error("Error al finalizar lote: {}", loteId, e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
            }
        }
    }

    private int obtenerSiguienteSecuencia(Connection conn, int anio) throws SQLException {
        String sql = "SELECT COALESCE(MAX(secuencia), 0) AS max_seq FROM lotes WHERE anio = ? FOR UPDATE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, anio);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("max_seq") + 1;
                }
            }
        }
        return 1;
    }

    private int insertarLote(Connection conn, String idNegocio, int anio, int secuencia,
                            String autoclaveNombre, int capacidadTotal, int capacidadUsada) throws SQLException {
        String sql = "INSERT INTO lotes (id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio) " +
                     "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, idNegocio);
            pstmt.setInt(2, anio);
            pstmt.setInt(3, secuencia);
            pstmt.setString(4, autoclaveNombre);
            pstmt.setInt(5, capacidadTotal);
            pstmt.setInt(6, capacidadUsada);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se generó ID para el lote");
    }

    private void aplicarMovimientoLote(Connection conn, int loteId, LoteMovimiento movimiento) throws SQLException {
        String sqlSelectLote =
            "SELECT codigo_catalogo, descripcion_copia, cantidad, estado " +
            "FROM equipo_materiales WHERE id = ? AND equipo_id = ? FOR UPDATE";
        String sqlUpdateCantidad = "UPDATE equipo_materiales SET cantidad = ? WHERE id = ? AND equipo_id = ?";
        String sqlUpdateEstado = "UPDATE equipo_materiales SET estado = ?, lote_id = ? WHERE id = ? AND equipo_id = ?";
        String sqlInsertLote =
            "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, descripcion_copia, cantidad, estado, lote_id) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        String sqlMovimiento = "INSERT INTO material_movimientos " +
                               "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                               "VALUES (?, ?, ?, ?, ?)";

        int materialId = movimiento.getMaterialId();
        int equipoId = movimiento.getEquipoId();
        int cantidadMover = movimiento.getCantidad();

        int codigo;
        String descripcion;
        int cantidadActual;
        String estadoActual;

        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelectLote)) {
            pstmt.setInt(1, materialId);
            pstmt.setInt(2, equipoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No se encontro el lote a mover: " + materialId);
                }
                codigo = rs.getInt("codigo_catalogo");
                descripcion = rs.getString("descripcion_copia");
                cantidadActual = rs.getInt("cantidad");
                estadoActual = rs.getString("estado");
            }
        }

        if (cantidadMover <= 0 || cantidadMover > cantidadActual) {
            throw new SQLException("Cantidad invalida para mover en lote: " + materialId);
        }

        if (cantidadMover == cantidadActual) {
            try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdateEstado)) {
                pstmt.setString(1, EstadoEquipo.ESTERILIZANDO.getNombre());
                pstmt.setInt(2, loteId);
                pstmt.setInt(3, materialId);
                pstmt.setInt(4, equipoId);
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sqlMovimiento)) {
                pstmt.setInt(1, materialId);
                pstmt.setInt(2, equipoId);
                pstmt.setInt(3, cantidadMover);
                pstmt.setString(4, estadoActual);
                pstmt.setString(5, EstadoEquipo.ESTERILIZANDO.getNombre());
                pstmt.executeUpdate();
            }
        } else {
            int cantidadRestante = cantidadActual - cantidadMover;
            try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdateCantidad)) {
                pstmt.setInt(1, cantidadRestante);
                pstmt.setInt(2, materialId);
                pstmt.setInt(3, equipoId);
                pstmt.executeUpdate();
            }

            int nuevoMaterialId;
            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsertLote, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, equipoId);
                pstmt.setInt(2, codigo);
                pstmt.setString(3, descripcion);
                pstmt.setInt(4, cantidadMover);
                pstmt.setString(5, EstadoEquipo.ESTERILIZANDO.getNombre());
                pstmt.setInt(6, loteId);
                pstmt.executeUpdate();

                try (ResultSet rsNuevo = pstmt.getGeneratedKeys()) {
                    if (rsNuevo.next()) {
                        nuevoMaterialId = rsNuevo.getInt(1);
                    } else {
                        throw new SQLException("No se generó ID para el nuevo lote");
                    }
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sqlMovimiento)) {
                pstmt.setInt(1, nuevoMaterialId);
                pstmt.setInt(2, equipoId);
                pstmt.setInt(3, cantidadMover);
                pstmt.setString(4, estadoActual);
                pstmt.setString(5, EstadoEquipo.ESTERILIZANDO.getNombre());
                pstmt.executeUpdate();
            }
        }
    }

    private void recalcularEstadoEquipo(Connection conn, int equipoId) throws SQLException {
        String sqlCalcularEstado =
            "SELECT MIN(CASE " +
            "  WHEN estado = 'Nuevo' THEN 1 " +
            "  WHEN estado = 'Lavando' THEN 2 " +
            "  WHEN estado = 'Lavado' THEN 3 " +
            "  WHEN estado = 'Empaquetado' THEN 4 " +
            "  WHEN estado = 'Esterilizando' THEN 5 " +
            "  WHEN estado = 'Esterilizado' THEN 6 " +
            "  WHEN estado = 'Entregado' THEN 7 " +
            "  ELSE 1 END) as orden_minimo " +
            "FROM equipo_materiales WHERE equipo_id = ?";

        EstadoEquipo estadoEquipo = EstadoEquipo.NUEVO;
        try (PreparedStatement pstmt = conn.prepareStatement(sqlCalcularEstado)) {
            pstmt.setInt(1, equipoId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int ordenMinimo = rs.getInt("orden_minimo");
                for (EstadoEquipo estado : EstadoEquipo.values()) {
                    if (estado.getOrden() == ordenMinimo) {
                        estadoEquipo = estado;
                        break;
                    }
                }
            }
        }

        String sqlEquipo = "UPDATE equipos SET estado = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlEquipo)) {
            pstmt.setString(1, estadoEquipo.getNombre());
            pstmt.setInt(2, equipoId);
            pstmt.executeUpdate();
        }
    }

    private Lote mapLote(ResultSet rs) throws SQLException {
        Timestamp inicio = rs.getTimestamp("fecha_inicio");
        Timestamp fin = rs.getTimestamp("fecha_fin");
        LocalDateTime fechaInicio = inicio != null ? LocalDateTime.ofInstant(inicio.toInstant(), ZoneId.systemDefault()) : null;
        LocalDateTime fechaFin = fin != null ? LocalDateTime.ofInstant(fin.toInstant(), ZoneId.systemDefault()) : null;

        return new Lote(
            rs.getInt("id"),
            rs.getString("id_negocio"),
            rs.getInt("anio"),
            rs.getInt("secuencia"),
            rs.getString("autoclave_nombre"),
            rs.getInt("capacidad_total"),
            rs.getInt("capacidad_usada"),
            fechaInicio,
            fechaFin
        );
    }

    private Lote mapLoteConMateriales(ResultSet rs) throws SQLException {
        Lote lote = mapLote(rs);
        List<LoteMaterialInfo> materiales = obtenerMaterialesPorLote(lote.getId());
        return new Lote(
            lote.getId(),
            lote.getIdNegocio(),
            lote.getAnio(),
            lote.getSecuencia(),
            lote.getAutoclaveNombre(),
            lote.getCapacidadTotal(),
            lote.getCapacidadUsada(),
            lote.getFechaInicio(),
            lote.getFechaFin(),
            materiales
        );
    }

    private Lote obtenerLotePorId(Connection conn, int loteId) throws SQLException {
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin " +
                     "FROM lotes WHERE id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapLote(rs);
                }
            }
        }
        return null;
    }
}
