package com.example.features.catalogo.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.features.catalogo.model.ItemCatalogo;
import com.example.infrastructure.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla {@code catalogo_otros}, con baja <b>lógica</b> (columna {@code activo},
 * V26).
 *
 * Características:
 * - Sin seed: las entradas se crean on-the-fly al guardar un equipo.
 * - Búsqueda por coincidencia parcial, desde 1 carácter, sólo entre las activas.
 * - Upsert: si la descripción ya existe y está activa, devuelve su ID existente; si está de
 *   baja, rechaza con {@link BusinessException} sin re-crearla.
 */
public class CatalogoOtrosDAO {

    private static final Logger log = LoggerFactory.getLogger(CatalogoOtrosDAO.class);

    /**
     * Busca descripciones que contengan el texto dado (case-insensitive).
     * Sin mínimo de caracteres: 1 carácter ya dispara la búsqueda.
     *
     * <p><b>No ofrece las dadas de baja</b>: es el autocompletado de carga. Sigue siendo
     * síncrono, sin debounce — no se toca su forma (excepción aceptada del repo).</p>
     *
     * @param texto Fragmento a buscar
     * @return Lista de descripciones coincidentes (nunca null)
     */
    public List<String> buscarPorDescripcionParcial(String texto) {
        List<String> resultados = new ArrayList<>();
        if (texto == null || texto.trim().isEmpty()) return resultados;

        String sql = "SELECT descripcion FROM catalogo_otros " +
                     "WHERE descripcion LIKE ? AND activo = TRUE ORDER BY descripcion LIMIT 20";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + texto.trim() + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(rs.getString("descripcion"));
                }
            }
        } catch (SQLException e) {
            log.error("Error buscando en catalogo_otros: texto='{}'", texto, e);
        }
        return resultados;
    }

    /**
     * Retorna el ID de la descripción si ya existe, o -1 si no.
     *
     * <p><b>No filtra por {@code activo}.</b> No es una consulta de carga: la usa
     * {@code LoteDAO.obtenerOCrearCatalogoOtros} para partir un material ya cargado, y ahí una
     * descripción de baja tiene que seguir resolviéndose.</p>
     *
     * @param descripcion Texto exacto a buscar
     * @return ID existente o -1
     */
    public int obtenerIdPorDescripcion(String descripcion) {
        if (descripcion == null || descripcion.trim().isEmpty()) return -1;

        String sql = "SELECT id FROM catalogo_otros WHERE descripcion = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, descripcion.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            log.error("Error obteniendo id de catalogo_otros: '{}'", descripcion, e);
        }
        return -1;
    }

    /**
     * Busca o crea la entrada para la descripción dada y devuelve su ID.
     * Opera dentro de la conexión/transacción proporcionada para garantizar
     * atomicidad cuando se llama desde {@link EquipoOtrosDAO}.
     *
     * <p><b>El orden — {@code INSERT IGNORE} primero, {@code SELECT} después — no se invierte.</b>
     * Es lo que ya cerraba la ventana entre chequeo y creación antes de este paso, y sigue siendo
     * lo que la sostiene ahora: como el {@code INSERT IGNORE} es un no-op cuando la fila ya
     * existe (activa o no), una descripción dada de baja <b>nunca se re-crea</b>. Invertir a
     * SELECT-primero reabriría esa ventana: dos operadores tipeando la misma descripción nueva al
     * mismo tiempo podrían los dos ver "no existe" y los dos intentar el INSERT, y uno de los dos
     * chocaría contra el UNIQUE con forma de error técnico.</p>
     *
     * <p>Nota aparte, porque los dos métodos se llaman casi igual y hacen casi lo mismo:
     * {@code LoteDAO.obtenerOCrearCatalogoOtros} es un <b>segundo</b> {@code obtenerOCrear} sobre
     * esta misma tabla, para partir un material "Otros" <b>ya cargado</b> al armar un lote. Ese es
     * SELECT-primero-INSERT-después —tiene la carrera que este método cierra— y a propósito no se
     * toca ni se unifica con éste: es un camino histórico (la descripción ya está en el snapshot
     * de {@code equipo_otros_materiales}), y meterle la regla de carga rompería el armado de un
     * lote con un material cuya descripción se dio de baja después de cargarlo.</p>
     *
     * @param conn        Conexión activa (propiedad del caller; no se cierra aquí)
     * @param descripcion Descripción a insertar o reutilizar
     * @return ID de la entrada en catalogo_otros
     * @throws SQLException si falla la operación
     * @throws BusinessException si la descripción ya existe pero está dada de baja
     */
    public int obtenerOCrear(Connection conn, String descripcion) throws SQLException {
        String desc = descripcion.trim();

        // 1. Intentar insertar (ignorando duplicado de UNIQUE)
        String sqlInsert = "INSERT IGNORE INTO catalogo_otros (descripcion) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
            pstmt.setString(1, desc);
            pstmt.executeUpdate();
        }

        // 2. Leer el ID y el estado (ya existía, activa o no, o acabamos de crearla activa)
        String sqlSelect = "SELECT id, activo FROM catalogo_otros WHERE descripcion = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
            pstmt.setString(1, desc);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No se pudo obtener ID de catalogo_otros para: " + desc);
                }
                if (!rs.getBoolean("activo")) {
                    throw new BusinessException(String.format(Constantes.Mensajes.MATERIAL_OTROS_DE_BAJA, desc));
                }
                return rs.getInt("id");
            }
        }
    }

    /** Todo el catálogo, activo y de baja: es lo que muestra Ajustes. */
    public List<ItemCatalogo> obtenerTodosConEstado() {
        String sql = "SELECT id, descripcion, activo FROM catalogo_otros ORDER BY descripcion";
        List<ItemCatalogo> items = new ArrayList<>();

        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                items.add(new ItemCatalogo(rs.getInt("id"), rs.getString("descripcion"), rs.getBoolean("activo")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error al obtener el catálogo de Otros con estado", e);
        }
        return items;
    }

    /**
     * Da de baja una descripción del catálogo de Otros. CAS: 0 filas afectadas significa que
     * otro operador ya la dio de baja.
     *
     * <p>{@code equipo_otros_materiales.catalogo_otros_id} es {@code FK RESTRICT} (V2): un
     * {@code INSERT} en vuelo desde {@link #obtenerOCrear} sostiene un lock sobre la fila del
     * catálogo hasta el commit de esa transacción, y este {@code UPDATE} puede quedarse esperando
     * detrás. Es un choque entre operadores, no un error técnico — mismo patrón que los
     * catálogos de Lavadero.</p>
     */
    public void darDeBaja(int id) {
        actualizarEstado("UPDATE catalogo_otros SET activo = FALSE WHERE id = ? AND activo = TRUE", id, "dar de baja");
    }

    public void reactivar(int id) {
        actualizarEstado("UPDATE catalogo_otros SET activo = TRUE WHERE id = ? AND activo = FALSE", id, "reactivar");
    }

    private void actualizarEstado(String sql, int id, String accion) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Constantes.Mensajes.CONFLICTO_CATALOGO);
        } catch (SQLException e) {
            if (ControlConcurrencia.esContencionDeLock(e)) {
                log.warn("No se pudo {} la descripción {} de catalogo_otros (contención de lock)", accion, id, e);
                throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_GENERICO);
            }
            log.error("Error al {} la descripción {} de catalogo_otros", accion, id, e);
            throw new DatabaseException("Error al " + accion + " la descripción #" + id + " de catalogo_otros", e);
        }
    }
}
