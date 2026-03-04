package com.example.features.equipos.dao;

import com.example.features.equipos.model.EquipoAuditoria;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestionar registros de auditoría de equipos.
 * Responsabilidad única: Operaciones CRUD sobre la tabla equipos_auditoria.
 */
public class AuditoriaDAO {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaDAO.class);

    /**
     * Registra un cambio en la tabla de auditoría.
     * @param equipoId ID del equipo
     * @param materialId ID del material (null si es eliminación de equipo)
     * @param tipoCambio MODIFICACION_CANTIDAD, MODIFICACION_CODIGO, ELIMINACION_EQUIPO
     * @param campoModificado Campo que cambió (null si es eliminación)
     * @param valorAnterior Valor anterior (null si es eliminación)
     * @param valorNuevo Valor nuevo (null si es eliminación)
     * @param motivo Motivo del cambio
     * @return true si se registró exitosamente
     */
    public boolean registrarCambio(Integer equipoId, Integer materialId, 
                                   String tipoCambio, String campoModificado, 
                                   String valorAnterior, String valorNuevo, String motivo) {
        String sql = "INSERT INTO equipos_auditoria (equipo_id, material_id, tipo_cambio, campo_modificado, " +
                    "valor_anterior, valor_nuevo, motivo) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, equipoId);
            if (materialId != null) {
                ps.setInt(2, materialId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, tipoCambio);
            ps.setString(4, campoModificado);
            ps.setString(5, valorAnterior);
            ps.setString(6, valorNuevo);
            ps.setString(7, motivo);
            
            ps.executeUpdate();
            log.debug("Auditoría registrada: equipo={}, tipo={}", equipoId, tipoCambio);
            return true;
            
        } catch (SQLException e) {
            log.error("Error al registrar auditoría para equipo {}", equipoId, e);
            return false;
        }
    }

    /**
     * Guarda snapshot de un equipo eliminado para auditoría histórica persistente.
     */
    public boolean registrarEquipoEliminado(Integer equipoIdOriginal,
                                            Integer nroCliente,
                                            String clienteNombre,
                                            Integer nroProfesional,
                                            String paciente,
                                            Integer nroInstitucion,
                                            String institucionNombre,
                                            String estado,
                                            String motivo) {
        String sql = "INSERT INTO equipos_eliminados (equipo_id_original, nro_cliente, cliente_nombre, " +
                "nro_profesional, paciente, nro_institucion, institucion_nombre, estado, motivo) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, equipoIdOriginal);
            if (nroCliente != null) {
                ps.setInt(2, nroCliente);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, clienteNombre);
            if (nroProfesional != null) {
                ps.setInt(4, nroProfesional);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setString(5, paciente);
            if (nroInstitucion != null) {
                ps.setInt(6, nroInstitucion);
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.setString(7, institucionNombre);
            ps.setString(8, estado);
            ps.setString(9, motivo);

            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.error("Error al registrar equipo eliminado {}", equipoIdOriginal, e);
            return false;
        }
    }

    /**
     * Guarda snapshot de materiales eliminados para auditoría histórica persistente.
     */
    public boolean registrarMaterialEliminado(Integer equipoIdOriginal,
                                              Integer materialIdOriginal,
                                              Integer codigoCatalogo,
                                              String descripcion,
                                              Integer cantidad,
                                              String estado,
                                              String motivo) {
        String sql = "INSERT INTO materiales_eliminados (equipo_id_original, material_id_original, codigo_catalogo, " +
                "descripcion, cantidad, estado, motivo) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, equipoIdOriginal);
            if (materialIdOriginal != null) {
                ps.setInt(2, materialIdOriginal);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setInt(3, codigoCatalogo);
            ps.setString(4, descripcion);
            if (cantidad != null) {
                ps.setInt(5, cantidad);
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setString(6, estado);
            ps.setString(7, motivo);

            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.error("Error al registrar material eliminado equipo={}, código={}", equipoIdOriginal, codigoCatalogo, e);
            return false;
        }
    }

    /**
     * Obtiene el historial de auditoría para un equipo específico.
     * @param equipoId ID del equipo
     * @return Lista de cambios registrados para el equipo
     */
    public List<EquipoAuditoria> obtenerPorEquipo(Integer equipoId) {
        List<EquipoAuditoria> auditorias = new ArrayList<>();
        String sql = "SELECT id, equipo_id, material_id, tipo_cambio, campo_modificado, valor_anterior, " +
                    "valor_nuevo, motivo, fecha_cambio FROM equipos_auditoria WHERE equipo_id = ? " +
                    "ORDER BY fecha_cambio DESC";
        
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auditorias.add(mapAuditoria(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener auditoría del equipo {}", equipoId, e);
        }
        return auditorias;
    }

    /**
     * Obtiene todos los registros de auditoría.
     * Excluye ELIMINACION_MATERIAL de materiales_eliminados cuando el material fue
     * eliminado como consecuencia de la eliminación del equipo (mismo minuto).
     * @return Lista completa de auditorías
     */
    public List<EquipoAuditoria> obtenerTodos() {
        List<EquipoAuditoria> auditorias = new ArrayList<>();
        String sql = "SELECT id, equipo_id, material_id, tipo_cambio, campo_modificado, valor_anterior, valor_nuevo, motivo, fecha_cambio " +
            "FROM equipos_auditoria WHERE tipo_cambio NOT IN ('ELIMINACION_EQUIPO', 'ELIMINACION_MATERIAL') " +
            "UNION ALL " +
            "SELECT id, equipo_id_original AS equipo_id, NULL AS material_id, 'ELIMINACION_EQUIPO' AS tipo_cambio, " +
            "'equipo' AS campo_modificado, " +
            "CONCAT('Cliente: ', COALESCE(cliente_nombre, '-'), ' | Paciente: ', COALESCE(paciente, '-')) AS valor_anterior, " +
            "NULL AS valor_nuevo, motivo, fecha_eliminacion AS fecha_cambio " +
            "FROM equipos_eliminados " +
            "UNION ALL " +
            "SELECT id, equipo_id_original AS equipo_id, material_id_original AS material_id, 'ELIMINACION_MATERIAL' AS tipo_cambio, " +
            "'material' AS campo_modificado, " +
            "CONCAT('Código: ', codigo_catalogo, ' | Desc: ', COALESCE(descripcion, '-'), ' | Cantidad: ', COALESCE(cantidad, 0)) AS valor_anterior, " +
            "NULL AS valor_nuevo, motivo, fecha_eliminacion AS fecha_cambio " +
            "FROM materiales_eliminados m " +
            "WHERE NOT EXISTS ( " +
            "  SELECT 1 FROM equipos_eliminados e " +
            "  WHERE e.equipo_id_original = m.equipo_id_original " +
            "  AND TIMESTAMPDIFF(MINUTE, e.fecha_eliminacion, m.fecha_eliminacion) = 0 " +
            ") " +
            "ORDER BY fecha_cambio DESC";
        
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                auditorias.add(mapAuditoria(rs));
            }
        } catch (SQLException e) {
            log.error("Error al obtener todas las auditorías", e);
        }
        return auditorias;
    }

    private EquipoAuditoria mapAuditoria(ResultSet rs) throws SQLException {
        EquipoAuditoria auditoria = new EquipoAuditoria();
        auditoria.setId(rs.getInt("id"));
        auditoria.setEquipoId(rs.getInt("equipo_id"));
        Integer materialId = rs.getObject("material_id", Integer.class);
        auditoria.setMaterialId(materialId);
        auditoria.setTipoCambio(rs.getString("tipo_cambio"));
        auditoria.setCampoModificado(rs.getString("campo_modificado"));
        auditoria.setValorAnterior(rs.getString("valor_anterior"));
        auditoria.setValorNuevo(rs.getString("valor_nuevo"));
        auditoria.setMotivo(rs.getString("motivo"));

        Timestamp ts = rs.getTimestamp("fecha_cambio");
        if (ts != null) {
            auditoria.setFechaCambio(java.time.LocalDateTime.ofInstant(
                ts.toInstant(), ZoneId.systemDefault()));
        }

        return auditoria;
    }
}
