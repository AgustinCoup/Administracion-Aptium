package com.example.common.eliminacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * Escribe la copia de un ingreso en {@code ingresos_eliminados}.
 *
 * <p><b>Trabaja sobre la {@link Connection} del llamador, y es lo único que sabe hacer.</b> El
 * archivo va dentro de la transacción del borrado, antes del {@code DELETE}: esta fila es lo único
 * que queda del ingreso, así que un borrado sin archivo no puede existir, y un archivo de un
 * ingreso que sigue vivo tampoco. Por eso este DAO no abre, no commitea ni cierra nada, y propaga
 * {@link SQLException} para que el llamador revierta todo — mismo contrato que
 * {@code EquipoOtrosDAO.guardar(Connection, …)}.</p>
 *
 * <p>Es la diferencia con Correcciones, que audita en {@code equipos_eliminados} <b>después</b> del
 * {@code DELETE}, en otra conexión, y acepta que un fallo del registro deje un borrado sin
 * auditar. Acá eso no se acepta.</p>
 */
public class ArchivoIngresosDAO {

    private static final String SQL_ARCHIVAR =
        "INSERT INTO ingresos_eliminados "
            + "(modulo, ingreso_id_original, cliente_nombre, fecha_ingreso, estado, motivo, "
            + " puesto, archivo_padre_id, snapshot) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * @return el id de la fila de archivo, para usarlo como {@code archivoPadreId} de los
     *         ingresos que este arrastra
     * @throws SQLException si el {@code INSERT} falla; el llamador tiene que revertir el borrado
     */
    public int archivar(Connection conn, IngresoArchivado ingreso) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_ARCHIVAR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ingreso.modulo().name());
            ps.setInt(2, ingreso.ingresoIdOriginal());
            setStringONull(ps, 3, ingreso.clienteNombre());
            if (ingreso.fechaIngreso() != null) {
                ps.setTimestamp(4, Timestamp.valueOf(ingreso.fechaIngreso()));
            } else {
                ps.setNull(4, Types.TIMESTAMP);
            }
            setStringONull(ps, 5, ingreso.estado());
            ps.setString(6, ingreso.motivo());
            setStringONull(ps, 7, ingreso.puesto());
            if (ingreso.archivoPadreId() != null) {
                ps.setInt(8, ingreso.archivoPadreId());
            } else {
                ps.setNull(8, Types.INTEGER);
            }
            ps.setString(9, ingreso.snapshot());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("No se generó id para ingresos_eliminados");
                }
                return rs.getInt(1);
            }
        }
    }

    private static void setStringONull(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor != null) {
            ps.setString(indice, valor);
        } else {
            ps.setNull(indice, Types.VARCHAR);
        }
    }
}
