package com.example.features.equipos.ortopedias.dao;

import com.example.common.exception.DatabaseException;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DAO para gestionar operaciones sobre materiales individuales.
 *
 * <p>La lógica compartida de recálculo de estado de equipo y unificación de
 * duplicados se delega a {@link EquipoMaterialHelper}, que es la única fuente
 * de verdad para esas operaciones.
 */
public class MaterialDAO {

    private static final Logger log = LoggerFactory.getLogger(MaterialDAO.class);

    // ── Métodos transaccionales ──────────────────────────────────────────────

    public boolean actualizarEstadoMaterial(int equipoId, int codigoCatalogo, EstadoEquipo nuevoEstado) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            String sqlMaterial = "UPDATE equipo_materiales SET estado = ? WHERE equipo_id = ? AND codigo_catalogo = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlMaterial)) {
                pstmt.setString(1, nuevoEstado.getNombre());
                pstmt.setInt(2, equipoId);
                pstmt.setInt(3, codigoCatalogo);
                if (pstmt.executeUpdate() == 0) {
                    throw new SQLException("No se encontró el material a actualizar");
                }
            }

            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
            tx.commit();
            return true;

        } catch (SQLException e) {
            throw new DatabaseException("Error al actualizar estado del material", e);
        }
    }

    public boolean actualizarMultiplesMateriales(int equipoId, Map<Integer, EstadoEquipo> actualizaciones) {
        if (actualizaciones == null || actualizaciones.isEmpty()) return true;

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            String sqlMaterial = "UPDATE equipo_materiales SET estado = ? WHERE equipo_id = ? AND id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlMaterial)) {
                for (Map.Entry<Integer, EstadoEquipo> entry : actualizaciones.entrySet()) {
                    pstmt.setString(1, entry.getValue().getNombre());
                    pstmt.setInt(2, equipoId);
                    pstmt.setInt(3, entry.getKey());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
            tx.commit();
            return true;

        } catch (SQLException e) {
            throw new DatabaseException("Error al actualizar múltiples materiales", e);
        }
    }

    public boolean aplicarMovimientos(int equipoId, List<MovimientoMaterial> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) return true;

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            String sqlSelectLote =
                "SELECT em.codigo_catalogo, em.cantidad, em.estado " +
                "FROM equipo_materiales em WHERE em.id = ? AND em.equipo_id = ? FOR UPDATE";
            String sqlUpdateCantidad = "UPDATE equipo_materiales SET cantidad = ? WHERE id = ? AND equipo_id = ?";
            String sqlUpdateEstado   = "UPDATE equipo_materiales SET estado = ? WHERE id = ? AND equipo_id = ?";
            String sqlInsertLote     =
                "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado) " +
                "VALUES (?, ?, ?, ?)";
            String sqlMovimiento =
                "INSERT INTO material_movimientos " +
                "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)";

            boolean requiereLavado  = true;
            boolean requiereEmpaque = true;
            String sqlEquipoConfig = "SELECT requiere_lavado, requiere_empaque FROM equipos WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlEquipoConfig)) {
                pstmt.setInt(1, equipoId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        requiereLavado  = rs.getBoolean("requiere_lavado");
                        requiereEmpaque = rs.getBoolean("requiere_empaque");
                    }
                }
            }

            for (MovimientoMaterial movimiento : movimientos) {
                int materialId      = movimiento.getMaterialId();
                int cantidadMover   = movimiento.getCantidad();
                EstadoEquipo estadoDestino = movimiento.getEstadoDestino();

                int    codigo;
                int    cantidadActual;
                String estadoActual;

                try (PreparedStatement pstmt = conn.prepareStatement(sqlSelectLote)) {
                    pstmt.setInt(1, materialId);
                    pstmt.setInt(2, equipoId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("No se encontró el lote a mover: " + materialId);
                        }
                        codigo        = rs.getInt("codigo_catalogo");
                        cantidadActual = rs.getInt("cantidad");
                        estadoActual  = rs.getString("estado");
                    }
                }

                if (cantidadMover <= 0 || cantidadMover > cantidadActual) {
                    throw new SQLException("Cantidad inválida para mover en lote: " + materialId);
                }

                if (estadoDestino == null) {
                    EstadoEquipo estadoActualEnum = EstadoEquipo.desdeBD(estadoActual);
                    estadoDestino = Equipo.calcularSiguienteEstado(
                        estadoActualEnum, requiereLavado, requiereEmpaque);
                }

                if (estadoDestino == null) {
                    throw new SQLException("El lote ya está en el estado final: " + materialId);
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
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            sqlInsertLote, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setInt(1, equipoId);
                        pstmt.setInt(2, codigo);
                        pstmt.setInt(3, cantidadMover);
                        pstmt.setString(4, estadoDestino.getNombre());
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

            EquipoMaterialHelper.unificarMaterialesDuplicados(conn, equipoId);
            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
            tx.commit();
            return true;

        } catch (SQLException e) {
            throw new DatabaseException("Error al aplicar movimientos de materiales", e);
        }
    }

    public boolean entregarInstitucionCompleta(int nroInstitucion) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            List<Integer> equiposIds = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT id FROM equipos WHERE nro_institucion = ?")) {
                pstmt.setInt(1, nroInstitucion);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) equiposIds.add(rs.getInt("id"));
                }
            }

            if (equiposIds.isEmpty()) {
                tx.commit();
                return true;
            }

            String sqlSelectMateriales =
                "SELECT id, estado, cantidad FROM equipo_materiales " +
                "WHERE equipo_id = ? AND LOWER(estado) = 'esterilizado' FOR UPDATE";
            String sqlUpdateMaterial =
                "UPDATE equipo_materiales SET estado = ? WHERE id = ?";
            String sqlMovimiento =
                "INSERT INTO material_movimientos " +
                "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)";

            for (int equipoId : equiposIds) {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlSelectMateriales)) {
                    pstmt.setInt(1, equipoId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            int    materialId  = rs.getInt("id");
                            String estadoActual = rs.getString("estado");
                            int    cantidad    = rs.getInt("cantidad");

                            try (PreparedStatement update = conn.prepareStatement(sqlUpdateMaterial)) {
                                update.setString(1, EstadoEquipo.ENTREGADO.getNombre());
                                update.setInt(2, materialId);
                                update.executeUpdate();
                            }
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

                EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
            }

            tx.commit();
            log.info("Institución {} entregada correctamente. {} equipos afectados",
                nroInstitucion, equiposIds.size());
            return true;

        } catch (SQLException e) {
            throw new DatabaseException("Error al entregar institución completa: " + nroInstitucion, e);
        }
    }

    // ── Métodos simples (sin transacción propia) ─────────────────────────────

    public boolean actualizarCantidad(Integer materialId, Integer cantidadNueva) {
        String sql = "UPDATE equipo_materiales SET cantidad = ? WHERE id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cantidadNueva);
            ps.setInt(2, materialId);
            int filasActualizadas = ps.executeUpdate();
            if (filasActualizadas > 0) {
                log.debug("Cantidad del material {} actualizada a {}", materialId, cantidadNueva);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new DatabaseException("Error al actualizar cantidad del material " + materialId, e);
        }
    }

    public Integer obtenerCantidad(Integer materialId) {
        String sql = "SELECT cantidad FROM equipo_materiales WHERE id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, materialId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("cantidad");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener cantidad del material " + materialId, e);
        }
        return null; // no encontrado
    }

    public boolean actualizarCodigo(Integer materialId, Integer codigoNuevo) {
        String sql = "UPDATE equipo_materiales SET codigo_catalogo = ? WHERE id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, codigoNuevo);
            ps.setInt(2, materialId);
            int filasActualizadas = ps.executeUpdate();
            if (filasActualizadas > 0) {
                log.debug("Código del material {} actualizado a {}", materialId, codigoNuevo);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new DatabaseException("Error al actualizar código del material " + materialId, e);
        }
    }

    public Object[] obtenerMaterial(Integer materialId) {
        String sql =
            "SELECT em.codigo_catalogo, em.equipo_id, cd.descripcion, em.cantidad, em.estado " +
            "FROM equipo_materiales em " +
            "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
            "WHERE em.id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, materialId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Object[]{
                        rs.getInt("codigo_catalogo"),
                        rs.getInt("equipo_id"),
                        rs.getString("descripcion"),
                        rs.getInt("cantidad"),
                        rs.getString("estado")
                    };
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener material " + materialId, e);
        }
        return null; // no encontrado
    }

    public Integer agregarMaterial(Integer equipoId, Integer codigoCatalogo, Integer cantidad) {
        String sqlInsertMaterial =
            "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado) " +
            "VALUES (?, ?, ?, ?)";
        String sqlInsertMovimiento =
            "INSERT INTO material_movimientos " +
            "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            int nuevoMaterialId;
            try (PreparedStatement ps = conn.prepareStatement(sqlInsertMaterial, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, equipoId);
                ps.setInt(2, codigoCatalogo);
                ps.setInt(3, cantidad);
                ps.setString(4, EstadoEquipo.NUEVO.getNombre());
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        nuevoMaterialId = rs.getInt(1);
                    } else {
                        throw new SQLException("No se generó ID para el nuevo material");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlInsertMovimiento)) {
                ps.setInt(1, nuevoMaterialId);
                ps.setInt(2, equipoId);
                ps.setInt(3, cantidad);
                ps.setString(4, EstadoEquipo.NUEVO.getNombre());
                ps.setString(5, EstadoEquipo.NUEVO.getNombre());
                ps.executeUpdate();
            }

            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
            tx.commit();

            log.info("Material código={} (cantidad={}) agregado al equipo {} -> id={}",
                codigoCatalogo, cantidad, equipoId, nuevoMaterialId);
            return nuevoMaterialId;

        } catch (SQLException e) {
            throw new DatabaseException("Error al agregar material al equipo", e);
        }
    }

    public List<Object[]> obtenerMaterialesPorCodigo(Integer equipoId, Integer codigoCatalogo) {
        List<Object[]> materiales = new ArrayList<>();
        String sql =
            "SELECT em.id, em.codigo_catalogo, cd.descripcion, em.cantidad, em.estado " +
            "FROM equipo_materiales em " +
            "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
            "WHERE em.equipo_id = ? AND em.codigo_catalogo = ? " +
            "ORDER BY em.id";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipoId);
            ps.setInt(2, codigoCatalogo);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    materiales.add(new Object[] {
                        rs.getInt("id"),
                        rs.getInt("codigo_catalogo"),
                        rs.getString("descripcion"),
                        rs.getInt("cantidad"),
                        rs.getString("estado")
                    });
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener materiales por código", e);
        }

        return materiales;
    }

    public boolean eliminarMaterialesPorCodigo(Integer equipoId, Integer codigoCatalogo) {
        String sqlSelectIds =
            "SELECT id FROM equipo_materiales WHERE equipo_id = ? AND codigo_catalogo = ?";
        String sqlDeleteMovimientos =
            "DELETE FROM material_movimientos WHERE material_id = ?";
        String sqlDeleteMateriales =
            "DELETE FROM equipo_materiales WHERE id = ?";

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            List<Integer> idsMateriales = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlSelectIds)) {
                ps.setInt(1, equipoId);
                ps.setInt(2, codigoCatalogo);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        idsMateriales.add(rs.getInt("id"));
                    }
                }
            }

            if (idsMateriales.isEmpty()) {
                tx.commit();
                return false;
            }

            try (PreparedStatement psMov = conn.prepareStatement(sqlDeleteMovimientos);
                 PreparedStatement psMat = conn.prepareStatement(sqlDeleteMateriales)) {
                for (Integer materialId : idsMateriales) {
                    psMov.setInt(1, materialId);
                    psMov.addBatch();

                    psMat.setInt(1, materialId);
                    psMat.addBatch();
                }
                psMov.executeBatch();
                psMat.executeBatch();
            }

            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
            tx.commit();

            log.info("Materiales con código={} eliminados del equipo {}", codigoCatalogo, equipoId);
            return true;

        } catch (SQLException e) {
            throw new DatabaseException("Error al eliminar materiales por código", e);
        }
    }
}
