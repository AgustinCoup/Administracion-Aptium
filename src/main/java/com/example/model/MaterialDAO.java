package com.example.model;

import com.example.database.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
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

            // 1. Actualizar cada material
            String sqlMaterial = "UPDATE equipo_materiales SET estado = ? WHERE equipo_id = ? AND codigo_catalogo = ?";
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
}
