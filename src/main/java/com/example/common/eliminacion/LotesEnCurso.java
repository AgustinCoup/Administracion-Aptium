package com.example.common.eliminacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Cuáles de estos lotes siguen en curso ({@code fecha_fin IS NULL}): el bloqueo que comparten los
 * dos eliminadores del CDE, en el resumen y en la transacción.
 *
 * <p><b>Lee {@code lotes} SIN bloquear, a propósito. No agregar un {@code FOR UPDATE}.</b>
 * {@code LoteDAO.finalizarLote} y {@code marcarLoteFallo} bloquean {@code lotes} (el
 * {@code UPDATE} CAS de {@code fecha_fin}) y <em>después</em> los materiales del lote
 * {@code FOR UPDATE}. El borrado ya tiene tomados los materiales cuando llega acá, así que un
 * {@code FOR UPDATE} sobre {@code lotes} cruzaría los dos órdenes y daría un deadlock contra cada
 * finalización. Leer sin bloquear alcanza, y es conservador:</p>
 * <ul>
 *   <li>un lote que se está finalizando sin commitear se ve todavía en curso: el borrado se
 *       rechaza, y el operador vuelve a intentar un segundo después;</li>
 *   <li>un lote finalizado no se reabre nunca, así que "finalizado" no puede dejar de ser cierto
 *       entre esta lectura y el {@code commit};</li>
 *   <li>ningún material del ingreso puede <em>entrar</em> a un lote nuevo mientras el borrado tiene
 *       sus filas bloqueadas: {@code LoteDAO.lanzarLote} las toma {@code FOR UPDATE}.</li>
 * </ul>
 *
 * <p>Es una lectura <b>no bloqueante</b>: bajo el {@code REPEATABLE READ} de MySQL fija la vista de
 * la transacción. Quien la llama tiene que haber tomado todos sus {@code FOR UPDATE} antes. H2 no
 * lo delata (corre en {@code READ COMMITTED}).</p>
 */
public final class LotesEnCurso {

    private LotesEnCurso() {}

    /**
     * @param loteIds los {@code lote_id} no nulos de los materiales; vacío no consulta nada
     * @return un {@link Bloqueo.LoteEnCurso} por lote en curso, ordenados por {@code id_negocio}
     */
    public static List<Bloqueo.LoteEnCurso> entre(Connection conn, Collection<Integer> loteIds)
            throws SQLException {
        if (loteIds.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>(new TreeSet<>(loteIds));
        String sql = "SELECT id_negocio FROM lotes WHERE fecha_fin IS NULL AND id IN ("
            + String.join(", ", Collections.nCopies(ids.size(), "?")) + ") ORDER BY id_negocio";
        List<Bloqueo.LoteEnCurso> enCurso = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    enCurso.add(new Bloqueo.LoteEnCurso(rs.getString(1)));
                }
            }
        }
        return enCurso;
    }
}
