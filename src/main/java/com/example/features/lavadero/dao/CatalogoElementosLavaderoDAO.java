package com.example.features.lavadero.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.CategoriaElementoLavadero;
import com.example.features.lavadero.model.ElementoCatalogo;
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
import java.util.Optional;

/**
 * Catálogo de elementos de Clasificación de Lavadero, con baja <b>lógica</b> (columna
 * {@code activo}, V26).
 *
 * <p>Regla que gobierna todo este DAO: {@link #findActivos()} alimenta el combo de
 * Clasificación —una consulta de <b>carga</b>, donde el operador elige algo nuevo— y filtra por
 * {@code activo = TRUE}. {@link #findAll()} es para Ajustes y no filtra nada.
 * {@link #buscarPorNombre(String)} tampoco filtra: sirve para rechazar altas duplicadas, y un
 * nombre dado de baja tiene que seguir bloqueando un alta con el mismo nombre.</p>
 */
public class CatalogoElementosLavaderoDAO {

    private static final Logger log = LoggerFactory.getLogger(CatalogoElementosLavaderoDAO.class);

    private static final String SQL_ACTIVOS =
        "SELECT id, nombre, activo FROM catalogo_elementos_lavadero WHERE activo = TRUE ORDER BY nombre";

    private static final String SQL_TODOS =
        "SELECT id, nombre, activo FROM catalogo_elementos_lavadero ORDER BY nombre";

    private static final String SQL_BUSCAR_POR_NOMBRE =
        "SELECT id, nombre, activo FROM catalogo_elementos_lavadero WHERE LOWER(nombre) = LOWER(?)";

    private static final String SQL_INSERTAR =
        "INSERT INTO catalogo_elementos_lavadero (nombre, categoria) VALUES (?, ?)";

    /** Sólo para armar el mensaje del alta rechazada. Ver {@link #agregar}. */
    private static final String SQL_ESTADO_POR_NOMBRE =
        "SELECT activo FROM catalogo_elementos_lavadero WHERE LOWER(nombre) = LOWER(?)";

    /** CAS: 0 filas significa que otro ya lo dio de baja. */
    private static final String SQL_DAR_DE_BAJA =
        "UPDATE catalogo_elementos_lavadero SET activo = FALSE WHERE id = ? AND activo = TRUE";

    /** CAS: 0 filas significa que otro ya lo reactivó. */
    private static final String SQL_REACTIVAR =
        "UPDATE catalogo_elementos_lavadero SET activo = TRUE WHERE id = ? AND activo = FALSE";

    /** Para combos de CARGA: sólo lo que el operador puede elegir. */
    public List<ElementoCatalogo> findActivos() {
        return leer(SQL_ACTIVOS, "activos");
    }

    /** Todos, activos y de baja: es lo que muestra Ajustes. */
    public List<ElementoCatalogo> findAll() {
        return leer(SQL_TODOS, "todos");
    }

    /**
     * Busca por nombre sin distinguir mayúsculas; sirve para rechazar altas duplicadas.
     *
     * <p><b>No filtra por {@code activo}.</b> Si filtrara, se podría dar de alta un nombre que ya
     * existe dado de baja, y el segundo {@code INSERT} chocaría contra el {@code UNIQUE} con
     * forma de error técnico en vez de un aviso claro.</p>
     */
    public Optional<ElementoCatalogo> buscarPorNombre(String nombre) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BUSCAR_POR_NOMBRE)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error al buscar elemento de catálogo lavadero por nombre '{}'", nombre, e);
            throw new DatabaseException("Error al buscar elemento de catálogo por nombre", e);
        }
        return Optional.empty();
    }

    /**
     * Inserta un elemento nuevo y lo devuelve ya con su id asignado.
     *
     * <p>El {@code INSERT} va primero: la PK/UNIQUE es la guarda real, y la segunda lectura (ver
     * {@link #mensajeDeDuplicado}) sólo elige cuál de los dos carteles mostrar. Mismo patrón que
     * {@code LavarropasDAO.agregar}.</p>
     *
     * @throws BusinessException si el nombre ya existe — con mensaje distinto según esté activo
     *                           o dado de baja
     */
    public ElementoCatalogo agregar(String nombre, CategoriaElementoLavadero categoria) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, categoria.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new ElementoCatalogo(rs.getInt(1), nombre, true);
                }
            }
            throw new DatabaseException("El alta del elemento de catálogo no devolvió id: " + nombre);
        } catch (SQLException e) {
            if (esViolacionDeClave(e)) {
                log.warn("Alta rechazada: el elemento de catálogo '{}' ya existe", nombre);
                throw new BusinessException(mensajeDeDuplicado(nombre));
            }
            log.error("Error al agregar elemento de catálogo lavadero '{}'", nombre, e);
            throw new DatabaseException("Error al agregar elemento de catálogo", e);
        }
    }

    /**
     * Da de baja un elemento del catálogo.
     *
     * <p>Choca con las clasificaciones en vuelo: {@code elementos_clasificacion_lavadero.elemento_id}
     * es {@code FK RESTRICT}, así que un {@code INSERT} de {@code ClasificacionLavaderoDAO.guardar}
     * en curso sostiene un lock que puede hacer esperar a este {@code UPDATE} hasta el lock wait
     * timeout. Eso es un choque entre operadores, no un error técnico.</p>
     */
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
                log.warn("No se pudo {} el elemento de catálogo {} (contención de lock)", accion, id, e);
                throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_GENERICO);
            }
            log.error("Error al {} el elemento de catálogo {}", accion, id, e);
            throw new DatabaseException("Error al " + accion + " el elemento de catálogo #" + id, e);
        }
    }

    private List<ElementoCatalogo> leer(String sql, String que) {
        List<ElementoCatalogo> result = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapear(rs));
            }
        } catch (SQLException e) {
            log.error("Error al cargar catálogo de elementos lavadero ({})", que, e);
            throw new DatabaseException("Error al cargar catálogo de elementos lavadero", e);
        }
        return Collections.unmodifiableList(result);
    }

    private static ElementoCatalogo mapear(ResultSet rs) throws SQLException {
        return new ElementoCatalogo(rs.getInt("id"), rs.getString("nombre"), rs.getBoolean("activo"));
    }

    /**
     * Segunda lectura, con la conexión del {@code INSERT} ya cerrada, sólo para elegir el cartel.
     */
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
            log.error("Error al leer el estado del elemento '{}' para el mensaje de duplicado", nombre, e);
        }
        return String.format(Constantes.Mensajes.CATALOGO_ITEM_YA_EXISTE, nombre);
    }

    private static boolean esViolacionDeClave(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
            || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }
}
