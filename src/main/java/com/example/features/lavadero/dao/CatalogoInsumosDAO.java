package com.example.features.lavadero.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.InsumoCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Catálogo de insumos extra de un ciclo de lavado. {@code activo} existe desde la V24 (plan A);
 * este paso agrega el ABM completo: alta, baja y reactivación.
 *
 * <p><b>{@code darDeBaja} choca con las tandas en vuelo</b>, igual que {@link CatalogoJabonesDAO}:
 * {@code insumos_ciclo_lavadero.insumo_id} es {@code FK RESTRICT} desde la V24, y el
 * {@code INSERT} de {@code lanzarTanda} sostiene un lock hasta su commit.</p>
 */
public class CatalogoInsumosDAO {

    private static final Logger log = LoggerFactory.getLogger(CatalogoInsumosDAO.class);

    private static final String SQL_ACTIVOS =
        "SELECT id, nombre, activo FROM catalogo_insumos WHERE activo = TRUE ORDER BY nombre";

    private static final String SQL_TODOS =
        "SELECT id, nombre, activo FROM catalogo_insumos ORDER BY nombre";

    private static final String SQL_INSERTAR =
        "INSERT INTO catalogo_insumos (nombre) VALUES (?)";

    private static final String SQL_ESTADO_POR_NOMBRE =
        "SELECT activo FROM catalogo_insumos WHERE LOWER(nombre) = LOWER(?)";

    private static final String SQL_DAR_DE_BAJA =
        "UPDATE catalogo_insumos SET activo = FALSE WHERE id = ? AND activo = TRUE";

    private static final String SQL_REACTIVAR =
        "UPDATE catalogo_insumos SET activo = TRUE WHERE id = ? AND activo = FALSE";

    /** Para el combo de la card: sólo lo que el operador puede elegir. */
    public List<InsumoCatalogo> findActivos() {
        return leer(SQL_ACTIVOS, "activos");
    }

    /**
     * Todo el catálogo, activos e inactivos: es lo que muestra Ajustes.
     *
     * <p>Un fallo de SQL sale como {@link DatabaseException}, no como lista vacía: una lista
     * vacía le dice a la card "no hay insumos configurados", que no es lo mismo que "no pude leer
     * el catálogo".</p>
     */
    public List<InsumoCatalogo> findAll() {
        return leer(SQL_TODOS, "todos");
    }

    public InsumoCatalogo agregar(String nombre) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new InsumoCatalogo(rs.getInt(1), nombre, true);
                }
            }
            throw new DatabaseException("El alta del insumo no devolvió id: " + nombre);
        } catch (SQLException e) {
            if (esViolacionDeClave(e)) {
                log.warn("Alta rechazada: el insumo '{}' ya existe", nombre);
                throw new BusinessException(mensajeDeDuplicado(nombre));
            }
            log.error("Error al agregar el insumo '{}'", nombre, e);
            throw new DatabaseException("Error al agregar el insumo", e);
        }
    }

    public void darDeBaja(int id) {
        actualizarEstado(SQL_DAR_DE_BAJA, id, "dar de baja");
    }

    public void reactivar(int id) {
        actualizarEstado(SQL_REACTIVAR, id, "reactivar");
    }

    // ── privados ─────────────────────────────────────────────────────────────

    private void actualizarEstado(String sql, int id, String accion) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Constantes.Mensajes.CONFLICTO_CATALOGO);
        } catch (SQLException e) {
            if (ControlConcurrencia.esContencionDeLock(e)) {
                log.warn("No se pudo {} el insumo {} (contención de lock)", accion, id, e);
                throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_GENERICO);
            }
            log.error("Error al {} el insumo {}", accion, id, e);
            throw new DatabaseException("Error al " + accion + " el insumo #" + id, e);
        }
    }

    private List<InsumoCatalogo> leer(String sql, String que) {
        List<InsumoCatalogo> result = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new InsumoCatalogo(
                    rs.getInt("id"), rs.getString("nombre"), rs.getBoolean("activo")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al cargar el catálogo de insumos", e);
        }
        return Collections.unmodifiableList(result);
    }

    private String mensajeDeDuplicado(String nombre) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ESTADO_POR_NOMBRE)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && !rs.getBoolean("activo")) {
                    return String.format(Constantes.Mensajes.CATALOGO_ITEM_YA_EXISTE_DE_BAJA, nombre);
                }
            }
        } catch (SQLException e) {
            log.error("Error al leer el estado del insumo '{}' para el mensaje de duplicado", nombre, e);
        }
        return String.format(Constantes.Mensajes.CATALOGO_ITEM_YA_EXISTE, nombre);
    }

    private static boolean esViolacionDeClave(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
            || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }
}
