package com.example.features.equipos.ortopedias.dao;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.eliminacion.ArchivoIngresosDAO;
import com.example.common.eliminacion.Bloqueo;
import com.example.common.eliminacion.EliminacionBloqueadaException;
import com.example.common.eliminacion.IngresoAEliminar;
import com.example.common.eliminacion.IngresoArchivado;
import com.example.common.eliminacion.LotesEnCurso;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.eliminacion.SnapshotJson;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Elimina un ingreso de ortopedia ({@code equipos}) en <b>cualquier estado</b>, archivándolo antes
 * en {@code ingresos_eliminados} dentro de la misma transacción.
 *
 * <p><b>No es el borrado de Correcciones y no lo reusa.</b> {@code EquipoDAO.eliminarConVersion}
 * abre su propia conexión, y {@code EquipoCorreccionService.eliminarEquipo} audita
 * <em>después</em> del {@code DELETE}, aceptando que un fallo del registro deje un borrado sin
 * auditar. Acá la copia es lo único que queda del ingreso, así que archivo y borrado van en una
 * sola transacción: o las dos cosas, o ninguna.</p>
 *
 * <p>La guarda es la {@code version} de la cabecera que vio el resumen previo
 * ({@link #resumir}): el mismo criterio que Correcciones. Si otro avanzó, entregó o corrigió algo
 * mientras el operador confirmaba, es conflicto — lo que se archiva tiene que ser lo que confirmó.</p>
 *
 * <p>Los materiales, sus movimientos y nada más caen por {@code ON DELETE CASCADE}. Un lote
 * finalizado que contenía materiales de este equipo queda con los demás, o vacío (decisión del
 * usuario: conserva la numeración y la constancia del autoclave).</p>
 */
public class EliminadorEquipoOrtopedia {

    private static final Logger log = LoggerFactory.getLogger(EliminadorEquipoOrtopedia.class);

    /** Versión del contenido de {@code snapshot}; ver {@link SnapshotJson#raiz}. */
    static final int FORMATO_SNAPSHOT = 1;

    private static final String SQL_CABECERA =
        "SELECT e.*, c.nombre AS cliente_nombre, p.nombre AS profesional_nombre, "
            + "       i.nombre AS institucion_nombre "
            + "FROM equipos e "
            + "LEFT JOIN clientes c ON e.nro_cliente = c.id "
            + "LEFT JOIN profesionales p ON e.nro_profesional = p.id "
            + "LEFT JOIN instituciones i ON e.nro_institucion = i.id "
            + "WHERE e.id = ?";

    private static final String SQL_MATERIALES =
        "SELECT em.*, cd.descripcion, l.id_negocio AS lote_id_negocio "
            + "FROM equipo_materiales em "
            + "LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo = cd.codigo "
            + "LEFT JOIN lotes l ON em.lote_id = l.id "
            + "WHERE em.equipo_id = ? ORDER BY em.id";

    private static final String SQL_MOVIMIENTOS =
        "SELECT * FROM material_movimientos WHERE equipo_id = ? ORDER BY id";

    /** Paso 1 de la transacción. Ver el javadoc de {@link #eliminar} por qué va por {@code equipo_id}. */
    private static final String SQL_BLOQUEAR_MATERIALES =
        "SELECT id, lote_id FROM equipo_materiales WHERE equipo_id = ? ORDER BY id FOR UPDATE";

    private static final String SQL_BLOQUEAR_CABECERA =
        "SELECT version FROM equipos WHERE id = ? FOR UPDATE";

    private static final String SQL_BORRAR =
        "DELETE FROM equipos WHERE id = ? AND version = ?";

    private final ArchivoIngresosDAO archivo;

    public EliminadorEquipoOrtopedia(ArchivoIngresosDAO archivo) {
        this.archivo = archivo;
    }

    /**
     * Lo que el diálogo de confirmación muestra: cabecera, materiales con su lote, bloqueos y la
     * {@code version} que va a viajar como guarda. Lectura sin transacción: es informativa, y
     * {@link #eliminar} re-verifica todo.
     *
     * @throws ResourceNotFoundException si el equipo ya no existe (otro lo eliminó)
     */
    public ResumenEquipo resumir(int equipoId) {
        try (Connection conn = ConnectionPool.getConnection()) {
            Cabecera cabecera = leerCabecera(conn, equipoId);
            List<ResumenEquipo.LineaMaterial> materiales = new ArrayList<>();
            Set<Integer> loteIds = new LinkedHashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(SQL_MATERIALES)) {
                ps.setInt(1, equipoId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        materiales.add(new ResumenEquipo.LineaMaterial(
                            rs.getString("descripcion"), rs.getInt("cantidad"),
                            EstadoEquipo.desdeBD(rs.getString("estado")).getNombre(),
                            rs.getString("lote_id_negocio")));
                        int loteId = rs.getInt("lote_id");
                        if (!rs.wasNull()) {
                            loteIds.add(loteId);
                        }
                    }
                }
            }
            List<Bloqueo> bloqueos = List.copyOf(LotesEnCurso.entre(conn, loteIds));
            return new ResumenEquipo(new IngresoAEliminar(ModuloIngreso.ORTOPEDIA, equipoId),
                cabecera.clienteNombre(),
                cabecera.institucionNombre(), cabecera.pacienteNombre(), cabecera.fechaIngreso(),
                cabecera.estado(), cabecera.version(), materiales, bloqueos, List.of());
        } catch (SQLException e) {
            log.error("Error al resumir el equipo {} para eliminarlo", equipoId, e);
            throw new DatabaseException("Error al leer el equipo " + equipoId + " para eliminarlo", e);
        }
    }

    /**
     * Archiva y borra el equipo, en una transacción, en este orden:
     *
     * <ol>
     *   <li><b>materiales {@code FOR UPDATE}</b>, por {@code equipo_id}, en orden de {@code id};</li>
     *   <li><b>cabecera {@code FOR UPDATE}</b>, que trae la {@code version} actual;</li>
     *   <li><b>recién acá, la primera lectura no bloqueante</b>: cuáles de los lotes de (1) siguen
     *       en curso ({@link LotesEnCurso}). Si hay alguno → {@link EliminacionBloqueadaException}.
     *       Si no, y la cabecera falta o tiene otra {@code version} que la vista →
     *       {@link ConflictoConcurrenciaException};</li>
     *   <li>el snapshot (cabecera con nombres, materiales con su lote, movimientos) →
     *       {@link ArchivoIngresosDAO#archivar} sobre esta misma conexión;</li>
     *   <li>{@code DELETE … AND version = ?} → {@link ControlConcurrencia#exigirFilaAfectada};</li>
     *   <li>{@code commit}.</li>
     * </ol>
     *
     * <h2>Por qué este orden (nada de esto lo delata un test: H2 no lo reproduce)</h2>
     *
     * <p><b>Materiales → cabecera</b> es el orden de {@code MaterialDAO.aplicarMovimientos}, de
     * {@code entregarMateriales}, de {@code LoteDAO.lanzarLote} y de {@code finalizarLote}/
     * {@code marcarLoteFallo} (que terminan en el recálculo de la cabecera). Con los caminos
     * calientes no se cruza: el que llega segundo espera en el primer material y no toma nada
     * más.</p>
     *
     * <p><b>Correcciones es el único al revés</b>: {@code EquipoMaterialHelper.bumpVersionConGuarda}
     * toma la cabecera primero y después toca los materiales. Un borrado contra una corrección
     * simultánea del mismo equipo puede dar un deadlock; MySQL lo resuelve abortando a uno, y si
     * es el borrado lo informa como conflicto (ver el {@code catch}). Se acepta: las dos son
     * operaciones esporádicas, y la alternativa —cabecera primero acá— cruzaría en cambio con
     * todos los caminos calientes.</p>
     *
     * <p><b>El {@code FOR UPDATE} de (1) va por {@code equipo_id}, no por id de material</b>: toma
     * el rango del índice {@code idx_equipo_material_equipo}, así que también frena el
     * {@code INSERT} de la fila partida que {@code aplicarMovimientos}/{@code lanzarLote} harían
     * sobre este equipo. Por id se bloquearían sólo las filas que ya existen.</p>
     *
     * <p><b>{@code lotes} se lee sin bloquear, a propósito</b> ({@code finalizarLote} bloquea
     * {@code lotes} → materiales, y un {@code FOR UPDATE} acá lo cruzaría). El razonamiento de por
     * qué eso alcanza está en {@link LotesEnCurso}.</p>
     *
     * <p><b>El bloqueo se informa antes que la versión</b>, aunque las dos cosas se sepan a la vez.
     * Meter un material en un lote bumpea la {@code version} (el recálculo de la cabecera), así que
     * un lote lanzado mientras el operador confirmaba da las dos. Informado como conflicto, el
     * operador reintentaría para recién ahí enterarse del lote; informado como bloqueo, sabe qué
     * lote finalizar. Ninguno de los dos borra nada, así que el orden de los chequeos no cambia la
     * guarda: la {@code version} se sigue exigiendo antes de archivar.</p>
     *
     * <p><b>Todos los {@code FOR UPDATE} van antes de la primera lectura no bloqueante.</b> Bajo el
     * {@code REPEATABLE READ} de MySQL la vista de la transacción se fija en esa primera lectura:
     * con los locks ya tomados, lo que ve (3) es lo último commiteado, incluido un lote que se lanzó
     * mientras (1) esperaba. Una lectura común antes de (1) congelaría una vista donde ese lote no
     * existe. El {@code lote_id} de (1) no tiene ese problema: una lectura con {@code FOR UPDATE}
     * lee siempre la última versión commiteada.</p>
     *
     * @param versionVista la {@code version} del {@link ResumenEquipo} que confirmó el operador
     * @throws ConflictoConcurrenciaException si el equipo cambió o desapareció desde el resumen, o
     *                                        si la base cortó la espera de un lock
     * @throws EliminacionBloqueadaException  si algún material está en un lote en curso
     * @throws DatabaseException              ante cualquier otro error de base; no se borró nada
     */
    public void eliminar(int equipoId, int versionVista, String motivo, String puesto) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            // ── (1) y (2): bloquear todo. Ninguna lectura no bloqueante antes del final. ──
            Set<Integer> loteIds = bloquearMateriales(conn, equipoId);
            Integer versionActual = bloquearCabecera(conn, equipoId);

            // ── (3): verificar. Primera lectura no bloqueante: fija la vista en MySQL. ──
            List<Bloqueo> bloqueos = List.copyOf(LotesEnCurso.entre(conn, loteIds));
            if (!bloqueos.isEmpty()) {
                throw new EliminacionBloqueadaException(bloqueos);
            }
            if (versionActual == null || versionActual != versionVista) {
                throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION);
            }

            // ── (4) y (5): archivar y borrar, en la misma transacción. ──
            archivo.archivar(conn, copiaParaArchivo(conn, equipoId, motivo, puesto));
            try (PreparedStatement ps = conn.prepareStatement(SQL_BORRAR)) {
                ps.setInt(1, equipoId);
                ps.setInt(2, versionVista);
                ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Mensajes.CONFLICTO_ELIMINACION);
            }

            tx.commit();
            log.info("Equipo de ortopedia {} eliminado y archivado desde {}", equipoId, puesto);
        } catch (SQLException e) {
            if (ControlConcurrencia.esContencionDeLock(e)) {
                throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION, e);
            }
            log.error("Error al eliminar el equipo de ortopedia {}", equipoId, e);
            throw new DatabaseException("Error al eliminar el equipo " + equipoId, e);
        }
    }

    // ── Fases de la transacción ──────────────────────────────────────────────

    private static Set<Integer> bloquearMateriales(Connection conn, int equipoId) throws SQLException {
        Set<Integer> loteIds = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_BLOQUEAR_MATERIALES)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int loteId = rs.getInt("lote_id");
                    if (!rs.wasNull()) {
                        loteIds.add(loteId);
                    }
                }
            }
        }
        return loteIds;
    }

    /** @return la {@code version} actual, o {@code null} si la cabecera ya no existe */
    private static Integer bloquearCabecera(Connection conn, int equipoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_BLOQUEAR_CABECERA)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("version") : null;
            }
        }
    }

    /** Lee el árbol completo del equipo y arma la fila de archivo. */
    private static IngresoArchivado copiaParaArchivo(Connection conn, int equipoId, String motivo, String puesto)
            throws SQLException {
        JSONObject raiz = SnapshotJson.raiz(ModuloIngreso.ORTOPEDIA, FORMATO_SNAPSHOT);
        String clienteNombre;
        LocalDateTime fechaIngreso;
        String estado;
        try (PreparedStatement ps = conn.prepareStatement(SQL_CABECERA)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Imposible con la cabecera bloqueada; si pasa, que no quede un archivo vacío.
                    throw new SQLException("equipos " + equipoId + " desapareció con la fila bloqueada");
                }
                raiz.put("equipo", SnapshotJson.filaActual(rs));
                clienteNombre = rs.getString("cliente_nombre");
                fechaIngreso = aFecha(rs.getTimestamp("fecha_ingreso"));
                estado = EstadoEquipo.desdeBD(rs.getString("estado")).getNombre();
            }
        }
        raiz.put("materiales", filas(conn, SQL_MATERIALES, equipoId));
        raiz.put("movimientos", filas(conn, SQL_MOVIMIENTOS, equipoId));
        return new IngresoArchivado(ModuloIngreso.ORTOPEDIA, equipoId, clienteNombre, fechaIngreso,
            estado, motivo, puesto, null, raiz.toString());
    }

    // ── Lecturas ─────────────────────────────────────────────────────────────

    private static Cabecera leerCabecera(Connection conn, int equipoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_CABECERA)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException(Mensajes.INGRESO_YA_ELIMINADO);
                }
                return new Cabecera(rs.getString("cliente_nombre"), rs.getString("institucion_nombre"),
                    rs.getString("paciente"), aFecha(rs.getTimestamp("fecha_ingreso")),
                    EstadoEquipo.desdeBD(rs.getString("estado")).getNombre(), rs.getInt("version"));
            }
        }
    }

    private static JSONArray filas(Connection conn, String sql, int equipoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                return SnapshotJson.filas(rs);
            }
        }
    }

    private static LocalDateTime aFecha(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** La cabecera tal como la necesita el resumen. */
    private record Cabecera(String clienteNombre, String institucionNombre, String pacienteNombre,
                            LocalDateTime fechaIngreso, String estado, int version) {}
}
