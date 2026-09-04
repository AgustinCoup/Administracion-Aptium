package com.example.features.equipos.otros.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers estáticos de DB para {@code equipo_otros_materiales}.
 * Análogo a {@link com.example.features.equipos.ortopedias.dao.EquipoMaterialHelper}.
 */
public final class EquipoOtrosMaterialHelper {

    private EquipoOtrosMaterialHelper() { }

    // ── recalcularEstadoEquipo ────────────────────────────────────────────────

    /**
     * Deriva el estado del equipo "otros" a partir del material más atrasado y persiste el
     * resultado en {@code equipo_otros}.
     *
     * <p>Debe llamarse dentro de una transacción activa.
     *
     * <p><b>Fuente única.</b> Hasta la unificación de este método, el cálculo estaba duplicado
     * en {@code EquipoOtrosDAO} y en {@code LoteDAO}, cada uno con su propia copia del
     * {@code CASE} de orden y su propio {@code UPDATE}. Los dos delegan acá ahora: es el único
     * lugar donde se escribe {@code equipo_otros.estado} por derivación, y por eso es también
     * el único lugar donde hace falta poner el mantenimiento de la columna {@code version}.
     * Análogo a {@link com.example.features.equipos.ortopedias.dao.EquipoMaterialHelper#recalcularEstadoEquipo}.
     *
     * <p>El {@code rs.getObject("orden_minimo") != null} es la variante defensiva de las dos que
     * había: sin materiales, el {@code MIN(...)} devuelve {@code NULL} y el chequeo evita un
     * {@code getInt} sobre {@code NULL}. El resultado es el mismo por otra vía — ningún
     * {@code EstadoEquipo} tiene orden 0, así que el {@code getInt} de la variante laxa tampoco
     * matcheaba y el estado quedaba en {@code NUEVO} — pero acá la intención está escrita en
     * vez de depender de esa coincidencia.
     *
     * <h2>Mantenimiento de la columna {@code version} (V21)</h2>
     *
     * <p>Acá se incrementa el token de bloqueo optimista del agregado, por el mismo motivo que en
     * el helper de ortopedias: toda ruta que muta materiales termina llamando a este recálculo, así
     * que la cobertura sale de una línea en vez de veinte {@code UPDATE} sueltos. <b>La columna se
     * mantiene pero NO se usa como guarda en ningún {@code WHERE}</b> — ver
     * {@link com.example.features.equipos.ortopedias.dao.EquipoMaterialHelper#recalcularEstadoEquipo}.
     *
     * <p><b>Auditoría de las rutas que escriben los agregados sin pasar por acá</b>
     * (2026-09-03, {@code grep "UPDATE equipo" -r src/main/java}). Una ruta que escribe sin
     * bumpear da un falso negativo silencioso, que es justo lo que el bloqueo viene a eliminar,
     * así que ninguna queda implícita:
     *
     * <ul>
     *   <li><b>{@code LoteDAO.acumularVolumenEquipoOtros} — {@code SET volumen_equipo}: cubierta.</b>
     *       Su único llamador ({@code LoteDAO:319}) corre inmediatamente después de
     *       {@code procesarEquiposOtrosAfectados} ({@code :318}), sobre el mismo conjunto de
     *       equipos y dentro de la misma transacción. Ojo: el recálculo corre <em>antes</em>, no
     *       después — lo que la cubre no es el orden sino la atomicidad, porque un lector
     *       concurrente ve la transacción entera o ninguna parte de ella.</li>
     *   <li><b>{@code EquipoOtrosDAO:162} — {@code SET remito_id}: no invalida ningún snapshot.</b>
     *       Corre dentro de {@code guardar}, en la misma transacción que el {@code INSERT} que
     *       crea la fila: nadie puede tener un snapshot de un equipo que todavía no existía.</li>
     *   <li><b>{@code EquipoDAO.actualizar} — {@code SET estado}: bumpea explícitamente.</b> Escribe
     *       el estado de la cabecera sin derivarlo, así que sí invalida un snapshot. Hoy no tiene
     *       llamador de producción (sólo implementa {@code DAO<T,ID>}), pero el bump va igual para
     *       que quien lo cablee mañana no herede un agujero.</li>
     *   <li><b>{@code EquipoOtrosDAO:338} — {@code SET estado} del camino REMITO sin materiales
     *       reales:</b> asignada al Paso 4 del plan, que la hace bumpear a mano — no hay materiales
     *       que recalcular, así que este helper no aplica.</li>
     *   <li><b>{@code FusionClientesDAO} — {@code SET nro_cliente}: cubierta.</b> Ya no es
     *       excepción: bumpea la {@code version} de los equipos que mueve, dentro de la misma
     *       transacción que verifica los nombres vigentes de origen y destino.</li>
     *   <li><b>Rutas de {@code Correcciones} ({@code actualizarCantidadRemito},
     *       {@code actualizarCantidadMaterial}, {@code insertarMaterial},
     *       {@code eliminarMaterialesPorDescripcion}, {@code eliminarEquipo}, y en ortopedias
     *       {@code MaterialDAO.actualizarCantidad} / {@code actualizarCodigo} /
     *       {@code agregarMaterial} / {@code eliminarMaterialesPorCodigo} /
     *       {@code EquipoDAO.eliminarConVersion}):</b> dejaron de ser escrituras ciegas. Además de
     *       bumpear, la {@code version} es ahora la <b>guarda</b>: la que el operador tenía a la
     *       vista viaja hasta el {@code WHERE}.</li>
     * </ul>
     *
     * <p>Esas rutas de Correcciones no pueden pasar por este recálculo: deriva {@code estado} desde
     * los materiales, y sobre un REMITO sin materiales reales pisaría la cabecera con
     * {@code NUEVO}. Tampoco son inocuas: son exactamente las escrituras que reemplazan lo que un
     * snapshot de Correcciones mostraba, y por eso son las únicas donde la {@code version} del
     * agregado sirve de guarda — el falso positivo que este helper descarta más arriba (dos
     * operadores avanzando materiales distintos del mismo equipo) se acepta a propósito acá, porque
     * Correcciones es de uso esporádico y auditado. Por eso llevan bump a mano, guardado.
     *
     * @param conn          conexión activa con {@code autoCommit=false}
     * @param equipoOtrosId ID del equipo "otros" a recalcular
     */
    public static void recalcularEstadoEquipo(Connection conn, int equipoOtrosId) throws SQLException {
        String sqlCalcularEstado =
            "SELECT MIN(CASE " +
            "  WHEN estado='Nuevo'         THEN 1 " +
            "  WHEN estado='Lavando'       THEN 2 " +
            "  WHEN estado='Lavado'        THEN 3 " +
            "  WHEN estado='Empaquetado'   THEN 4 " +
            "  WHEN estado='Esterilizando' THEN 5 " +
            "  WHEN estado='Esterilizado'  THEN 6 " +
            "  WHEN estado='Entregado'     THEN 7 " +
            "  ELSE 1 END) AS orden_minimo " +
            "FROM equipo_otros_materiales WHERE equipo_otros_id = ?";

        EstadoEquipo nuevoEstado = EstadoEquipo.NUEVO;
        try (PreparedStatement ps = conn.prepareStatement(sqlCalcularEstado)) {
            ps.setInt(1, equipoOtrosId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getObject("orden_minimo") != null) {
                    int ordenMinimo = rs.getInt("orden_minimo");
                    for (EstadoEquipo estado : EstadoEquipo.values()) {
                        if (estado.getOrden() == ordenMinimo) {
                            nuevoEstado = estado;
                            break;
                        }
                    }
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE equipo_otros SET estado = ?, version = version + 1 WHERE id = ?")) {
            ps.setString(1, nuevoEstado.getNombre());
            ps.setInt   (2, equipoOtrosId);
            ps.executeUpdate();
        }
    }

    /**
     * Incrementa la {@code version} del agregado sin tocar ninguna otra columna, sin comparar
     * contra un valor esperado.
     *
     * <p>Para las rutas que mutan {@code equipo_otros_materiales} <b>sin</b> pasar por
     * {@link #recalcularEstadoEquipo} — las de {@code Correcciones}. No pueden usar el recálculo
     * porque deriva {@code estado} desde los materiales y sobre un REMITO sin materiales reales
     * pisaría la cabecera con {@code NUEVO}. Para esas rutas, {@link #bumpVersionConGuarda} es la
     * variante que corresponde: además de mantener el token, lo usa como guarda de bloqueo
     * optimista contra la {@code version} que el operador tenía a la vista.
     *
     * <p>Llamar dentro de la misma transacción que la escritura que lo motiva, para que el bump
     * y el cambio se vean o no se vean juntos.
     *
     * @param conn          conexión activa con {@code autoCommit=false}
     * @param equipoOtrosId ID del equipo "otros" cuyo token se invalida
     */
    public static void bumpVersion(Connection conn, int equipoOtrosId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE equipo_otros SET version = version + 1 WHERE id = ?")) {
            ps.setInt(1, equipoOtrosId);
            ps.executeUpdate();
        }
    }

    /**
     * Incrementa la {@code version} del agregado, guardada: sólo si sigue valiendo lo que el
     * operador tenía a la vista.
     *
     * <p>CAS de una sola sentencia sobre {@code equipo_otros} — no {@code equipos}, la tabla del
     * helper análogo de ortopedias. {@code 0} filas afectadas significa que otro operador tocó
     * el equipo desde que la pantalla lo leyó — {@link ControlConcurrencia#exigirFilaAfectada}
     * revierte la transacción entera con {@link com.example.common.exception.ConflictoConcurrenciaException}.
     *
     * <p>Va <b>primero</b>, antes de tocar el detalle: toma el lock de la fila de
     * {@code equipo_otros} al principio, así que un segundo operador se bloquea ahí y no hace
     * trabajo que va a descartar.
     *
     * <p>Llamar dentro de la misma transacción que la escritura que lo motiva.
     *
     * @param conn            conexión activa con {@code autoCommit=false}
     * @param equipoOtrosId   ID del equipo "otros" cuyo token se invalida
     * @param versionEsperada la {@code version} que el operador tenía a la vista
     * @throws com.example.common.exception.ConflictoConcurrenciaException si la fila ya no tiene esa version
     */
    public static void bumpVersionConGuarda(Connection conn, int equipoOtrosId, int versionEsperada)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE equipo_otros SET version = version + 1 WHERE id = ? AND version = ?")) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, versionEsperada);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Constantes.Mensajes.CONFLICTO_CORRECCION);
        }
    }

    /**
     * Materializa el split de un REMITO en {@code equipo_otros_materiales}.
     *
     * <p>Si {@code cantidadMover < remitoCantidad}, inserta una fila extra para los
     * elementos que no avanzan, manteniéndolos en {@code estadoActual} sin lote.
     * Siempre inserta la fila para los elementos que avanzan a {@code estadoDestino}
     * (con {@code loteId} opcional) y registra el movimiento.
     *
     * @return ID de la fila avanzada en {@code equipo_otros_materiales}
     */
    public static int materializarRemitoSplit(
            Connection conn,
            int equipoOtrosId,
            int catalogoId,
            int remitoCantidad,
            String estadoActual,
            int cantidadMover,
            String estadoDestino,
            Integer loteId) throws SQLException {

        if (cantidadMover <= 0)
            throw new SQLException("cantidadMover debe ser mayor que cero: " + cantidadMover);
        if (cantidadMover > remitoCantidad)
            throw new SQLException("cantidadMover (" + cantidadMover +
                ") supera remitoCantidad (" + remitoCantidad + ")");

        int cantidadEfectiva = cantidadMover;

        // Fila para los elementos que no avanzan (sin lote)
        if (cantidadEfectiva < remitoCantidad) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO equipo_otros_materiales " +
                    "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado) " +
                    "VALUES (?, ?, 'Elementos', ?, ?)")) {
                ps.setInt(1, equipoOtrosId);
                ps.setInt(2, catalogoId);
                ps.setInt(3, remitoCantidad - cantidadEfectiva);
                ps.setString(4, estadoActual);
                ps.executeUpdate();
            }
        }

        // Fila para los elementos que avanzan de estado
        int movedId;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO equipo_otros_materiales " +
                "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado, lote_id) " +
                "VALUES (?, ?, 'Elementos', ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, catalogoId);
            ps.setInt(3, cantidadEfectiva);
            ps.setString(4, estadoDestino);
            if (loteId != null) ps.setInt(5, loteId);     else ps.setNull(5, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se generó ID al materializar REMITO split");
                movedId = rs.getInt(1);
            }
        }

        // Registrar movimiento para los elementos avanzados
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO otros_material_movimientos " +
                "(material_id, equipo_otros_id, cantidad, estado_origen, estado_destino) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, movedId);
            ps.setInt(2, equipoOtrosId);
            ps.setInt(3, cantidadEfectiva);
            if (estadoActual != null) ps.setString(4, estadoActual); else ps.setNull(4, Types.VARCHAR);
            ps.setString(5, estadoDestino);
            ps.executeUpdate();
        }

        return movedId;
    }

    /**
     * Unifica filas de {@code equipo_otros_materiales} con la misma
     * {@code (equipo_otros_id, descripcion, estado)}.
     * La fila superviviente es la de movimiento más reciente; el resto se elimina
     * tras reasignar sus movimientos al superviviente.
     */
    public static void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        // lote_id MUST be part of the grouping key: rows belonging to different lotes must never be merged.
        String sqlGrupos =
            "SELECT descripcion, estado, lote_id, SUM(cantidad) AS cantidad_total " +
            "FROM equipo_otros_materiales " +
            "WHERE equipo_otros_id = ? " +
            "GROUP BY descripcion, estado, lote_id " +
            "HAVING COUNT(*) > 1";

        List<Object[]> grupos = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sqlGrupos)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int loteIdVal = rs.getInt("lote_id");
                    Integer loteIdObj = rs.wasNull() ? null : loteIdVal;
                    grupos.add(new Object[]{
                        rs.getString("descripcion"),
                        rs.getString("estado"),
                        loteIdObj,
                        rs.getInt("cantidad_total")
                    });
                }
            }
        }

        for (Object[] g : grupos) {
            String  desc          = (String)  g[0];
            String  estado        = (String)  g[1];
            Integer loteId        = (Integer) g[2]; // null for rows not yet assigned to a lote
            int     cantidadTotal = (int)     g[3];

            String loteFilterSup  = loteId == null ? "m.lote_id IS NULL"  : "m.lote_id = ?";
            String loteFilterElim = loteId == null ? "lote_id IS NULL"     : "lote_id = ?";

            String sqlSup =
                "SELECT m.id FROM equipo_otros_materiales m " +
                "LEFT JOIN (" +
                "  SELECT material_id, MAX(fecha) AS uf " +
                "  FROM otros_material_movimientos GROUP BY material_id" +
                ") mv ON m.id = mv.material_id " +
                "WHERE m.equipo_otros_id = ? AND m.descripcion = ? AND m.estado = ? " +
                "AND " + loteFilterSup + " " +
                "ORDER BY mv.uf DESC, m.id DESC LIMIT 1";

            int supervivienteId;
            try (PreparedStatement ps = conn.prepareStatement(sqlSup)) {
                ps.setInt(1, equipoId);
                ps.setString(2, desc);
                ps.setString(3, estado);
                if (loteId != null) ps.setInt(4, loteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) continue;
                    supervivienteId = rs.getInt("id");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE equipo_otros_materiales SET cantidad = ? WHERE id = ?")) {
                ps.setInt(1, cantidadTotal);
                ps.setInt(2, supervivienteId);
                ps.executeUpdate();
            }

            List<Integer> idsEliminar = new ArrayList<>();
            String sqlElim =
                "SELECT id FROM equipo_otros_materiales " +
                "WHERE equipo_otros_id = ? AND descripcion = ? AND estado = ? " +
                "AND " + loteFilterElim + " AND id <> ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlElim)) {
                ps.setInt(1, equipoId);
                ps.setString(2, desc);
                ps.setString(3, estado);
                if (loteId != null) {
                    ps.setInt(4, loteId);
                    ps.setInt(5, supervivienteId);
                } else {
                    ps.setInt(4, supervivienteId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) idsEliminar.add(rs.getInt("id"));
                }
            }
            if (idsEliminar.isEmpty()) continue;

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE otros_material_movimientos SET material_id = ? WHERE material_id = ?")) {
                for (int idElim : idsEliminar) {
                    ps.setInt(1, supervivienteId);
                    ps.setInt(2, idElim);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM equipo_otros_materiales WHERE id = ?")) {
                for (int idElim : idsEliminar) {
                    ps.setInt(1, idElim);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }
}
