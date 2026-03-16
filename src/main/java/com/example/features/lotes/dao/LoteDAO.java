package com.example.features.lotes.dao;

import com.example.features.equipos.model.EstadoEquipo;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
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

            while (rs.next()) {
                finalizados.add(mapLote(rs));
            }
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

            while (rs.next()) {
                todos.add(mapLote(rs));
            }
        } catch (SQLException e) {
            log.error("Error al obtener todos los lotes", e);
        }

        return todos;
    }

    /**
     * Devuelve todos los lotes cuya {@code fecha_inicio} cae dentro del rango
     * [desde, hasta] inclusive (comparación a nivel de fecha, sin hora).
     * Los lotes se devuelven ordenados cronológicamente.
     * No carga materiales; usá {@link #obtenerMaterialesPorLote(int)} para eso.
     */
    public List<Lote> obtenerLotesEnRango(LocalDate desde, LocalDate hasta) {
        List<Lote> lotes = new ArrayList<>();
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes " +
                     "WHERE DATE(fecha_inicio) BETWEEN ? AND ? " +
                     "  AND estado = 'EXITOSO' " +
                     "ORDER BY fecha_inicio ASC, id ASC";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(desde));
            pstmt.setDate(2, Date.valueOf(hasta));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    lotes.add(mapLote(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener lotes en rango [{} - {}]", desde, hasta, e);
        }
        return lotes;
    }

    /**
     * Devuelve los nombres de los clientes (sin repetir) cuyos equipos tienen
     * materiales asignados al lote dado.
     *
     * Relación usada: lotes ← equipo_materiales → equipos → clientes
     */
    public List<String> obtenerClientesPorLote(int loteId) {
        List<String> clientes = new ArrayList<>();
        String sql = "SELECT DISTINCT c.nombre " +
                     "FROM clientes c " +
                     "  JOIN equipos e           ON e.nro_cliente = c.id " +
                     "  JOIN equipo_materiales em ON em.equipo_id  = e.id " +
                     "WHERE em.lote_id = ? " +
                     "ORDER BY c.nombre";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    clientes.add(rs.getString("nombre"));
                }
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
            "WHERE em.lote_id = ? " +
            "ORDER BY c.nombre, cd.descripcion";

        try (Connection conn = ConnectionPool.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String cliente  = rs.getString("cliente");
                    String material = rs.getString("descripcion") +
                                    " x" + rs.getInt("cantidad");
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

    public boolean finalizarLote(int loteId) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            String sqlActualizarLote = "UPDATE lotes SET fecha_fin = CURRENT_TIMESTAMP, estado = 'EXITOSO' " +
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
                        int equipoId   = rs.getInt("equipo_id");
                        int cantidad   = rs.getInt("cantidad");
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

            // Recalcular estado y unificar lotes del mismo código que ahora comparten estado
            for (Integer equipoId : equiposAfectados.keySet()) {
                recalcularEstadoEquipo(conn, equipoId);
                unificarMaterialesDuplicados(conn, equipoId);
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

    public boolean marcarLoteFallo(int loteId) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            String sqlActualizarLote = "UPDATE lotes SET fecha_fin = CURRENT_TIMESTAMP, estado = 'FALLIDO' " +
                                       "WHERE id = ? AND fecha_fin IS NULL";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlActualizarLote)) {
                pstmt.setInt(1, loteId);
                int filas = pstmt.executeUpdate();
                if (filas == 0) {
                    conn.rollback();
                    return false;
                }
            }

            String sqlSelect = "SELECT id, equipo_id, cantidad FROM equipo_materiales " +
                               "WHERE lote_id = ? AND estado = ? FOR UPDATE";
            String sqlGetPreviousState = "SELECT estado_origen FROM material_movimientos " +
                                        "WHERE material_id = ? AND estado_destino = ? " +
                                        "ORDER BY fecha DESC LIMIT 1";
            String sqlUpdate = "UPDATE equipo_materiales SET estado = ?, lote_id = NULL WHERE id = ?";
            String sqlMovimiento = "INSERT INTO material_movimientos " +
                                   "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                                   "VALUES (?, ?, ?, ?, ?)";

            Map<Integer, Boolean> equiposAfectados = new HashMap<>();

            try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
                pstmt.setInt(1, loteId);
                pstmt.setString(2, EstadoEquipo.ESTERILIZANDO.getNombre());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int materialId = rs.getInt("id");
                        int equipoId   = rs.getInt("equipo_id");
                        int cantidad   = rs.getInt("cantidad");

                        equiposAfectados.put(equipoId, true);

                        String estadoAnterior = EstadoEquipo.EMPAQUETADO.getNombre();
                        try (PreparedStatement pstmtPrev = conn.prepareStatement(sqlGetPreviousState)) {
                            pstmtPrev.setInt(1, materialId);
                            pstmtPrev.setString(2, EstadoEquipo.ESTERILIZANDO.getNombre());
                            try (ResultSet rsPrev = pstmtPrev.executeQuery()) {
                                if (rsPrev.next()) {
                                    estadoAnterior = rsPrev.getString("estado_origen");
                                }
                            }
                        }

                        try (PreparedStatement update = conn.prepareStatement(sqlUpdate)) {
                            update.setString(1, estadoAnterior);
                            update.setInt(2, materialId);
                            update.executeUpdate();
                        }

                        try (PreparedStatement mov = conn.prepareStatement(sqlMovimiento)) {
                            mov.setInt(1, materialId);
                            mov.setInt(2, equipoId);
                            mov.setInt(3, cantidad);
                            mov.setString(4, EstadoEquipo.ESTERILIZANDO.getNombre());
                            mov.setString(5, estadoAnterior);
                            mov.executeUpdate();
                        }
                    }
                }
            }

            // Recalcular estado y unificar: al regresar al estado anterior, podría
            // haber otra fila con el mismo código ya en ese estado (ej: otro fragmento
            // que no entró al lote fallido).
            for (Integer equipoId : equiposAfectados.keySet()) {
                recalcularEstadoEquipo(conn, equipoId);
                unificarMaterialesDuplicados(conn, equipoId);
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error al hacer rollback", ex); }
            }
            log.error("Error al marcar lote como fallido: {}", loteId, e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
            }
        }
    }

    public Lote lanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                           List<LoteMovimiento> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) return null;

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            int anio      = java.time.LocalDate.now().getYear();
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

    // ── Helpers privados ─────────────────────────────────────────────────────

    private int obtenerSiguienteSecuencia(Connection conn, int anio) throws SQLException {
        String sql = "SELECT COALESCE(MAX(secuencia), 0) AS max_seq FROM lotes WHERE anio = ? FOR UPDATE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, anio);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("max_seq") + 1;
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
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se generó ID para el lote");
    }

    private void aplicarMovimientoLote(Connection conn, int loteId, LoteMovimiento movimiento) throws SQLException {
        String sqlSelectLote =
            "SELECT codigo_catalogo, cantidad, estado " +
            "FROM equipo_materiales WHERE id = ? AND equipo_id = ? FOR UPDATE";
        String sqlUpdateCantidad = "UPDATE equipo_materiales SET cantidad = ? WHERE id = ? AND equipo_id = ?";
        String sqlUpdateEstado   = "UPDATE equipo_materiales SET estado = ?, lote_id = ? WHERE id = ? AND equipo_id = ?";
        String sqlInsertLote =
            "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado, lote_id) " +
            "VALUES (?, ?, ?, ?, ?)";
        String sqlMovimiento = "INSERT INTO material_movimientos " +
                               "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                               "VALUES (?, ?, ?, ?, ?)";

        int materialId    = movimiento.getMaterialId();
        int equipoId      = movimiento.getEquipoId();
        int cantidadMover = movimiento.getCantidad();

        int codigo;
        int cantidadActual;
        String estadoActual;

        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelectLote)) {
            pstmt.setInt(1, materialId);
            pstmt.setInt(2, equipoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("No se encontro el lote a mover: " + materialId);
                codigo         = rs.getInt("codigo_catalogo");
                cantidadActual = rs.getInt("cantidad");
                estadoActual   = rs.getString("estado");
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
                pstmt.setInt(3, cantidadMover);
                pstmt.setString(4, EstadoEquipo.ESTERILIZANDO.getNombre());
                pstmt.setInt(5, loteId);
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

    /**
     * Unifica filas de equipo_materiales que tienen el mismo equipo_id, codigo_catalogo y estado.
     * Esto ocurre cuando fragmentos de un mismo material se dividen y luego convergen al mismo estado
     * a través de distintos lotes de esterilización.
     *
     * La fila superviviente es la que tiene el movimiento más reciente.
     * Los movimientos de las filas eliminadas se reasignan al superviviente para preservar historial.
     *
     * Debe llamarse dentro de una transacción activa.
     */
    private void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        String sqlGrupos =
            "SELECT codigo_catalogo, estado, SUM(cantidad) AS cantidad_total " +
            "FROM equipo_materiales " +
            "WHERE equipo_id = ? " +
            "GROUP BY codigo_catalogo, estado " +
            "HAVING COUNT(*) > 1";

        List<int[]>    grupos       = new ArrayList<>();
        List<String>   estadosGrupos = new ArrayList<>();

        try (PreparedStatement pstmt = conn.prepareStatement(sqlGrupos)) {
            pstmt.setInt(1, equipoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    grupos.add(new int[]{ rs.getInt("codigo_catalogo"), rs.getInt("cantidad_total") });
                    estadosGrupos.add(rs.getString("estado"));
                }
            }
        }

        for (int i = 0; i < grupos.size(); i++) {
            int    codigo        = grupos.get(i)[0];
            int    cantidadTotal = grupos.get(i)[1];
            String estado        = estadosGrupos.get(i);
            unificarGrupo(conn, equipoId, codigo, estado, cantidadTotal);
        }
    }

    /**
     * Unifica todas las filas de un grupo (mismo equipo, código y estado) en una sola.
     *
     * Criterio de superviviente: la fila con el movimiento más reciente.
     * En caso de empate o sin movimientos, se usa el mayor id de equipo_materiales.
     *
     * Los material_movimientos de las filas eliminadas se reasignan al superviviente,
     * preservando el historial completo de trazabilidad.
     */
    private void unificarGrupo(Connection conn, int equipoId, int codigo,
                                String estado, int cantidadTotal) throws SQLException {
        String sqlSuperviviente =
            "SELECT em.id " +
            "FROM equipo_materiales em " +
            "LEFT JOIN (" +
            "  SELECT material_id, MAX(fecha) AS ultima_fecha " +
            "  FROM material_movimientos GROUP BY material_id" +
            ") mm ON em.id = mm.material_id " +
            "WHERE em.equipo_id = ? AND em.codigo_catalogo = ? AND em.estado = ? " +
            "ORDER BY mm.ultima_fecha DESC, em.id DESC " +
            "LIMIT 1";

        int supervivienteId;
        try (PreparedStatement pstmt = conn.prepareStatement(sqlSuperviviente)) {
            pstmt.setInt(1, equipoId);
            pstmt.setInt(2, codigo);
            pstmt.setString(3, estado);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) return;
                supervivienteId = rs.getInt("id");
            }
        }

        // Acumular cantidad total en el superviviente
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE equipo_materiales SET cantidad = ? WHERE id = ?")) {
            pstmt.setInt(1, cantidadTotal);
            pstmt.setInt(2, supervivienteId);
            pstmt.executeUpdate();
        }

        // Obtener IDs de las filas a eliminar (todo el grupo excepto el superviviente)
        List<Integer> idsAEliminar = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT id FROM equipo_materiales " +
                "WHERE equipo_id = ? AND codigo_catalogo = ? AND estado = ? AND id <> ?")) {
            pstmt.setInt(1, equipoId);
            pstmt.setInt(2, codigo);
            pstmt.setString(3, estado);
            pstmt.setInt(4, supervivienteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) idsAEliminar.add(rs.getInt("id"));
            }
        }

        if (idsAEliminar.isEmpty()) return;

        // Reasignar movimientos al superviviente (preservar historial)
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE material_movimientos SET material_id = ? WHERE material_id = ?")) {
            for (int idEliminar : idsAEliminar) {
                pstmt.setInt(1, supervivienteId);
                pstmt.setInt(2, idEliminar);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Eliminar las filas duplicadas
        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM equipo_materiales WHERE id = ?")) {
            for (int idEliminar : idsAEliminar) {
                pstmt.setInt(1, idEliminar);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        log.debug("Unificados {} lotes del material código={} estado={} en equipo={} → id superviviente={}",
            idsAEliminar.size() + 1, codigo, estado, equipoId, supervivienteId);
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private Lote mapLote(ResultSet rs) throws SQLException {
        Timestamp inicio = rs.getTimestamp("fecha_inicio");
        Timestamp fin    = rs.getTimestamp("fecha_fin");
        LocalDateTime fechaInicio = inicio != null
            ? LocalDateTime.ofInstant(inicio.toInstant(), ZoneId.systemDefault()) : null;
        LocalDateTime fechaFin = fin != null
            ? LocalDateTime.ofInstant(fin.toInstant(),    ZoneId.systemDefault()) : null;
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
            lote.getId(),
            lote.getIdNegocio(),
            lote.getAnio(),
            lote.getSecuencia(),
            lote.getAutoclaveNombre(),
            lote.getCapacidadTotal(),
            lote.getCapacidadUsada(),
            lote.getFechaInicio(),
            lote.getFechaFin(),
            lote.getEstado(),
            materiales
        );
    }

    private Lote obtenerLotePorId(Connection conn, int loteId) throws SQLException {
        String sql = "SELECT id, id_negocio, anio, secuencia, autoclave_nombre, " +
                     "capacidad_total, capacidad_usada, fecha_inicio, fecha_fin, estado " +
                     "FROM lotes WHERE id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapLote(rs);
            }
        }
        return null;
    }
}