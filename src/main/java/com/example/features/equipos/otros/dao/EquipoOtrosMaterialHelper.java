package com.example.features.equipos.otros.dao;

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

    /**
     * Materializa el split de un REMITO en {@code equipo_otros_materiales}.
     *
     * <p>Si {@code cantidadMover < remitoCantidad}, inserta una fila extra para los
     * elementos que no avanzan, manteniéndolos en {@code estadoActual} sin lote.
     * Siempre inserta la fila para los elementos que avanzan a {@code estadoDestino}
     * (con {@code loteId} y {@code volumenLote} opcionales) y registra el movimiento.
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
            Integer loteId,
            Integer volumenLote) throws SQLException {

        int cantidadEfectiva = Math.min(cantidadMover, remitoCantidad);

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
                "(equipo_otros_id, catalogo_otros_id, descripcion, cantidad, estado, lote_id, volumen_lote) " +
                "VALUES (?, ?, 'Elementos', ?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, equipoOtrosId);
            ps.setInt(2, catalogoId);
            ps.setInt(3, cantidadEfectiva);
            ps.setString(4, estadoDestino);
            if (loteId != null) ps.setInt(5, loteId);     else ps.setNull(5, Types.INTEGER);
            if (volumenLote != null) ps.setInt(6, volumenLote); else ps.setNull(6, Types.INTEGER);
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
