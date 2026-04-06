package com.example.features.lotes.dao;

import com.example.features.equipos.dao.EquipoMaterialHelper;
import com.example.features.equipos.model.EstadoEquipo;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LoteDAO {

    private static final Logger log = LoggerFactory.getLogger(LoteDAO.class);

    // ── Consultas ────────────────────────────────────────────────────────────

    public Map<String, Lote> obtenerLotesActivosPorAutoclave() {
        Map<String, Lote> activos = new HashMap<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
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

    public List<Lote> obtenerLotesFinalizados() {
        List<Lote> finalizados = new ArrayList<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes WHERE fecha_fin IS NOT NULL ORDER BY fecha_fin DESC, id DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) finalizados.add(mapLote(rs));
        } catch (SQLException e) {
            log.error("Error al obtener lotes finalizados", e);
        }
        return finalizados;
    }

    public List<Lote> obtenerTodosLosLotes() {
        List<Lote> todos = new ArrayList<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes ORDER BY fecha_inicio DESC, id DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) todos.add(mapLote(rs));
        } catch (SQLException e) {
            log.error("Error al obtener todos los lotes", e);
        }
        return todos;
    }

    /**
     * Devuelve los lotes cuya {@code fecha_inicio} cae en [desde, hasta] (inclusive).
     * No carga materiales; usá {@link #obtenerMaterialesPorLote(int)} para eso.
     */
    public List<Lote> obtenerLotesEnRango(LocalDate desde, LocalDate hasta) {
        List<Lote> lotes = new ArrayList<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes " +
                     "WHERE DATE(fecha_inicio) BETWEEN ? AND ? AND estado = 'EXITOSO' " +
                     "ORDER BY fecha_inicio ASC, id ASC";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(desde));
            pstmt.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) lotes.add(mapLote(rs));
            }
        } catch (SQLException e) {
            log.error("Error al obtener lotes en rango [{} - {}]", desde, hasta, e);
        }
        return lotes;
    }

    public List<String> obtenerClientesPorLote(int loteId) {
        List<String> clientes = new ArrayList<>();
        String sql = "SELECT DISTINCT c.nombre " +
                     "FROM clientes c " +
                     "  JOIN equipos e           ON e.nro_cliente = c.id " +
                     "  JOIN equipo_materiales em ON em.equipo_id  = e.id " +
                     "WHERE em.lote_id = ? ORDER BY c.nombre";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) clientes.add(rs.getString("nombre"));
            }
        } catch (SQLException e) {
            log.error("Error al obtener clientes del lote {}", loteId, e);
        }
        return clientes;
    }

    public Map<String, List<String>> obtenerMaterialesPorClientePorLote(int loteId) {
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        String sql =
            "SELECT c.nombre AS cliente, cd.descripcion, em.cantidad " +
            "FROM equipo_materiales em " +
            "  JOIN equipos e                 ON em.equipo_id      = e.id " +
            "  JOIN clientes c                ON e.nro_cliente     = c.id " +
            "  JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
            "WHERE em.lote_id = ? ORDER BY c.nombre, cd.descripcion";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String cliente  = rs.getString("cliente");
                    String material = rs.getString("descripcion") + " x" + rs.getInt("cantidad");
                    resultado.computeIfAbsent(cliente, k -> new ArrayList<>()).add(material);
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener materiales por cliente del lote {}", loteId, e);
        }
        return resultado;
    }

    public List<LoteMaterialInfo> obtenerMaterialesPorLote(int loteId) {
        List<LoteMaterialInfo> materiales = new ArrayList<>();
        String sql = "SELECT em.id, em.equipo_id, em.codigo_catalogo, cd.descripcion, " +
                     "em.cantidad, cd.volumen " +
                     "FROM equipo_materiales em " +
                     "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
                     "WHERE em.lote_id = ? ORDER BY em.id";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    materiales.add(new LoteMaterialInfo(
                        rs.getInt("id"),
                        rs.getInt("equipo_id"),
                        rs.getInt("codigo_catalogo"),
                        rs.getString("descripcion"),
                        rs.getInt("cantidad"),
                        rs.getInt("volumen")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener materiales del lote {}", loteId, e);
        }
        return materiales;
    }

    // ── Mutaciones ───────────────────────────────────────────────────────────

    public boolean finalizarLote(int loteId) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
 
            if (!actualizarEstadoLoteAbierto(conn, loteId, "EXITOSO")) {
                return false;  // lote ya cerrado: tx.close() hace rollback automático
            }
 
            Map<Integer, Boolean> equiposAfectados = new HashMap<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id, equipo_id, cantidad, estado FROM equipo_materiales WHERE lote_id = ? FOR UPDATE")) {
                pstmt.setInt(1, loteId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId = rs.getInt("id");
                        int equipoId = rs.getInt("equipo_id");
                        int cantidad = rs.getInt("cantidad");
                        String estadoActual = rs.getString("estado");

                        equiposAfectados.put(equipoId, true);
                        actualizarEstadoMaterial(conn, materialId, EstadoEquipo.ESTERILIZADO.getNombre());
                        registrarMovimiento(conn, materialId, equipoId, cantidad, estadoActual,
                            EstadoEquipo.ESTERILIZADO.getNombre());
                    }
                }
            }
 
            procesarEquiposAfectados(conn, equiposAfectados.keySet());
 
            tx.commit();
            return true;
        } catch (SQLException e) {
            log.error("Error al finalizar lote: {}", loteId, e);
            return false;
        }
    }

    public boolean marcarLoteFallo(int loteId) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
 
            if (!actualizarEstadoLoteAbierto(conn, loteId, "FALLIDO")) {
                return false;  // lote ya cerrado: rollback automático
            }
 
            Map<Integer, Boolean> equiposAfectados = new HashMap<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id, equipo_id, cantidad FROM equipo_materiales " +
                    "WHERE lote_id = ? AND estado = ? FOR UPDATE")) {
                pstmt.setInt(1, loteId);
                pstmt.setString(2, EstadoEquipo.ESTERILIZANDO.getNombre());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId = rs.getInt("id");
                        int equipoId = rs.getInt("equipo_id");
                        int cantidad = rs.getInt("cantidad");

                        equiposAfectados.put(equipoId, true);

                        String estadoAnterior = obtenerEstadoAnteriorDesdeMovimiento(conn, materialId,
                            EstadoEquipo.ESTERILIZANDO.getNombre(), EstadoEquipo.EMPAQUETADO.getNombre());

                        actualizarEstadoMaterial(conn, materialId, estadoAnterior);
                        registrarMovimiento(conn, materialId, equipoId, cantidad,
                            EstadoEquipo.ESTERILIZANDO.getNombre(), estadoAnterior);
                    }
                }
            }
 
            procesarEquiposAfectados(conn, equiposAfectados.keySet());
 
            tx.commit();
            return true;
        } catch (SQLException e) {
            log.error("Error al marcar lote como fallido: {}", loteId, e);
            return false;
        }
    }

    public Lote lanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                           List<LoteMovimiento> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) return null;
 
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
 
            int anio      = java.time.LocalDate.now().getYear();
            int secuencia = obtenerSiguienteSecuencia(conn, anio);
            String idNegocio = construirIdNegocio(anio, secuencia);
 
            int loteId = insertarLote(conn, idNegocio, anio, secuencia, autoclaveNombre,
                                      capacidadTotal, capacidadUsada);
 
            Map<Integer, Boolean> equiposAfectados = new HashMap<>();
            for (LoteMovimiento mov : movimientos) {
                equiposAfectados.put(mov.getEquipoId(), true);
                aplicarMovimientoLote(conn, loteId, mov);
            }
 
            procesarEquiposSoloRecalculo(conn, equiposAfectados.keySet());
 
            tx.commit();
            return obtenerLotePorId(conn, loteId);
        } catch (SQLException e) {
            log.error("Error al lanzar lote para autoclave: {}", autoclaveNombre, e);
            return null;
        }
    }

    // ── Helpers privados de LoteDAO ──────────────────────────────────────────

    private int obtenerSiguienteSecuencia(Connection conn, int anio) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COALESCE(MAX(secuencia), 0) AS max_seq FROM lotes WHERE anio = ? FOR UPDATE")) {
            pstmt.setInt(1, anio);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("max_seq") + 1;
            }
        }
        return 1;
    }

    private String construirIdNegocio(int anio, int secuencia) {
        return String.valueOf(anio) + secuencia;
    }

    private boolean actualizarEstadoLoteAbierto(Connection conn, int loteId, String estado) throws SQLException {
        String sql = "UPDATE lotes SET fecha_fin = CURRENT_TIMESTAMP, estado = ? " +
                     "WHERE id = ? AND fecha_fin IS NULL";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, estado);
            pstmt.setInt(2, loteId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private void actualizarEstadoMaterial(Connection conn, int materialId, String nuevoEstado) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE equipo_materiales SET estado = ? WHERE id = ?")) {
            update.setString(1, nuevoEstado);
            update.setInt(2, materialId);
            update.executeUpdate();
        }
    }

    private void actualizarEstadoMaterial(Connection conn, int materialId, String nuevoEstado, Integer loteId)
            throws SQLException {
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE equipo_materiales SET estado = ?, lote_id = ? WHERE id = ?")) {
            update.setString(1, nuevoEstado);
            if (loteId != null) {
                update.setInt(2, loteId);
            } else {
                update.setNull(2, Types.INTEGER);
            }
            update.setInt(3, materialId);
            update.executeUpdate();
        }
    }

    private void actualizarCantidadMaterial(Connection conn, int materialId, int cantidadNueva) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE equipo_materiales SET cantidad = ? WHERE id = ?")) {
            pstmt.setInt(1, cantidadNueva);
            pstmt.setInt(2, materialId);
            pstmt.executeUpdate();
        }
    }

    private void registrarMovimiento(Connection conn, int materialId, int equipoId, int cantidad,
                                     String estadoOrigen, String estadoDestino) throws SQLException {
        try (PreparedStatement mov = conn.prepareStatement(
                "INSERT INTO material_movimientos " +
                "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            mov.setInt(1, materialId);
            mov.setInt(2, equipoId);
            mov.setInt(3, cantidad);
            mov.setString(4, estadoOrigen);
            mov.setString(5, estadoDestino);
            mov.executeUpdate();
        }
    }

    private String obtenerEstadoAnteriorDesdeMovimiento(Connection conn, int materialId,
                                                        String estadoDestino, String valorPorDefecto)
            throws SQLException {
        String sql = "SELECT estado_origen FROM material_movimientos " +
                     "WHERE material_id = ? AND estado_destino = ? " +
                     "ORDER BY fecha DESC LIMIT 1";
        try (PreparedStatement pstmtPrev = conn.prepareStatement(sql)) {
            pstmtPrev.setInt(1, materialId);
            pstmtPrev.setString(2, estadoDestino);
            try (ResultSet rsPrev = pstmtPrev.executeQuery()) {
                if (rsPrev.next()) {
                    String estadoAnterior = rsPrev.getString("estado_origen");
                    return estadoAnterior != null ? estadoAnterior : valorPorDefecto;
                }
            }
        }
        return valorPorDefecto;
    }

    private void procesarEquiposAfectados(Connection conn, Iterable<Integer> equiposAfectados) throws SQLException {
        for (Integer equipoId : equiposAfectados) {
            recalcularEstadoEquipo(conn, equipoId);
            unificarMaterialesDuplicados(conn, equipoId);
        }
    }

    private void procesarEquiposSoloRecalculo(Connection conn, Iterable<Integer> equiposAfectados) throws SQLException {
        for (Integer equipoId : equiposAfectados) {
            recalcularEstadoEquipo(conn, equipoId);
        }
    }

    private void recalcularEstadoEquipo(Connection conn, int equipoId) throws SQLException {
        EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
    }

    private void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        EquipoMaterialHelper.unificarMaterialesDuplicados(conn, equipoId);
    }

    private void aplicarMovimientoLote(Connection conn, int loteId,
                                       LoteMovimiento movimiento) throws SQLException {
        String sqlSelect =
            "SELECT codigo_catalogo, cantidad, estado " +
            "FROM equipo_materiales WHERE id = ? AND equipo_id = ? FOR UPDATE";
        String sqlUpdateCantidad =
            "UPDATE equipo_materiales SET cantidad = ? WHERE id = ? AND equipo_id = ?";
        String sqlUpdateEstado =
            "UPDATE equipo_materiales SET estado = ?, lote_id = ? WHERE id = ? AND equipo_id = ?";
        String sqlInsert =
            "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado, lote_id) " +
            "VALUES (?, ?, ?, ?, ?)";
        String sqlMovimiento =
            "INSERT INTO material_movimientos " +
            "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
            "VALUES (?, ?, ?, ?, ?)";

        int materialId = movimiento.getMaterialId();
        int equipoId = movimiento.getEquipoId();
        int cantidadMover = movimiento.getCantidad();

        int codigo;
        int cantidadActual;
        String estadoActual;

        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
            pstmt.setInt(1, materialId);
            pstmt.setInt(2, equipoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("No se encontró el lote a mover: " + materialId);
                codigo = rs.getInt("codigo_catalogo");
                cantidadActual = rs.getInt("cantidad");
                estadoActual = rs.getString("estado");
            }
        }

        if (cantidadMover <= 0 || cantidadMover > cantidadActual) {
            throw new SQLException("Cantidad inválida para mover en lote: " + materialId);
        }

        if (cantidadMover == cantidadActual) {
            actualizarEstadoMaterial(conn, materialId, EstadoEquipo.ESTERILIZANDO.getNombre(), loteId);
            registrarMovimiento(conn, materialId, equipoId, cantidadMover, estadoActual,
                EstadoEquipo.ESTERILIZANDO.getNombre());
        } else {
            int cantidadRestante = cantidadActual - cantidadMover;
            actualizarCantidadMaterial(conn, materialId, cantidadRestante);

            int nuevoMaterialId;
            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, equipoId);
                pstmt.setInt(2, codigo);
                pstmt.setInt(3, cantidadMover);
                pstmt.setString(4, EstadoEquipo.ESTERILIZANDO.getNombre());
                pstmt.setInt(5, loteId);
                pstmt.executeUpdate();
                try (ResultSet rsNuevo = pstmt.getGeneratedKeys()) {
                    if (!rsNuevo.next()) throw new SQLException("No se generó ID para el nuevo lote");
                    nuevoMaterialId = rsNuevo.getInt(1);
                }
            }

            registrarMovimiento(conn, nuevoMaterialId, equipoId, cantidadMover, estadoActual,
                EstadoEquipo.ESTERILIZANDO.getNombre());
        }
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
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se generó ID para el lote");
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private Lote mapLote(ResultSet rs) throws SQLException {
        Timestamp inicio = rs.getTimestamp("fecha_inicio");
        Timestamp fin    = rs.getTimestamp("fecha_fin");
        LocalDateTime fechaInicio = inicio != null
            ? LocalDateTime.ofInstant(inicio.toInstant(), ZoneId.systemDefault()) : null;
        LocalDateTime fechaFin = fin != null
            ? LocalDateTime.ofInstant(fin.toInstant(), ZoneId.systemDefault()) : null;
        String estado = rs.getString("estado");
        if (estado == null) estado = "ACTIVO";

        return new Lote(
            rs.getInt("id"),
            rs.getString("id_negocio"),
            rs.getInt("anio"),
            rs.getInt("secuencia"),
            rs.getString("autoclave_nombre"),
            rs.getInt("capacidad_total"),
            rs.getInt("capacidad_usada"),
            fechaInicio,
            fechaFin,
            estado,
            new ArrayList<>()
        );
    }

    private Lote mapLoteConMateriales(ResultSet rs) throws SQLException {
        Lote lote = mapLote(rs);
        List<LoteMaterialInfo> materiales = obtenerMaterialesPorLote(lote.getId());
        return new Lote(
            lote.getId(), lote.getIdNegocio(), lote.getAnio(), lote.getSecuencia(),
            lote.getAutoclaveNombre(), lote.getCapacidadTotal(), lote.getCapacidadUsada(),
            lote.getFechaInicio(), lote.getFechaFin(), lote.getEstado(), materiales
        );
    }

    private Lote obtenerLotePorId(Connection conn, int loteId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                "FROM lotes WHERE id = ?")) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapLote(rs);
            }
        }
        return null;
    }

    // ── Infra ────────────────────────────────────────────────────────────────

    private void rollback(Connection conn) {
        if (conn != null) {
            try { conn.rollback(); } catch (SQLException ex) { log.error("Error en rollback", ex); }
        }
    }

    private void cerrar(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
        }
    }
}