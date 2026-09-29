package com.example.features.equipos.otros.dao;

import com.example.common.constants.Constantes;
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
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Elimina un ingreso de Otros ({@code equipo_otros}) en <b>cualquier estado</b>, archivándolo antes
 * en {@code ingresos_eliminados} dentro de la misma transacción.
 *
 * <p><b>Es el segundo punto donde Lavadero escribe en tablas del CDE</b>, junto con
 * {@code DerivadorIngresoCDE}. Por eso la transacción está partida en <b>fases públicas sobre una
 * {@link Connection} ajena</b> —{@link #bloquear}, {@link #verificar}, {@link #archivarYBorrar}—,
 * que no abren, no commitean ni cierran nada: el borrado de un ingreso de Lavadero las llama dentro
 * de <em>su</em> transacción para llevarse el {@code equipo_otros} que derivó. Existen para que
 * ninguna consulta de borrado del CDE se copie en Lavadero. {@link #eliminar} es la transacción
 * propia, la que usa Ver Equipos, y no es más que esas tres fases en orden.</p>
 *
 * <p>Lo mismo que {@code EliminadorEquipoOrtopedia}, y por las mismas razones: no reusa el borrado
 * de Correcciones ({@code EquipoOtrosDAO.eliminarEquipo} abre su propia conexión y la auditoría va
 * después), la guarda es la {@code version} del resumen, y materiales, movimientos y
 * {@code lote_otros_volumenes} caen por {@code CASCADE}. Lo propio de Otros:</p>
 * <ul>
 *   <li>{@code salidas_lavadero.equipo_otros_id} pasa a {@code NULL} por la FK ({@code V17}) y la
 *       salida conserva {@code destino = 'CDE_OTROS'}: el ingreso de Lavadero no se elimina.</li>
 *   <li>Los litros del lote ({@code lote_otros_volumenes}) son por lote <em>e ingreso</em>: sólo
 *       desaparece la fila de este equipo, los demás ingresos del lote conservan los suyos, y
 *       {@code lotes.capacidad_usada} no se toca (es lo que entró al autoclave).</li>
 * </ul>
 */
public class EliminadorEquipoOtros {

    private static final Logger log = LoggerFactory.getLogger(EliminadorEquipoOtros.class);

    /** Versión del contenido de {@code snapshot}; ver {@link SnapshotJson#raiz}. */
    static final int FORMATO_SNAPSHOT = 1;

    private static final String SQL_CABECERA =
        "SELECT eo.*, c.nombre AS cliente_nombre "
            + "FROM equipo_otros eo "
            + "LEFT JOIN clientes c ON eo.nro_cliente = c.id "
            + "WHERE eo.id = ?";

    private static final String SQL_MATERIALES =
        "SELECT m.*, l.id_negocio AS lote_id_negocio "
            + "FROM equipo_otros_materiales m "
            + "LEFT JOIN lotes l ON m.lote_id = l.id "
            + "WHERE m.equipo_otros_id = ? ORDER BY m.id";

    private static final String SQL_MOVIMIENTOS =
        "SELECT * FROM otros_material_movimientos WHERE equipo_otros_id = ? ORDER BY id";

    private static final String SQL_VOLUMENES =
        "SELECT v.*, l.id_negocio AS lote_id_negocio "
            + "FROM lote_otros_volumenes v "
            + "JOIN lotes l ON v.lote_id = l.id "
            + "WHERE v.equipo_otros_id = ? ORDER BY v.id";

    /**
     * Las salidas de Lavadero que se derivaron a este equipo, con el ingreso de Lavadero de cada
     * una. Una salida de un equipo repartido en varios lavarropas no tiene {@code elemento_ciclo_id}
     * sino {@code instancia_equipo_id} ({@code V20}): se llega a la línea de clasificación por el
     * que no sea nulo.
     */
    private static final String SQL_SALIDAS_DERIVADAS =
        "SELECT s.*, ecl.ingreso_id AS ingreso_lavadero_id "
            + "FROM salidas_lavadero s "
            + "LEFT JOIN elementos_ciclo_lavadero ec ON s.elemento_ciclo_id = ec.id "
            + "LEFT JOIN instancias_equipo_ciclo ie ON s.instancia_equipo_id = ie.id "
            + "LEFT JOIN elementos_clasificacion_lavadero ecl "
            + "       ON ecl.id = COALESCE(ec.elemento_clasificacion_id, ie.elemento_clasificacion_id) "
            + "WHERE s.equipo_otros_id = ? ORDER BY s.id";

    // ── Las tres sentencias de bloquear(), en su orden ───────────────────────

    private static final String SQL_BLOQUEAR_SALIDAS =
        "SELECT id FROM salidas_lavadero WHERE equipo_otros_id = ? ORDER BY id FOR UPDATE";

    private static final String SQL_BLOQUEAR_MATERIALES =
        "SELECT id, lote_id FROM equipo_otros_materiales WHERE equipo_otros_id = ? ORDER BY id FOR UPDATE";

    private static final String SQL_BLOQUEAR_CABECERA =
        "SELECT version FROM equipo_otros WHERE id = ? FOR UPDATE";

    private static final String SQL_BORRAR =
        "DELETE FROM equipo_otros WHERE id = ? AND version = ?";

    private final ArchivoIngresosDAO archivo;

    public EliminadorEquipoOtros(ArchivoIngresosDAO archivo) {
        this.archivo = archivo;
    }

    /**
     * Lo que el diálogo de confirmación muestra. Lectura sin transacción: es informativa, y la
     * transacción re-verifica todo.
     *
     * <p>Un REMITO que todavía no se partió no tiene filas de material: se resume como una sola
     * línea con {@code remito_cantidad}, para que la confirmación no muestre un ingreso vacío.</p>
     *
     * @throws ResourceNotFoundException si el equipo ya no existe (otro lo eliminó)
     */
    public ResumenEquipo resumir(int equipoOtrosId) {
        try (Connection conn = ConnectionPool.getConnection()) {
            Cabecera cabecera = leerCabecera(conn, equipoOtrosId);
            List<ResumenEquipo.LineaMaterial> materiales = new ArrayList<>();
            Set<Integer> loteIds = new LinkedHashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(SQL_MATERIALES)) {
                ps.setInt(1, equipoOtrosId);
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
            if (materiales.isEmpty() && cabecera.remitoCantidad() != null) {
                materiales.add(new ResumenEquipo.LineaMaterial(
                    String.format(Constantes.Eliminacion.DESCRIPCION_REMITO_SIN_DETALLE, cabecera.remitoId()),
                    cabecera.remitoCantidad(), cabecera.estado(), null));
            }
            List<Bloqueo> bloqueos = List.copyOf(LotesEnCurso.entre(conn, loteIds));
            return new ResumenEquipo(new IngresoAEliminar(ModuloIngreso.OTROS, equipoOtrosId),
                cabecera.clienteNombre(), null, null, cabecera.fechaIngreso(), cabecera.estado(),
                cabecera.version(), materiales, bloqueos, ingresosLavaderoOrigen(conn, equipoOtrosId));
        } catch (SQLException e) {
            log.error("Error al resumir el equipo otros {} para eliminarlo", equipoOtrosId, e);
            throw new DatabaseException("Error al leer el equipo " + equipoOtrosId + " para eliminarlo", e);
        }
    }

    /**
     * La transacción propia, la de Ver Equipos: {@link #bloquear} (la cabecera tiene que existir)
     * → {@link #verificar} → la {@code version} tiene que ser la vista → {@link #archivarYBorrar} →
     * {@code commit}. El bloqueo se informa antes que la versión por la misma razón que en
     * {@code EliminadorEquipoOrtopedia.eliminar}: un lote nuevo bumpea la {@code version}, y "finalizá
     * el lote X" le sirve al operador más que "volvé a intentar".
     *
     * @param versionVista la {@code version} del {@link ResumenEquipo} que confirmó el operador
     * @throws ConflictoConcurrenciaException si el equipo cambió o desapareció desde el resumen, o
     *                                        si la base cortó la espera de un lock
     * @throws EliminacionBloqueadaException  si algún material está en un lote en curso
     * @throws DatabaseException              ante cualquier otro error de base; no se borró nada
     */
    public void eliminar(int equipoOtrosId, int versionVista, String motivo, String puesto) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();

            BloqueoEquipoOtros bloqueado = bloquear(conn, equipoOtrosId)
                .orElseThrow(() -> new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION));

            // El bloqueo antes que la versión: ver EliminadorEquipoOrtopedia.eliminar.
            List<Bloqueo> bloqueos = List.copyOf(verificar(conn, bloqueado));
            if (!bloqueos.isEmpty()) {
                throw new EliminacionBloqueadaException(bloqueos);
            }
            if (bloqueado.version() != versionVista) {
                throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION);
            }

            archivarYBorrar(conn, equipoOtrosId, versionVista, motivo, puesto, null);
            tx.commit();
            log.info("Equipo otros {} eliminado y archivado desde {}", equipoOtrosId, puesto);
        } catch (SQLException e) {
            if (ControlConcurrencia.esContencionDeLock(e)) {
                throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION, e);
            }
            log.error("Error al eliminar el equipo otros {}", equipoOtrosId, e);
            throw new DatabaseException("Error al eliminar el equipo " + equipoOtrosId, e);
        }
    }

    // ── Fases públicas: sobre una Connection ajena, sin abrir ni commitear nada ─

    /**
     * <b>Fase 1.</b> Toma {@code FOR UPDATE}, en este orden, todo lo que el borrado va a escribir:
     *
     * <ol start="0">
     *   <li><b>las {@code salidas_lavadero} que apuntan a este equipo</b>, por
     *       {@code equipo_otros_id}, en orden de {@code id};</li>
     *   <li>sus <b>materiales</b>, por {@code equipo_otros_id}, en orden de {@code id};</li>
     *   <li>la <b>cabecera</b>.</li>
     * </ol>
     *
     * <p>No hace ninguna lectura no bloqueante: quien la llama puede seguir tomando locks después
     * (el borrado de Lavadero bloquea así varios derivados) y recién entonces pasar a
     * {@link #verificar}.</p>
     *
     * <h2>Por qué este orden (H2 no lo reproduce; ningún test lo defiende)</h2>
     *
     * <p><b>(0) va primero porque el {@code DELETE} de la cabecera escribe
     * {@code salidas_lavadero}</b>: el {@code ON DELETE SET NULL} de la FK ({@code V17}) les pone
     * {@code equipo_otros_id = NULL} al final de la transacción, cuando ya se tienen los materiales
     * y la cabecera. El borrado de un ingreso de Lavadero llega a esas mismas salidas desde su lado
     * (salidas → derivados). Tomarlas acá primero deja el orden <b>salidas → materiales →
     * cabecera</b> en los dos borrados, y no se cruzan. Sin (0), el {@code SET NULL} sería un lock
     * tomado último sobre filas que el otro borrado toma primero.</p>
     *
     * <p><b>Materiales → cabecera</b> es el orden de {@code EquipoOtrosDAO.aplicarMovimientos} y
     * {@code entregar}, de {@code LoteDAO.lanzarLote} y de {@code finalizarLote}/
     * {@code marcarLoteFallo} sobre filas de DETALLES. Con ellos no se cruza. El {@code FOR UPDATE}
     * va por {@code equipo_otros_id} para tomar el rango de {@code idx_otros_mat_equipo} y frenar
     * también el {@code INSERT} de una fila partida.</p>
     *
     * <p><b>Los cruces que quedan, aceptados</b> (MySQL resuelve el deadlock abortando a uno, y si es
     * el borrado lo informa como conflicto):</p>
     * <ul>
     *   <li>Correcciones ({@code EquipoOtrosMaterialHelper.bumpVersionConGuarda}: cabecera →
     *       materiales), igual que en ortopedias;</li>
     *   <li><b>un REMITO sin partir</b>: {@code aplicarMovimientos} y {@code lanzarLote} toman
     *       primero la cabecera {@code FOR UPDATE} (no hay fila de material que bloquear) y después
     *       <em>insertan</em> las filas del split, un {@code INSERT} que choca con el rango que (1)
     *       tiene tomado. Pasa sólo si alguien avanza o lanza ese mismo remito en el instante en que
     *       otro lo borra.</li>
     * </ul>
     *
     * @return vacío si la cabecera ya no existe (otro la borró); quien llama decide qué hacer, y
     *         lo que se haya bloqueado se suelta con su transacción
     */
    public Optional<BloqueoEquipoOtros> bloquear(Connection conn, int equipoOtrosId) throws SQLException {
        List<Integer> salidaIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_BLOQUEAR_SALIDAS)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salidaIds.add(rs.getInt("id"));
                }
            }
        }

        Set<Integer> loteIds = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_BLOQUEAR_MATERIALES)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int loteId = rs.getInt("lote_id");
                    if (!rs.wasNull()) {
                        loteIds.add(loteId);
                    }
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(SQL_BLOQUEAR_CABECERA)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new BloqueoEquipoOtros(equipoOtrosId, rs.getInt("version"),
                    loteIds, salidaIds));
            }
        }
    }

    /**
     * <b>Fase 2.</b> Cuáles de los lotes de sus materiales siguen en curso.
     *
     * <p>Es una lectura <b>no bloqueante</b> de {@code lotes}, a propósito: ver {@link LotesEnCurso}
     * por qué no lleva {@code FOR UPDATE} y por qué alcanza. Bajo el {@code REPEATABLE READ} de
     * MySQL fija la vista de la transacción, así que quien la llama tiene que haber terminado de
     * bloquear <b>todo</b> antes (incluidos los demás derivados, en el borrado de Lavadero).</p>
     *
     * @return un {@link Bloqueo.LoteEnCurso} por lote en curso; el borrado de Lavadero los traduce
     *         a {@link Bloqueo.DerivadoEnLoteEnCurso}
     */
    public List<Bloqueo.LoteEnCurso> verificar(Connection conn, BloqueoEquipoOtros bloqueado)
            throws SQLException {
        return LotesEnCurso.entre(conn, bloqueado.loteIds());
    }

    /**
     * <b>Fase 3.</b> Lee el árbol completo del equipo, lo archiva y borra la cabecera con CAS de
     * {@code version}, todo sobre la conexión del llamador. El archivo va <b>antes</b> del
     * {@code DELETE} y en la misma transacción: si cualquiera de los dos falla, el llamador
     * revierte los dos.
     *
     * <p>Snapshot: cabecera con el nombre del cliente, materiales con el {@code id_negocio} de su
     * lote, movimientos, {@code lote_otros_volumenes} con el lote, y las salidas de Lavadero que
     * apuntaban acá con su ingreso de origen.</p>
     *
     * @param archivoPadreId la fila {@code LAVADERO} del archivo que arrastra a este equipo, o
     *                       {@code null} si se borra solo
     * @return el id de la fila de {@code ingresos_eliminados}
     * @throws ConflictoConcurrenciaException si la cabecera ya no tiene {@code versionEsperada}
     */
    public int archivarYBorrar(Connection conn, int equipoOtrosId, int versionEsperada, String motivo,
                               String puesto, Integer archivoPadreId) throws SQLException {
        int archivoId = archivo.archivar(conn,
            copiaParaArchivo(conn, equipoOtrosId, motivo, puesto, archivoPadreId));
        try (PreparedStatement ps = conn.prepareStatement(SQL_BORRAR)) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, versionEsperada);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Mensajes.CONFLICTO_ELIMINACION);
        }
        return archivoId;
    }

    // ── Privados ─────────────────────────────────────────────────────────────

    private static IngresoArchivado copiaParaArchivo(Connection conn, int equipoOtrosId, String motivo,
                                                     String puesto, Integer archivoPadreId)
            throws SQLException {
        JSONObject raiz = SnapshotJson.raiz(ModuloIngreso.OTROS, FORMATO_SNAPSHOT);
        String clienteNombre;
        LocalDateTime fechaIngreso;
        String estado;
        try (PreparedStatement ps = conn.prepareStatement(SQL_CABECERA)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Imposible con la cabecera bloqueada; si pasa, que no quede un archivo vacío.
                    throw new SQLException("equipo_otros " + equipoOtrosId + " desapareció con la fila bloqueada");
                }
                raiz.put("equipo", SnapshotJson.filaActual(rs));
                clienteNombre = rs.getString("cliente_nombre");
                fechaIngreso = aFecha(rs.getTimestamp("fecha_ingreso"));
                estado = EstadoEquipo.desdeBD(rs.getString("estado")).getNombre();
            }
        }
        raiz.put("materiales", filas(conn, SQL_MATERIALES, equipoOtrosId));
        raiz.put("movimientos", filas(conn, SQL_MOVIMIENTOS, equipoOtrosId));
        raiz.put("volumenes_lote", filas(conn, SQL_VOLUMENES, equipoOtrosId));
        raiz.put("salidas_lavadero", filas(conn, SQL_SALIDAS_DERIVADAS, equipoOtrosId));
        return new IngresoArchivado(ModuloIngreso.OTROS, equipoOtrosId, clienteNombre, fechaIngreso,
            estado, motivo, puesto, archivoPadreId, raiz.toString());
    }

    private static Cabecera leerCabecera(Connection conn, int equipoOtrosId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_CABECERA)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException(Mensajes.INGRESO_YA_ELIMINADO);
                }
                int cantidad = rs.getInt("remito_cantidad");
                Integer remitoCantidad = rs.wasNull() ? null : cantidad;
                return new Cabecera(rs.getString("cliente_nombre"), aFecha(rs.getTimestamp("fecha_ingreso")),
                    EstadoEquipo.desdeBD(rs.getString("estado")).getNombre(), rs.getInt("version"),
                    rs.getString("remito_id"), remitoCantidad);
            }
        }
    }

    private static List<Integer> ingresosLavaderoOrigen(Connection conn, int equipoOtrosId) throws SQLException {
        Set<Integer> ingresos = new TreeSet<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_SALIDAS_DERIVADAS)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ingresoId = rs.getInt("ingreso_lavadero_id");
                    if (!rs.wasNull()) {
                        ingresos.add(ingresoId);
                    }
                }
            }
        }
        return List.copyOf(ingresos);
    }

    private static JSONArray filas(Connection conn, String sql, int equipoOtrosId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                return SnapshotJson.filas(rs);
            }
        }
    }

    private static LocalDateTime aFecha(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** La cabecera tal como la necesita el resumen. {@code remitoCantidad} es nulo si no es REMITO. */
    private record Cabecera(String clienteNombre, LocalDateTime fechaIngreso, String estado, int version,
                            String remitoId, Integer remitoCantidad) {}
}
