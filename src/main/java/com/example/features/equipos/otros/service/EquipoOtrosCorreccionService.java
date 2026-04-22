package com.example.features.equipos.otros.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de correcciones para equipos de tipo "Otros".
 *
 * Refleja la estructura de {@link com.example.features.equipos.ortopedias.service.EquipoCorreccionService}
 * adaptada a las particularidades de REMITO y DETALLES.
 *
 * RESTRICCIONES:
 * - Solo permite modificar equipos en estado NUEVO.
 * - Para REMITO: "corregir cantidad" solo está disponible cuando no hay filas reales
 *   en equipo_otros_materiales (el equipo no tuvo movimientos aún).
 * - Todas las operaciones registran en las tablas de auditoría compartidas con ortopedias
 *   usando tipo_equipo = 'OTROS'.
 */
public class EquipoOtrosCorreccionService {

    private static final Logger log = LoggerFactory.getLogger(EquipoOtrosCorreccionService.class);
    private static final String TIPO = "OTROS";

    private final EquipoOtrosDAO   equipoOtrosDAO;
    private final AuditoriaDAO     auditoriaDAO;
    private final CatalogoOtrosDAO catalogoOtrosDAO;

    public EquipoOtrosCorreccionService(EquipoOtrosDAO equipoOtrosDAO,
                                        AuditoriaDAO auditoriaDAO,
                                        CatalogoOtrosDAO catalogoOtrosDAO) {
        if (equipoOtrosDAO   == null) throw new IllegalArgumentException("EquipoOtrosDAO no puede ser nulo");
        if (auditoriaDAO     == null) throw new IllegalArgumentException("AuditoriaDAO no puede ser nulo");
        if (catalogoOtrosDAO == null) throw new IllegalArgumentException("CatalogoOtrosDAO no puede ser nulo");
        this.equipoOtrosDAO   = equipoOtrosDAO;
        this.auditoriaDAO     = auditoriaDAO;
        this.catalogoOtrosDAO = catalogoOtrosDAO;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public List<EquipoOtros> obtenerEquiposOtrosNuevos() {
        return equipoOtrosDAO.obtenerEquiposNuevos();
    }

    // ── REMITO: modificar cantidad global ────────────────────────────────────

    /**
     * Modifica remito_cantidad de un REMITO que todavía no tuvo movimientos.
     * Si ya hay filas en equipo_otros_materiales la operación es bloqueada.
     */
    public boolean modificarCantidadRemito(int equipoId, int cantidadNueva, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(cantidadNueva <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        EquipoOtros equipo = cargarYValidarNuevo(equipoId);
        if (equipo.getTipoIngreso() != TipoIngresoOtros.REMITO) {
            throw new ValidationException("Esta operación es solo para equipos de tipo REMITO");
        }

        if (tieneFilasMateriales(equipoId)) {
            throw new ValidationException(
                "El equipo ya tuvo movimientos de estado. No se puede modificar la cantidad del remito.");
        }

        int cantidadAnterior = equipo.getRemitoCantidad() != null ? equipo.getRemitoCantidad() : 0;

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE equipo_otros SET remito_cantidad = ? WHERE id = ?")) {

            ps.setInt(1, cantidadNueva);
            ps.setInt(2, equipoId);
            ps.executeUpdate();

        } catch (SQLException e) {
            log.error("Error al modificar remito_cantidad equipo={}", equipoId, e);
            throw new DatabaseException("Error al modificar la cantidad del remito");
        }

        auditoriaDAO.registrarCambio(equipoId, null, "MODIFICACION_CANTIDAD",
            "remito_cantidad",
            String.valueOf(cantidadAnterior),
            String.valueOf(cantidadNueva),
            motivo.trim(), TIPO);

        log.info("Cantidad remito equipo={} modificada {} → {} — motivo: {}",
            equipoId, cantidadAnterior, cantidadNueva, motivo);
        return true;
    }

    // ── DETALLES: modificar cantidad de material ─────────────────────────────

    public boolean modificarCantidadMaterial(int equipoId, int materialId,
                                              int cantidadNueva, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(cantidadNueva <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        cargarYValidarNuevo(equipoId);

        int cantidadAnterior = obtenerCantidadMaterial(materialId, equipoId);

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE equipo_otros_materiales SET cantidad = ? WHERE id = ? AND equipo_otros_id = ?")) {

            ps.setInt(1, cantidadNueva);
            ps.setInt(2, materialId);
            ps.setInt(3, equipoId);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new ValidationException("Material no encontrado en el equipo");

        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Error al modificar cantidad material={}", materialId, e);
            throw new DatabaseException("Error al modificar la cantidad del material");
        }

        auditoriaDAO.registrarCambio(equipoId, materialId, "MODIFICACION_CANTIDAD",
            "cantidad",
            String.valueOf(cantidadAnterior),
            String.valueOf(cantidadNueva),
            motivo.trim(), TIPO);

        log.info("Cantidad material={} equipo={} modificada {} → {} — motivo: {}",
            materialId, equipoId, cantidadAnterior, cantidadNueva, motivo);
        return true;
    }

    // ── DETALLES: agregar material ───────────────────────────────────────────

    public boolean agregarMaterial(int equipoId, String descripcion, int cantidad, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(descripcion == null || descripcion.trim().isEmpty(), "La descripción es obligatoria")
         .addErrorIf(cantidad <= 0, "La cantidad debe ser mayor a 0")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        cargarYValidarNuevo(equipoId);

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            int catalogoId = catalogoOtrosDAO.obtenerOCrear(conn, descripcion.trim());

            String sqlMat =
                "INSERT INTO equipo_otros_materiales " +
                "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado) " +
                "VALUES (?, ?, ?, ?, ?)";
            String sqlMov =
                "INSERT INTO otros_material_movimientos " +
                "(material_id, equipo_otros_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, NULL, ?)";

            int nuevoMaterialId;
            try (PreparedStatement ps = conn.prepareStatement(sqlMat, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt   (1, equipoId);
                ps.setInt   (2, catalogoId);
                ps.setString(3, descripcion.trim());
                ps.setInt   (4, cantidad);
                ps.setString(5, EstadoEquipo.NUEVO.getNombre());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No se generó ID para nuevo material_otros");
                    nuevoMaterialId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlMov)) {
                ps.setInt   (1, nuevoMaterialId);
                ps.setInt   (2, equipoId);
                ps.setInt   (3, cantidad);
                ps.setString(4, EstadoEquipo.NUEVO.getNombre());
                ps.executeUpdate();
            }

            conn.commit();

            auditoriaDAO.registrarCambio(equipoId, nuevoMaterialId, "ADICION_MATERIAL",
                "material_nuevo", null, String.valueOf(cantidad), motivo.trim(), TIPO);

            log.info("Material '{}' (cantidad={}) agregado al equipo {} — motivo: {}",
                descripcion, cantidad, equipoId, motivo);
            return true;

        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            rollback(conn, e);
            throw new DatabaseException("Error al agregar material al equipo");
        } finally {
            closeConn(conn);
        }
    }

    // ── DETALLES: eliminar material por descripción ──────────────────────────

    /** Elimina todas las filas del equipo con la descripción indicada y genera snapshots. */
    public boolean eliminarMaterial(int equipoId, String descripcion, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(descripcion == null || descripcion.trim().isEmpty(), "La descripción es obligatoria")
         .addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        cargarYValidarNuevo(equipoId);

        List<MaterialOtros> materiales = obtenerMaterialesPorDescripcion(equipoId, descripcion.trim());
        if (materiales.isEmpty()) {
            throw new ValidationException("No hay materiales con esa descripción en el equipo");
        }

        for (MaterialOtros m : materiales) {
            auditoriaDAO.registrarMaterialEliminado(
                equipoId, m.getId(), null,
                m.getDescripcion(), m.getCantidad(),
                m.getEstado() != null ? m.getEstado().getNombre() : null,
                motivo.trim(), TIPO);
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM equipo_otros_materiales WHERE equipo_otros_id = ? AND descripcion = ?")) {

            ps.setInt   (1, equipoId);
            ps.setString(2, descripcion.trim());
            ps.executeUpdate();

        } catch (SQLException e) {
            log.error("Error al eliminar material descripcion='{}' equipo={}", descripcion, equipoId, e);
            throw new DatabaseException("Error al eliminar el material");
        }

        auditoriaDAO.registrarCambio(equipoId, null, "ELIMINACION_MATERIAL",
            "material", null, null, motivo.trim(), TIPO);

        log.info("Material '{}' eliminado del equipo {} — motivo: {}", descripcion, equipoId, motivo);
        return true;
    }

    // ── Eliminar equipo ──────────────────────────────────────────────────────

    public boolean eliminarEquipo(int equipoId, String motivo) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(motivo == null || motivo.trim().isEmpty(), "El motivo es obligatorio");
        v.throwIfHasErrors();

        EquipoOtros equipo = cargarYValidarNuevo(equipoId);

        boolean snapEquipo = auditoriaDAO.registrarEquipoEliminado(
            equipo.getId(), equipo.getNroCliente(), equipo.getClienteNombre(),
            null, null, null, null,
            equipo.getEstado().getNombre(), motivo.trim(), TIPO);
        if (!snapEquipo) throw new DatabaseException("No se pudo registrar el snapshot del equipo eliminado");

        for (MaterialOtros m : equipo.getMateriales()) {
            boolean snapMat = auditoriaDAO.registrarMaterialEliminado(
                equipo.getId(), m.getId(), null,
                m.getDescripcion(), m.getCantidad(),
                m.getEstado() != null ? m.getEstado().getNombre() : null,
                motivo.trim(), TIPO);
            if (!snapMat) throw new DatabaseException("No se pudo registrar el snapshot del material eliminado");
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM equipo_otros WHERE id = ?")) {

            ps.setInt(1, equipoId);
            ps.executeUpdate();

        } catch (SQLException e) {
            log.error("Error al eliminar equipo_otros id={}", equipoId, e);
            throw new DatabaseException("Error al eliminar el equipo");
        }

        auditoriaDAO.registrarCambio(equipoId, null, "ELIMINACION_EQUIPO",
            "equipo", null, null, motivo.trim(), TIPO);

        log.info("EquipoOtros id={} eliminado — motivo: {}", equipoId, motivo);
        return true;
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private EquipoOtros cargarYValidarNuevo(int equipoId) {
        EquipoOtros equipo = equipoOtrosDAO.obtenerPorId(equipoId);
        if (equipo == null) {
            throw new ValidationException("El equipo no existe");
        }
        if (!equipo.getEstado().equals(EstadoEquipo.NUEVO)) {
            throw new ValidationException("El equipo no está en estado 'Nuevo' y no puede ser modificado");
        }
        return equipo;
    }

    private boolean tieneFilasMateriales(int equipoId) {
        String sql = "SELECT COUNT(*) FROM equipo_otros_materiales WHERE equipo_otros_id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.error("Error al verificar filas de materiales equipo={}", equipoId, e);
            return false;
        }
    }

    private int obtenerCantidadMaterial(int materialId, int equipoId) {
        String sql = "SELECT cantidad FROM equipo_otros_materiales WHERE id = ? AND equipo_otros_id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, materialId);
            ps.setInt(2, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("cantidad");
            }
        } catch (SQLException e) {
            log.error("Error al obtener cantidad material={}", materialId, e);
        }
        throw new ValidationException("El material no existe en el equipo");
    }

    private List<MaterialOtros> obtenerMaterialesPorDescripcion(int equipoId, String descripcion) {
        List<MaterialOtros> lista = new ArrayList<>();
        String sql =
            "SELECT id, catalogo_otros_id, descripcion, cantidad, estado " +
            "FROM equipo_otros_materiales " +
            "WHERE equipo_otros_id = ? AND descripcion = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt   (1, equipoId);
            ps.setString(2, descripcion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MaterialOtros m = new MaterialOtros(
                        rs.getInt("id"),
                        rs.getObject("catalogo_otros_id") != null ? rs.getInt("catalogo_otros_id") : null,
                        rs.getString("descripcion"),
                        rs.getInt("cantidad"),
                        EstadoEquipo.desdeBD(rs.getString("estado")),
                        null
                    );
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener materiales descripcion='{}' equipo={}", descripcion, equipoId, e);
        }
        return lista;
    }

    private void rollback(Connection conn, Exception e) {
        log.error("Error en EquipoOtrosCorreccionService — haciendo rollback", e);
        if (conn != null) {
            try { conn.rollback(); } catch (SQLException ex) {
                log.error("Error en rollback", ex);
            }
        }
    }

    private void closeConn(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) {
                log.error("Error al cerrar conexión", e);
            }
        }
    }
}
