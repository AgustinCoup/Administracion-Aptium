package com.example.features.equipos.otros.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers estáticos de DB para {@code equipo_otros_materiales}.
 * Análogo a {@link com.example.features.equipos.ortopedias.dao.EquipoMaterialHelper}.
 */
public final class EquipoOtrosMaterialHelper {

    private EquipoOtrosMaterialHelper() { }

    /**
     * Unifica filas de {@code equipo_otros_materiales} con la misma
     * {@code (equipo_otros_id, descripcion, estado)}.
     * La fila superviviente es la de movimiento más reciente; el resto se elimina
     * tras reasignar sus movimientos al superviviente.
     */
    public static void unificarMaterialesDuplicados(Connection conn, int equipoId) throws SQLException {
        String sqlGrupos =
            "SELECT descripcion, estado, SUM(cantidad) AS cantidad_total " +
            "FROM equipo_otros_materiales " +
            "WHERE equipo_otros_id = ? " +
            "GROUP BY descripcion, estado " +
            "HAVING COUNT(*) > 1";

        List<Object[]> grupos = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sqlGrupos)) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    grupos.add(new Object[]{
                        rs.getString("descripcion"),
                        rs.getString("estado"),
                        rs.getInt("cantidad_total")
                    });
                }
            }
        }

        for (Object[] g : grupos) {
            String desc       = (String) g[0];
            String estado     = (String) g[1];
            int cantidadTotal = (int)    g[2];

            String sqlSup =
                "SELECT m.id FROM equipo_otros_materiales m " +
                "LEFT JOIN (" +
                "  SELECT material_id, MAX(fecha) AS uf " +
                "  FROM otros_material_movimientos GROUP BY material_id" +
                ") mv ON m.id = mv.material_id " +
                "WHERE m.equipo_otros_id = ? AND m.descripcion = ? AND m.estado = ? " +
                "ORDER BY mv.uf DESC, m.id DESC LIMIT 1";

            int supervivienteId;
            try (PreparedStatement ps = conn.prepareStatement(sqlSup)) {
                ps.setInt(1, equipoId);
                ps.setString(2, desc);
                ps.setString(3, estado);
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
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM equipo_otros_materiales " +
                    "WHERE equipo_otros_id = ? AND descripcion = ? AND estado = ? AND id <> ?")) {
                ps.setInt(1, equipoId);
                ps.setString(2, desc);
                ps.setString(3, estado);
                ps.setInt(4, supervivienteId);
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
