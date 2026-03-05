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
 *
 * La consulta completa de todos los registros delega en la vista SQL
 * {@code vista_auditoria}, que centraliza los tres orígenes de datos
 * (modificaciones, equipos eliminados y materiales eliminados).
 * Esto simplifica el código Java y hace que la lógica de unión sea
 * mantenible directamente en la base de datos.
 */
public class AuditoriaDAO {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaDAO.class);

    // ── Escritura ────────────────────────────────────────────────────────────

    /**
     * Registra un cambio en la tabla de auditoría.
     *
     * @param equipoId        ID del equipo
     * @param materialId      ID del material (null si es eliminación de equipo)
     * @param tipoCambio      MODIFICACION_CANTIDAD | MODIFICACION_CODIGO | ELIMINACION_EQUIPO
     * @param campoModificado Campo que cambió (null si es eliminación)
     * @param valorAnterior   Valor anterior
     * @param valorNuevo      Valor nuevo
     * @param motivo          Motivo del cambio
     * @return true si se registró exitosamente
     */
    public boolean registrarCambio(Integer equipoId, Integer materialId,
                                   String tipoCambio, String campoModificado,
                                   String valorAnterior, String valorNuevo, String motivo) {
        String sql =
            "INSERT INTO equipos_auditoria " +
            "(equipo_id, material_id, tipo_cambio, campo_modificado, valor_anterior, valor_nuevo, motivo) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, equipoId);
            if (materialId != null) ps.setInt(2, materialId); else ps.setNull(2, Types.INTEGER);
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
     * Guarda snapshot de un equipo eliminado para trazabilidad histórica persistente.
     * Al guardar el snapshot antes del hard-delete, la vista puede recuperar el nombre
     * del cliente incluso después de que el equipo desaparezca de la tabla principal.
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
        String sql =
            "INSERT INTO equipos_eliminados " +
            "(equipo_id_original, nro_cliente, cliente_nombre, nro_profesional, paciente, " +
            " nro_institucion, institucion_nombre, estado, motivo) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, equipoIdOriginal);
            if (nroCliente    != null) ps.setInt(2, nroCliente);    else ps.setNull(2, Types.INTEGER);
            ps.setString(3, clienteNombre);
            if (nroProfesional != null) ps.setInt(4, nroProfesional); else ps.setNull(4, Types.INTEGER);
            ps.setString(5, paciente);
            if (nroInstitucion != null) ps.setInt(6, nroInstitucion); else ps.setNull(6, Types.INTEGER);
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
     * Guarda snapshot de un material eliminado para trazabilidad histórica persistente.
     */
    public boolean registrarMaterialEliminado(Integer equipoIdOriginal,
                                              Integer materialIdOriginal,
                                              Integer codigoCatalogo,
                                              String descripcion,
                                              Integer cantidad,
                                              String estado,
                                              String motivo) {
        String sql =
            "INSERT INTO materiales_eliminados " +
            "(equipo_id_original, material_id_original, codigo_catalogo, descripcion, cantidad, estado, motivo) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, equipoIdOriginal);
            if (materialIdOriginal != null) ps.setInt(2, materialIdOriginal); else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, codigoCatalogo);
            ps.setString(4, descripcion);
            if (cantidad != null) ps.setInt(5, cantidad); else ps.setNull(5, Types.INTEGER);
            ps.setString(6, estado);
            ps.setString(7, motivo);

            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            log.error("Error al registrar material eliminado equipo={}, código={}", equipoIdOriginal, codigoCatalogo, e);
            return false;
        }
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    /**
     * Obtiene el historial de auditoría para un equipo específico.
     * Consulta directamente {@code equipos_auditoria} (sin pasar por la vista,
     * ya que el contexto del equipo es conocido y no se necesitan los campos extendidos).
     *
     * @param equipoId ID del equipo
     * @return Lista de cambios ordenados descendentemente por fecha
     */
    public List<EquipoAuditoria> obtenerPorEquipo(Integer equipoId) {
        List<EquipoAuditoria> auditorias = new ArrayList<>();
        String sql =
            "SELECT id, equipo_id, material_id, tipo_cambio, campo_modificado, " +
            "       valor_anterior, valor_nuevo, motivo, fecha_cambio " +
            "FROM equipos_auditoria " +
            "WHERE equipo_id = ? " +
            "ORDER BY fecha_cambio DESC";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auditorias.add(mapAuditoria(rs, false));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener auditoría del equipo {}", equipoId, e);
        }
        return auditorias;
    }

    /**
     * Obtiene todos los registros de auditoría consultando la vista {@code vista_auditoria}.
     * La vista centraliza los tres orígenes (modificaciones, equipos eliminados y materiales
     * eliminados) y garantiza que las modificaciones previas a una eliminación de equipo
     * sigan siendo visibles (usando COALESCE entre equipos y equipos_eliminados para el cliente).
     *
     * @return Lista completa ordenada descendentemente por fecha
     */
    public List<EquipoAuditoria> obtenerTodos() {
        List<EquipoAuditoria> auditorias = new ArrayList<>();
        String sql =
            "SELECT id, equipo_id, material_id, tipo_cambio, campo_modificado, " +
            "       valor_anterior, valor_nuevo, motivo, fecha_cambio, " +
            "       cliente_nombre, material_info " +
            "FROM vista_auditoria " +
            "ORDER BY fecha_cambio DESC";

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {

            while (rs.next()) {
                auditorias.add(mapAuditoria(rs, true));
            }
        } catch (SQLException e) {
            log.error("Error al obtener todas las auditorías", e);
        }
        return auditorias;
    }

    // ── Mapeo ────────────────────────────────────────────────────────────────

    /**
     * Mapea un ResultSet a {@link EquipoAuditoria}.
     *
     * @param rs             ResultSet posicionado en la fila actual
     * @param conExtendidos  Si true, lee también {@code cliente_nombre} y {@code material_info}
     *                       (solo disponibles en consultas sobre vista_auditoria)
     */
    private EquipoAuditoria mapAuditoria(ResultSet rs, boolean conExtendidos) throws SQLException {
        EquipoAuditoria a = new EquipoAuditoria();
        a.setId(rs.getInt("id"));
        a.setEquipoId(rs.getInt("equipo_id"));
        a.setMaterialId(rs.getObject("material_id", Integer.class));
        a.setTipoCambio(rs.getString("tipo_cambio"));
        a.setCampoModificado(rs.getString("campo_modificado"));
        a.setValorAnterior(rs.getString("valor_anterior"));
        a.setValorNuevo(rs.getString("valor_nuevo"));
        a.setMotivo(rs.getString("motivo"));

        Timestamp ts = rs.getTimestamp("fecha_cambio");
        if (ts != null) {
            a.setFechaCambio(
                java.time.LocalDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()));
        }

        if (conExtendidos) {
            a.setClienteNombre(rs.getString("cliente_nombre"));
            a.setMaterialInfo(rs.getString("material_info"));
        }

        return a;
    }
}