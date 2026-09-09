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
     * Una tabla que apunta a {@code clientes} y que la fusión tiene que mover.
     *
     * @param bumpeaVersion si la tabla lleva columna {@code version}. La fusión cambia el cliente,
     *                      que es justamente lo que la guarda de Correcciones protege, así que
     *                      moverlo sin bumpear dejaría pasar una corrección armada sobre el
     *                      cliente viejo. Sólo {@code equipos} y {@code equipo_otros} la tienen
     *                      (V21); {@code ingresos_lavadero} no —su guarda natural es la máquina de
     *                      estados persistida— y por eso va en {@code false}.
     */
    private record ReferenciaACliente(String tabla, String columna, boolean bumpeaVersion) {

        private String sqlDeMudanza() {
            return "UPDATE " + tabla + " SET " + columna + " = ?"
                + (bumpeaVersion ? ", version = version + 1" : "")
                + " WHERE " + columna + " = ?";
        }
    }

    /**
     * <b>Todas</b> las tablas con FK a {@code clientes}. Es una lista y no tres sentencias sueltas
     * porque el error que este código invita a cometer es <b>por omisión</b>: la FK de
     * {@code ingresos_lavadero} llegó con la rama de Lavadero y esta clase no se enteró, así que
     * fusionar un cliente que alguna vez mandó ropa fallaba con un {@code DatabaseException}
     * —todas las FK son {@code ON DELETE RESTRICT}, y el {@code DELETE FROM clientes} de abajo
     * choca contra ellas.
     *
     * <p>Que sea un dato y no código suelto es lo que permite que
     * {@code FusionClientesDAOTest.todaTablaConFkAClientes_estaContempladaEnLaFusion} lo compare
     * contra el {@code INFORMATION_SCHEMA} y falle sola cuando alguien agregue la cuarta. Agregar
     * acá una fila es más barato que acordarse.</p>
     */
    private static final java.util.List<ReferenciaACliente> REFERENCIAS = java.util.List.of(
        new ReferenciaACliente("equipos",           "nro_cliente", true),
        new ReferenciaACliente("equipo_otros",      "nro_cliente", true),
        new ReferenciaACliente("ingresos_lavadero", "cliente_id",  false));

    /** Lo que el test de cobertura compara contra el esquema: {@code tabla → columna de FK}. */
    static Map<String, String> tablasQueLaFusionMueve() {
        Map<String, String> porTabla = new HashMap<>();
        REFERENCIAS.forEach(r -> porTabla.put(r.tabla(), r.columna()));
        return porTabla;
    }

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
            for (ReferenciaACliente referencia : REFERENCIAS) {
                ejecutarUpdate(tx, referencia.sqlDeMudanza(), idDestino, idOrigen);
            }
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
