package com.example.features.equipos.dao;

import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.EstadoEquipo;
import com.example.features.equipos.model.MovimientoMaterial;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DAO para gestionar operaciones sobre materiales individuales.
 * Permite actualizar el estado de materiales específicos.
 */
public class MaterialDAO {

    private static final Logger log = LoggerFactory.getLogger(MaterialDAO.class);

    /**
     * Actualiza el estado de un material específico en la base de datos.
     * Después de actualizar el material, recalcula y actualiza el estado del equipo.
     * 
     * @param equipoId ID del equipo al que pertenece el material
     * @param codigoCatalogo Código del material en el catálogo
     * @param nuevoEstado Nuevo estado para el material
     * @return true si la actualización fue exitosa, false en caso contrario
     */
    public boolean actualizarEstadoMaterial(int equipoId, int codigoCatalogo, EstadoEquipo nuevoEstado) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false); // Transacción

            // 1. Actualizar el estado del material
            String sqlMaterial = "UPDATE equipo_materiales SET estado = ? WHERE equipo_id = ? AND codigo_catalogo = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlMaterial)) {
                pstmt.setString(1, nuevoEstado.getNombre());
                pstmt.setInt(2, equipoId);
                pstmt.setInt(3, codigoCatalogo);
                int filasAfectadas = pstmt.executeUpdate();
                
                if (filasAfectadas == 0) {
                    throw new SQLException("No se encontró el material a actualizar");
                }
            }

            // 2. Recalcular el estado del equipo basado en el material más atrasado
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
                    // Convertir el orden al estado correspondiente
                    for (EstadoEquipo estado : EstadoEquipo.values()) {
                        if (estado.getOrden() == ordenMinimo) {
                            estadoEquipo = estado;
                            break;
                        }
                    }
                }
            }

            // 3. Actualizar el estado del equipo
            String sqlEquipo = "UPDATE equipos SET estado = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlEquipo)) {
                pstmt.setString(1, estadoEquipo.getNombre());
                pstmt.setInt(2, equipoId);
                pstmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error al hacer rollback", ex); }
            }
            log.error("Error al actualizar estado del material", e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
            }
        }
    }

    /**
     * Actualiza múltiples materiales de un equipo en una sola transacción.
     * Útil para confirmar varios cambios pendientes a la vez.
     * 
     * @param equipoId ID del equipo
     * @param actualizaciones Map con código de material y su nuevo estado
     * @return true si todas las actualizaciones fueron exitosas
     */
    public boolean actualizarMultiplesMateriales(int equipoId, Map<Integer, EstadoEquipo> actualizaciones) {
        if (actualizaciones == null || actualizaciones.isEmpty()) {
            return true;
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. Actualizar cada lote de material por ID
            String sqlMaterial = "UPDATE equipo_materiales SET estado = ? WHERE equipo_id = ? AND id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlMaterial)) {
                for (java.util.Map.Entry<Integer, EstadoEquipo> entry : actualizaciones.entrySet()) {
                    pstmt.setString(1, entry.getValue().getNombre());
                    pstmt.setInt(2, equipoId);
                    pstmt.setInt(3, entry.getKey());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            // 2. Recalcular el estado del equipo
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

            // 3. Actualizar el estado del equipo
            String sqlEquipo = "UPDATE equipos SET estado = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlEquipo)) {
                pstmt.setString(1, estadoEquipo.getNombre());
                pstmt.setInt(2, equipoId);
                pstmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error al hacer rollback", ex); }
            }
            log.error("Error al actualizar múltiples materiales", e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
            }
        }
    }

    /**
     * Aplica movimientos de subcantidades creando lotes nuevos cuando es necesario.
     * Cada movimiento avanza una cantidad hacia un estado destino dentro de la misma transacción.
     *
     * @param equipoId ID del equipo
     * @param movimientos Lista de movimientos a aplicar
     * @return true si todas las operaciones fueron exitosas
     */
    public boolean aplicarMovimientos(int equipoId, List<MovimientoMaterial> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) {
            return true;
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            String sqlSelectLote =
                "SELECT codigo_catalogo, descripcion_copia, cantidad, estado " +
                "FROM equipo_materiales WHERE id = ? AND equipo_id = ? FOR UPDATE";
            String sqlUpdateCantidad = "UPDATE equipo_materiales SET cantidad = ? WHERE id = ? AND equipo_id = ?";
            String sqlUpdateEstado = "UPDATE equipo_materiales SET estado = ? WHERE id = ? AND equipo_id = ?";
            String sqlInsertLote =
                "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, descripcion_copia, cantidad, estado) " +
                "VALUES (?, ?, ?, ?, ?)";
            String sqlMovimiento = "INSERT INTO material_movimientos " +
                                   "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                                   "VALUES (?, ?, ?, ?, ?)";

            boolean requiereLavado = true;
            boolean requiereEmpaque = true;
            String sqlEquipoConfig = "SELECT requiere_lavado, requiere_empaque FROM equipos WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlEquipoConfig)) {
                pstmt.setInt(1, equipoId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        requiereLavado = rs.getBoolean("requiere_lavado");
                        requiereEmpaque = rs.getBoolean("requiere_empaque");
                    }
                }
            }

            for (MovimientoMaterial movimiento : movimientos) {
                int materialId = movimiento.getMaterialId();
                int cantidadMover = movimiento.getCantidad();
                EstadoEquipo estadoDestino = movimiento.getEstadoDestino();

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

                if (estadoDestino == null) {
                    EstadoEquipo estadoActualEnum = EstadoEquipo.desdeBD(estadoActual);
                    estadoDestino = Equipo.calcularSiguienteEstado(
                        estadoActualEnum,
                        requiereLavado,
                        requiereEmpaque
                    );
                }

                if (estadoDestino == null) {
                    throw new SQLException("El lote ya esta en el estado final: " + materialId);
                }

                if (cantidadMover == cantidadActual) {
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdateEstado)) {
                        pstmt.setString(1, estadoDestino.getNombre());
                        pstmt.setInt(2, materialId);
                        pstmt.setInt(3, equipoId);
                        pstmt.executeUpdate();
                    }

                    try (PreparedStatement pstmt = conn.prepareStatement(sqlMovimiento)) {
                        pstmt.setInt(1, materialId);
                        pstmt.setInt(2, equipoId);
                        pstmt.setInt(3, cantidadMover);
                        pstmt.setString(4, estadoActual);
                        pstmt.setString(5, estadoDestino.getNombre());
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
                        pstmt.setString(5, estadoDestino.getNombre());
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
                        pstmt.setString(5, estadoDestino.getNombre());
                        pstmt.executeUpdate();
                    }
                }
            }

            // Unificar materiales con mismo código y estado dentro de la misma transacción
            unificarMaterialesDuplicados(conn, equipoId);

            // Recalcular estado del equipo basado en el material mas atrasado
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

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error al hacer rollback", ex); }
            }
            log.error("Error al aplicar movimientos de materiales", e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
            }
        }
    }

    /**
     * Unifica filas de equipo_materiales que tienen el mismo equipo_id, codigo_catalogo y estado.
     * Esto ocurre cuando una cantidad se divide en sublotes que luego convergen al mismo estado.
     *
     * La fila superviviente es la que tiene el movimiento más reciente.
     * Los movimientos de las filas eliminadas se reasignan al superviviente para preservar historial.
     *
     * Debe llamarse dentro de una transacción activa.
     *
     * @param conn conexión activa con autoCommit=false
     * @param equipoId ID del equipo a procesar
     */
    private void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        // Buscar grupos (codigo_catalogo, estado) con más de una fila
        String sqlGrupos =
            "SELECT codigo_catalogo, estado, SUM(cantidad) AS cantidad_total " +
            "FROM equipo_materiales " +
            "WHERE equipo_id = ? " +
            "GROUP BY codigo_catalogo, estado " +
            "HAVING COUNT(*) > 1";

        List<int[]> grupos = new ArrayList<>();   // [codigo, cantidadTotal], estado por separado
        List<String> estadosGrupos = new ArrayList<>();

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
            int codigo        = grupos.get(i)[0];
            int cantidadTotal = grupos.get(i)[1];
            String estado     = estadosGrupos.get(i);
            unificarGrupo(conn, equipoId, codigo, estado, cantidadTotal);
        }
    }

    /**
     * Unifica todas las filas de un grupo (mismo equipo, código y estado) en una sola.
     *
     * Criterio de superviviente: la fila con el movimiento más reciente en material_movimientos.
     * En caso de empate o sin movimientos, se usa el mayor id de equipo_materiales.
     *
     * Los material_movimientos de las filas eliminadas se reasignan al superviviente,
     * preservando el historial completo de trazabilidad.
     *
     * @param conn           conexión activa
     * @param equipoId       ID del equipo
     * @param codigo         codigo_catalogo del grupo
     * @param estado         estado del grupo
     * @param cantidadTotal  suma de cantidades de todas las filas del grupo
     */
    private void unificarGrupo(Connection conn, int equipoId, int codigo,
                                String estado, int cantidadTotal) throws SQLException {
        // Elegir superviviente: la fila con el movimiento más reciente; si hay empate, el mayor id
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
                if (!rs.next()) {
                    return;  // No debería ocurrir, pero por seguridad
                }
                supervivienteId = rs.getInt("id");
            }
        }

        // Actualizar la cantidad del superviviente con la suma total del grupo
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
                while (rs.next()) {
                    idsAEliminar.add(rs.getInt("id"));
                }
            }
        }

        if (idsAEliminar.isEmpty()) {
            return;
        }

        // Reasignar los movimientos de las filas eliminadas al superviviente (preserva historial)
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

    /**
     * Marca todos los materiales entregables de una institución como entregados.
     * Solo afecta materiales que estén >= ESTERILIZADO y < ENTREGADO.
     * Actualiza el estado de todos los equipos afectados.
     * 
     * @param nroInstitucion Número de institución
     * @return true si la operación fue exitosa
     */
    public boolean entregarInstitucionCompleta(int nroInstitucion) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. Obtener todos los equipos de la institución
            String sqlEquipos = "SELECT id FROM equipos WHERE nro_institucion = ?";
            java.util.List<Integer> equiposIds = new java.util.ArrayList<>();
            
            try (PreparedStatement pstmt = conn.prepareStatement(sqlEquipos)) {
                pstmt.setInt(1, nroInstitucion);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        equiposIds.add(rs.getInt("id"));
                    }
                }
            }

            if (equiposIds.isEmpty()) {
                conn.commit();
                return true;
            }

            // 2. Para cada equipo, marcar materiales entregables como ENTREGADO
            String sqlSelectMateriales = 
                "SELECT id, estado, cantidad FROM equipo_materiales " +
                "WHERE equipo_id = ? AND LOWER(estado) = 'esterilizado' " +
                "FOR UPDATE";
            String sqlUpdateMaterial = "UPDATE equipo_materiales SET estado = ? WHERE id = ?";
            String sqlMovimiento = 
                "INSERT INTO material_movimientos " +
                "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)";

            for (Integer equipoId : equiposIds) {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlSelectMateriales)) {
                    pstmt.setInt(1, equipoId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            int materialId = rs.getInt("id");
                            String estadoActual = rs.getString("estado");
                            int cantidad = rs.getInt("cantidad");

                            // Actualizar material a ENTREGADO
                            try (PreparedStatement update = conn.prepareStatement(sqlUpdateMaterial)) {
                                update.setString(1, EstadoEquipo.ENTREGADO.getNombre());
                                update.setInt(2, materialId);
                                update.executeUpdate();
                            }

                            // Registrar movimiento
                            try (PreparedStatement mov = conn.prepareStatement(sqlMovimiento)) {
                                mov.setInt(1, materialId);
                                mov.setInt(2, equipoId);
                                mov.setInt(3, cantidad);
                                mov.setString(4, estadoActual);
                                mov.setString(5, EstadoEquipo.ENTREGADO.getNombre());
                                mov.executeUpdate();
                            }
                        }
                    }
                }

                // 3. Recalcular estado del equipo
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

                // 4. Actualizar estado del equipo
                String sqlUpdateEquipo = "UPDATE equipos SET estado = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdateEquipo)) {
                    pstmt.setString(1, estadoEquipo.getNombre());
                    pstmt.setInt(2, equipoId);
                    pstmt.executeUpdate();
                }
            }

            conn.commit();
            log.info("Institución {} entregada correctamente. {} equipos afectados", nroInstitucion, equiposIds.size());
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error al hacer rollback", ex); }
            }
            log.error("Error al entregar institución completa: {}", nroInstitucion, e);
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { log.error("Error al cerrar conexión", e); }
            }
        }
    }
}