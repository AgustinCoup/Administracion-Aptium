package com.example.features.clientes.dao;

import com.example.common.constants.Constantes;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.infrastructure.db.TransactionalConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class FusionClientesDAO {

    /**
     * {@code nombreOrigen}/{@code nombreDestino} son los que el operador vio en los dos combos
     * del diálogo de fusión: sirven de guarda contra que alguien haya renombrado alguno de los
     * dos clientes mientras el diálogo estaba abierto.
     */
    public void fusionar(int idOrigen, String nombreOrigen, int idDestino, String nombreDestino) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            verificarNombresVigentes(conn, idOrigen, nombreOrigen, idDestino, nombreDestino);

            // No llevan exigirFilaAfectada: 0 filas es legítimo, un cliente sin equipos se
            // fusiona igual. El bump de version es necesario porque la fusión cambia el
            // nro_cliente que la guarda de Correcciones protege.
            ejecutarUpdate(tx, "UPDATE equipos SET nro_cliente = ?, version = version + 1 WHERE nro_cliente = ?", idDestino, idOrigen);
            ejecutarUpdate(tx, "UPDATE equipo_otros SET nro_cliente = ?, version = version + 1 WHERE nro_cliente = ?", idDestino, idOrigen);
            ejecutarUpdate(tx, "DELETE FROM clientes WHERE id = ?", idOrigen);
            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al fusionar clientes " + idOrigen + " → " + idDestino, e);
        }
    }

    /**
     * Primera sentencia de la transacción de fusión: fija el snapshot con {@code FOR UPDATE} y lo
     * compara contra los nombres que el operador vio en los combos.
     *
     * <p>Acá el {@code FOR UPDATE} sí sirve, a diferencia de las guardas de Correcciones: la
     * lectura y la escritura comparten esta misma transacción, así que el lock vive hasta el
     * commit. {@code IN (?, ?)} bloquea en orden de índice, no en el orden de los parámetros: dos
     * fusiones cruzadas (A→B y B→A) toman los locks en el mismo orden y no se traban entre sí.
     *
     * <p>Que hayan aparecido equipos nuevos a nombre del origen entre la lectura del operador y
     * este momento no es un conflicto: mover esos equipos es justamente lo que la fusión quiere
     * hacer. Lo único que se verifica es la identidad de los dos clientes.
     */
    private void verificarNombresVigentes(Connection conn, int idOrigen, String nombreOrigen,
                                           int idDestino, String nombreDestino) throws SQLException {
        Map<Integer, String> nombresActuales = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, nombre FROM clientes WHERE id IN (?, ?) FOR UPDATE")) {
            ps.setInt(1, idOrigen);
            ps.setInt(2, idDestino);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nombresActuales.put(rs.getInt("id"), rs.getString("nombre"));
                }
            }
        }
        boolean vigente = nombreOrigen.equals(nombresActuales.get(idOrigen))
            && nombreDestino.equals(nombresActuales.get(idDestino));
        if (!vigente) {
            throw new ConflictoConcurrenciaException(Constantes.Mensajes.CONFLICTO_CLIENTE);
        }
    }

    private void ejecutarUpdate(TransactionalConnection tx, String sql, int... params) throws SQLException {
        try (PreparedStatement ps = tx.get().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
