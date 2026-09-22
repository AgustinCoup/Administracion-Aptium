package com.example.features.lavadero.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo de jabones, con baja <b>lógica</b> (columna {@code activo}, V26). Mismo juego que
 * {@link CatalogoElementosLavaderoDAO}: {@link #findActivos()} para el combo de la card
 * (carga), {@link #findAll()} sin filtro para Ajustes.
 *
 * <p><b>{@code darDeBaja} choca con las tandas en vuelo.</b> {@code ciclos_lavadero.jabon_id} es
 * {@code FK RESTRICT} desde la V12: un {@code lanzarTanda} en curso sostiene un lock sobre la
 * fila del jabón hasta su commit, y el {@code UPDATE} de la baja puede quedarse esperando detrás
 * hasta el lock wait timeout. Es un choque entre operadores ("alguien está usando ese jabón en
 * este momento"), no un error técnico.</p>
 */
public class CatalogoJabonesDAO {

    private static final Logger log = LoggerFactory.getLogger(CatalogoJabonesDAO.class);

    private static final String SQL_ACTIVOS =
        "SELECT id, nombre, activo FROM catalogo_jabones WHERE activo = TRUE ORDER BY nombre";

    private static final String SQL_TODOS =
        "SELECT id, nombre, activo FROM catalogo_jabones ORDER BY nombre";

    private static final String SQL_INSERTAR =
        "INSERT INTO catalogo_jabones (nombre) VALUES (?)";

    private static final String SQL_ESTADO_POR_NOMBRE =
        "SELECT activo FROM catalogo_jabones WHERE LOWER(nombre) = LOWER(?)";

    private static final String SQL_DAR_DE_BAJA =
        "UPDATE catalogo_jabones SET activo = FALSE WHERE id = ? AND activo = TRUE";

    private static final String SQL_REACTIVAR =
        "UPDATE catalogo_jabones SET activo = TRUE WHERE id = ? AND activo = FALSE";

    /** Para el combo de la card: sólo lo que el operador puede elegir. */
    public List<JabonCatalogo> findActivos() {
        return leer(SQL_ACTIVOS, "activos");
    }

    /** Todos, activos y de baja: es lo que muestra Ajustes. */
    public List<JabonCatalogo> findAll() {
        return leer(SQL_TODOS, "todos");
    }

    /** Ver javadoc de la clase: el {@code INSERT} es la guarda, la segunda lectura sólo elige el cartel. */
    public JabonCatalogo agregar(String nombre) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new JabonCatalogo(rs.getInt(1), nombre, true);
                }
            }
            throw new DatabaseException("El alta del jabón no devolvió id: " + nombre);
        } catch (SQLException e) {
            if (esViolacionDeClave(e)) {
                log.warn("Alta rechazada: el jabón '{}' ya existe", nombre);
                throw new BusinessException(mensajeDeDuplicado(nombre));
            }
            log.error("Error al agregar el jabón '{}'", nombre, e);
            throw new DatabaseException("Error al agregar el jabón", e);
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
                log.warn("No se pudo {} el jabón {} (contención de lock)", accion, id, e);
                throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_GENERICO);
            }
            log.error("Error al {} el jabón {}", accion, id, e);
            throw new DatabaseException("Error al " + accion + " el jabón #" + id, e);
        }
    }

    private List<JabonCatalogo> leer(String sql, String que) {
        List<JabonCatalogo> result = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new JabonCatalogo(rs.getInt("id"), rs.getString("nombre"), rs.getBoolean("activo")));
            }
        } catch (SQLException e) {
            log.error("Error al cargar catálogo de jabones ({})", que, e);
            throw new DatabaseException("Error al cargar catálogo de jabones", e);
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
            log.error("Error al leer el estado del jabón '{}' para el mensaje de duplicado", nombre, e);
        }
        return String.format(Constantes.Mensajes.CATALOGO_ITEM_YA_EXISTE, nombre);
    }

    private static boolean esViolacionDeClave(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
            || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }
}
