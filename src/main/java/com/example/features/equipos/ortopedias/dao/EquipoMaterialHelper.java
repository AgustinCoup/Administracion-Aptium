package com.example.features.equipos.ortopedias.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Operaciones de infraestructura compartidas entre {@link MaterialDAO} y
 * {@code LoteDAO} que actúan sobre las tablas {@code equipo_materiales} y
 * {@code equipos} dentro de una transacción ya abierta.
 *
 * <p>Todos los métodos reciben la {@link Connection} activa; el manejo de
 * commit/rollback es responsabilidad exclusiva del llamador.
 *
 * <p>La clase no tiene estado propio y no puede instanciarse.
 */
public final class EquipoMaterialHelper {

    private static final Logger log = LoggerFactory.getLogger(EquipoMaterialHelper.class);

    private EquipoMaterialHelper() { }

    // ── recalcularEstadoEquipo ────────────────────────────────────────────────

    /**
     * Deriva el estado del equipo a partir del material más atrasado y
     * persiste el resultado en la tabla {@code equipos}.
     *
     * <p>Debe llamarse dentro de una transacción activa.
     *
     * <p><b>Acá se mantiene la columna {@code version}</b> (V21), el token de bloqueo optimista
     * del agregado. Va en este único lugar y no en cada {@code UPDATE} desperdigado porque toda
     * ruta que muta materiales de un equipo termina llamando a este recálculo: la cobertura sale
     * de una línea en vez de veinte que alguien va a olvidar de tocar el día que agregue una ruta
     * nueva. Las excepciones auditadas están listadas abajo.
     *
     * <p><b>La {@code version} se mantiene pero NO se usa como guarda en ningún {@code WHERE} de
     * este helper ni de Registrar Estado.</b> Es deliberado: guardar con la version del agregado
     * haría chocar a dos operadores que avanzan materiales <em>distintos</em> del mismo equipo, un
     * falso positivo que enseña al operador a ignorar el cartel. La protección real de esos flujos
     * es la guarda sobre el {@code estado} de cada material, que es precisa. El consumidor
     * previsto de la columna es {@code Correcciones}, que reemplaza la fila entera desde un
     * snapshot — <b>y ahí sí es guarda</b>: el falso positivo que acá se descarta se acepta a
     * propósito en Correcciones, por ser una pantalla de uso esporádico y auditado (ver el javadoc
     * de {@link com.example.features.equipos.otros.dao.EquipoOtrosMaterialHelper#recalcularEstadoEquipo},
     * que lleva la auditoría completa de rutas).
     *
     * @param conn      conexión activa con {@code autoCommit=false}
     * @param equipoId  ID del equipo a recalcular
     */
    public static void recalcularEstadoEquipo(Connection conn, int equipoId) throws SQLException {
        String sqlCalcularEstado =
            "SELECT MIN(CASE " +
            "  WHEN estado = 'Nuevo'         THEN 1 " +
            "  WHEN estado = 'Lavando'       THEN 2 " +
            "  WHEN estado = 'Lavado'        THEN 3 " +
            "  WHEN estado = 'Empaquetado'   THEN 4 " +
            "  WHEN estado = 'Esterilizando' THEN 5 " +
            "  WHEN estado = 'Esterilizado'  THEN 6 " +
            "  WHEN estado = 'Entregado'     THEN 7 " +
            "  ELSE 1 END) AS orden_minimo " +
            "FROM equipo_materiales WHERE equipo_id = ?";

        EstadoEquipo estadoEquipo = EstadoEquipo.NUEVO;
        try (PreparedStatement pstmt = conn.prepareStatement(sqlCalcularEstado)) {
            pstmt.setInt(1, equipoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int ordenMinimo = rs.getInt("orden_minimo");
                    for (EstadoEquipo estado : EstadoEquipo.values()) {
                        if (estado.getOrden() == ordenMinimo) {
                            estadoEquipo = estado;
                            break;
                        }
                    }
                }
            }
        }

        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE equipos SET estado = ?, version = version + 1 WHERE id = ?")) {
            pstmt.setString(1, estadoEquipo.getNombre());
            pstmt.setInt(2, equipoId);
            pstmt.executeUpdate();
        }
    }

    // ── bumpVersion ───────────────────────────────────────────────────────────

    /**
     * Incrementa la {@code version} del agregado sin tocar ninguna otra columna, sin comparar
     * contra un valor esperado.
     *
     * <p>Las rutas de {@code Correcciones}, que mutan {@code equipo_materiales} sin pasar por
     * {@link #recalcularEstadoEquipo}, usan en cambio {@link #bumpVersionConGuarda}: además de
     * mantener el token, lo usan como guarda de bloqueo optimista contra la {@code version} que
     * el operador tenía a la vista.
     *
     * <p>Llamar dentro de la misma transacción que la escritura que lo motiva.
     *
     * @param conn     conexión activa con {@code autoCommit=false}
     * @param equipoId ID del equipo cuyo token se invalida
     */
    public static void bumpVersion(Connection conn, int equipoId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE equipos SET version = version + 1 WHERE id = ?")) {
            pstmt.setInt(1, equipoId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Incrementa la {@code version} del agregado, guardada: sólo si sigue valiendo lo que el
     * operador tenía a la vista.
     *
     * <p>CAS de una sola sentencia. {@code 0} filas afectadas significa que otro operador tocó
     * el equipo desde que la pantalla lo leyó — {@link ControlConcurrencia#exigirFilaAfectada}
     * revierte la transacción entera con {@link com.example.common.exception.ConflictoConcurrenciaException}.
     *
     * <p>Va <b>primero</b>, antes de tocar el detalle: toma el lock de la fila de {@code equipos}
     * al principio, así que un segundo operador se bloquea ahí y no hace trabajo que va a
     * descartar.
     *
     * <p>Llamar dentro de la misma transacción que la escritura que lo motiva.
     *
     * @param conn            conexión activa con {@code autoCommit=false}
     * @param equipoId        ID del equipo cuyo token se invalida
     * @param versionEsperada la {@code version} que el operador tenía a la vista
     * @throws com.example.common.exception.ConflictoConcurrenciaException si la fila ya no tiene esa version
     */
    public static void bumpVersionConGuarda(Connection conn, int equipoId, int versionEsperada)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE equipos SET version = version + 1 WHERE id = ? AND version = ?")) {
            ps.setInt(1, equipoId);
            ps.setInt(2, versionEsperada);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Constantes.Mensajes.CONFLICTO_CORRECCION);
        }
    }

    // ── unificarMaterialesDuplicados ─────────────────────────────────────────

    /**
     * Unifica filas de {@code equipo_materiales} con el mismo
     * {@code (equipo_id, codigo_catalogo, estado)}.
     *
     * <p>La fila superviviente es la de movimiento más reciente; en caso de
     * empate, la de mayor {@code id}. Los movimientos de las filas eliminadas
     * se reasignan al superviviente para preservar el historial de trazabilidad.
     *
     * <p>Debe llamarse dentro de una transacción activa.
     *
     * @param conn      conexión activa con {@code autoCommit=false}
     * @param equipoId  ID del equipo a procesar
     */
    public static void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        // lote_id MUST be part of the grouping key: rows belonging to different lotes must never be merged.
        String sqlGrupos =
            "SELECT codigo_catalogo, estado, lote_id, SUM(cantidad) AS cantidad_total " +
            "FROM equipo_materiales " +
            "WHERE equipo_id = ? " +
            "GROUP BY codigo_catalogo, estado, lote_id " +
            "HAVING COUNT(*) > 1";

        List<Object[]> grupos = new ArrayList<>();

        try (PreparedStatement pstmt = conn.prepareStatement(sqlGrupos)) {
            pstmt.setInt(1, equipoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int loteIdVal = rs.getInt("lote_id");
                    Integer loteIdObj = rs.wasNull() ? null : loteIdVal;
                    grupos.add(new Object[]{
                        rs.getInt("codigo_catalogo"),
                        rs.getString("estado"),
                        loteIdObj,
                        rs.getInt("cantidad_total")
                    });
                }
            }
        }

        for (Object[] g : grupos) {
            unificarGrupo(conn, equipoId,
                (int) g[0], (String) g[1], (Integer) g[2], (int) g[3]);
        }
    }

    // ── unificarGrupo (privado) ───────────────────────────────────────────────

    private static void unificarGrupo(Connection conn, int equipoId, int codigo,
                                      String estado, Integer loteId, int cantidadTotal)
            throws SQLException {
        String loteFilterSup  = loteId == null ? "em.lote_id IS NULL"  : "em.lote_id = ?";
        String loteFilterElim = loteId == null ? "lote_id IS NULL"      : "lote_id = ?";

        String sqlSuperviviente =
            "SELECT em.id " +
            "FROM equipo_materiales em " +
            "LEFT JOIN (" +
            "  SELECT material_id, MAX(fecha) AS ultima_fecha " +
            "  FROM material_movimientos GROUP BY material_id" +
            ") mm ON em.id = mm.material_id " +
            "WHERE em.equipo_id = ? AND em.codigo_catalogo = ? AND em.estado = ? " +
            "AND " + loteFilterSup + " " +
            "ORDER BY mm.ultima_fecha DESC, em.id DESC " +
            "LIMIT 1";

        int supervivienteId;
        try (PreparedStatement pstmt = conn.prepareStatement(sqlSuperviviente)) {
            pstmt.setInt(1, equipoId);
            pstmt.setInt(2, codigo);
            pstmt.setString(3, estado);
            if (loteId != null) pstmt.setInt(4, loteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) return;
                supervivienteId = rs.getInt("id");
            }
        }

        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE equipo_materiales SET cantidad = ? WHERE id = ?")) {
            pstmt.setInt(1, cantidadTotal);
            pstmt.setInt(2, supervivienteId);
            pstmt.executeUpdate();
        }

        List<Integer> idsAEliminar = new ArrayList<>();
        String sqlElim =
            "SELECT id FROM equipo_materiales " +
            "WHERE equipo_id = ? AND codigo_catalogo = ? AND estado = ? " +
            "AND " + loteFilterElim + " AND id <> ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlElim)) {
            pstmt.setInt(1, equipoId);
            pstmt.setInt(2, codigo);
            pstmt.setString(3, estado);
            if (loteId != null) {
                pstmt.setInt(4, loteId);
                pstmt.setInt(5, supervivienteId);
            } else {
                pstmt.setInt(4, supervivienteId);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) idsAEliminar.add(rs.getInt("id"));
            }
        }

        if (idsAEliminar.isEmpty()) return;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE material_movimientos SET material_id = ? WHERE material_id = ?")) {
            for (int idEliminar : idsAEliminar) {
                pstmt.setInt(1, supervivienteId);
                pstmt.setInt(2, idEliminar);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM equipo_materiales WHERE id = ?")) {
            for (int idEliminar : idsAEliminar) {
                pstmt.setInt(1, idEliminar);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        log.debug("Unificados {} registros: código={} estado={} lote={} equipo={} → superviviente={}",
            idsAEliminar.size() + 1, codigo, estado, loteId, equipoId, supervivienteId);
    }
}