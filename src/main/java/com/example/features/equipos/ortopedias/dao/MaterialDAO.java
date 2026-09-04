package com.example.features.equipos.ortopedias.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
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

    /**
     * Aplica en una transacción los movimientos de estado de los materiales de un equipo.
     *
     * <p><b>Guarda de concurrencia:</b> cada {@link MovimientoMaterial} trae el
     * {@code estadoOrigenEsperado} que la pantalla mostraba al tildarlo. Antes de escribir se
     * relee la fila {@code FOR UPDATE} y se compara: si el estado cambió, otro operador avanzó
     * ese material mientras este pensaba y se lanza {@link ConflictoConcurrenciaException}, que
     * revierte la transacción de este equipo entera.
     *
     * <p><b>Por qué la guarda es el {@code estado} del material y no la {@code version} del
     * equipo:</b> la columna {@code version} de {@code equipos} existe y se mantiene (V21), pero
     * <b>no viaja acá</b> — ni en {@link com.example.common.model.EquipoKey} ni como parámetro de
     * este método. Guardar con la {@code version} del agregado haría chocar a dos operadores que
     * avanzan materiales <em>distintos</em> del mismo equipo (los dos incrementan la misma
     * {@code version}), un falso positivo que enseña al operador a ignorar el aviso. La guarda por
     * material es precisa: sólo choca cuando el choque es real. Un parámetro {@code version} sin
     * consumidor sería además código muerto. Su consumidor previsto es {@code Correcciones}
     * (reemplazo de la fila entera desde un snapshot), fuera del alcance de este cambio.
     */
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

                // Guarda de concurrencia: va ANTES de validar la cantidad. Si el estado cambió,
                // el saldo que vio el operador es de otra fila conceptual y "cantidad inválida"
                // sería un mensaje engañoso.
                EstadoEquipo estadoEsperado = movimiento.getEstadoOrigenEsperado();
                if (estadoEsperado == null || !estadoActual.equalsIgnoreCase(estadoEsperado.getNombre())) {
                    throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_MATERIAL);
                }

                if (cantidadMover <= 0 || cantidadMover > cantidadActual) {
                    throw new SQLException("Cantidad inválida para mover en lote: " + materialId);
                }

                if (estadoDestino == null) {
                    throw new SQLException("El estado destino no puede ser nulo (material " + materialId + ")");
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

    /**
     * Ruta de {@code Correcciones}: guarda por {@code version} del agregado, además del scope por
     * {@code equipo_id} para que un {@code materialId} de otro equipo no pueda bumpear ni escribir
     * acá. El bump guardado va primero: toma el lock de la fila de {@code equipos} antes de tocar
     * el detalle. Con {@code 0} filas no se commitea — el rollback del try-with-resources revierte
     * el bump.
     */
    public boolean actualizarCantidad(Integer equipoId, Integer materialId, Integer cantidadNueva,
                                      int versionEsperada) {
        String sql = "UPDATE equipo_materiales SET cantidad = ? WHERE id = ? AND equipo_id = ?";
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            EquipoMaterialHelper.bumpVersionConGuarda(conn, equipoId, versionEsperada);

            int filasActualizadas;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, cantidadNueva);
                ps.setInt(2, materialId);
                ps.setInt(3, equipoId);
                filasActualizadas = ps.executeUpdate();
            }
            if (filasActualizadas == 0) return false;
            tx.commit();
            log.debug("Cantidad del material {} actualizada a {}", materialId, cantidadNueva);
            return true;
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

    /** Ruta de {@code Correcciones}; mismo criterio de guarda y scope que {@link #actualizarCantidad}. */
    public boolean actualizarCodigo(Integer equipoId, Integer materialId, Integer codigoNuevo,
                                    int versionEsperada) {
        String sql = "UPDATE equipo_materiales SET codigo_catalogo = ? WHERE id = ? AND equipo_id = ?";
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            EquipoMaterialHelper.bumpVersionConGuarda(conn, equipoId, versionEsperada);

            int filasActualizadas;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, codigoNuevo);
                ps.setInt(2, materialId);
                ps.setInt(3, equipoId);
                filasActualizadas = ps.executeUpdate();
            }
            if (filasActualizadas == 0) return false;
            tx.commit();
            log.debug("Código del material {} actualizado a {}", materialId, codigoNuevo);
            return true;
        } catch (SQLException e) {
            throw new DatabaseException("Error al actualizar código del material " + materialId, e);
        }
    }

    public FilaMaterial obtenerMaterial(Integer materialId) {
        String sql =
            "SELECT em.id, em.equipo_id, em.codigo_catalogo, cd.descripcion, em.cantidad, em.estado " +
            "FROM equipo_materiales em " +
            "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo " +
            "WHERE em.id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, materialId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapearFila(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener material " + materialId, e);
        }
        return null; // no encontrado
    }

    /**
     * Mapea la fila actual del {@code ResultSet} a {@link FilaMaterial}.
     * El acceso es por nombre de columna, así que reordenar el {@code SELECT}
     * no rompe nada; agregar o quitar una columna del record sí falla al compilar.
     */
    private static FilaMaterial mapearFila(ResultSet rs) throws SQLException {
        return new FilaMaterial(
            rs.getInt("id"),
            rs.getInt("equipo_id"),
            rs.getInt("codigo_catalogo"),
            rs.getString("descripcion"),
            rs.getInt("cantidad"),
            rs.getString("estado")
        );
    }

    /**
     * Ruta de {@code Correcciones}; bump guardado como primera sentencia. Ya llama a
     * {@link EquipoMaterialHelper#recalcularEstadoEquipo}, que bumpea por su cuenta: la
     * {@code version} sube 2 en esta ruta, inocuo porque el token es un CAS, no un contador.
     */
    public Integer agregarMaterial(Integer equipoId, Integer codigoCatalogo, Integer cantidad,
                                   int versionEsperada) {
        String sqlInsertMaterial =
            "INSERT INTO equipo_materiales (equipo_id, codigo_catalogo, cantidad, estado) " +
            "VALUES (?, ?, ?, ?)";
        String sqlInsertMovimiento =
            "INSERT INTO material_movimientos " +
            "(material_id, equipo_id, cantidad, estado_origen, estado_destino) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            EquipoMaterialHelper.bumpVersionConGuarda(conn, equipoId, versionEsperada);

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

    public List<FilaMaterial> obtenerMaterialesPorCodigo(Integer equipoId, Integer codigoCatalogo) {
        List<FilaMaterial> materiales = new ArrayList<>();
        String sql =
            "SELECT em.id, em.equipo_id, em.codigo_catalogo, cd.descripcion, em.cantidad, em.estado " +
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
                    materiales.add(mapearFila(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener materiales por código", e);
        }

        return materiales;
    }

    /**
     * Ruta de {@code Correcciones}; bump guardado primero. Un {@code DELETE} de 0 filas después de
     * un bump que sí matcheó es contradictorio (la version dice que nadie tocó el equipo, pero las
     * filas que la pantalla mostraba no están): lleva su propio {@link ControlConcurrencia}, no
     * commitea el bump y nada aguas abajo se ejecuta.
     */
    public boolean eliminarMaterialesPorCodigo(Integer equipoId, Integer codigoCatalogo,
                                               int versionEsperada) {
        String sqlSelectIds =
            "SELECT id FROM equipo_materiales WHERE equipo_id = ? AND codigo_catalogo = ?";
        String sqlDeleteMovimientos =
            "DELETE FROM material_movimientos WHERE material_id = ?";
        String sqlDeleteMateriales =
            "DELETE FROM equipo_materiales WHERE id = ?";

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            EquipoMaterialHelper.bumpVersionConGuarda(conn, equipoId, versionEsperada);

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

            if (idsMateriales.isEmpty()) return false;

            int filasEliminadas = 0;
            try (PreparedStatement psMov = conn.prepareStatement(sqlDeleteMovimientos);
                 PreparedStatement psMat = conn.prepareStatement(sqlDeleteMateriales)) {
                for (Integer materialId : idsMateriales) {
                    psMov.setInt(1, materialId);
                    psMov.addBatch();

                    psMat.setInt(1, materialId);
                    psMat.addBatch();
                }
                psMov.executeBatch();
                for (int filas : psMat.executeBatch()) filasEliminadas += filas;
            }
            ControlConcurrencia.exigirFilasAfectadas(idsMateriales.size(), filasEliminadas,
                Constantes.Mensajes.CONFLICTO_CORRECCION);

            EquipoMaterialHelper.recalcularEstadoEquipo(conn, equipoId);
            tx.commit();

            log.info("Materiales con código={} eliminados del equipo {}", codigoCatalogo, equipoId);
            return true;

        } catch (SQLException e) {
            throw new DatabaseException("Error al eliminar materiales por código", e);
        }
    }
}
