package com.example.features.lavadero.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.infrastructure.db.TransactionalConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC de la clasificación de un ingreso de lavadero: qué hay adentro de las bolsas que
 * entraron.
 *
 * <p>Es una operación de <b>una sola vez por ingreso</b>, y esa unicidad es lo que la guarda
 * de concurrencia protege: sin ella, dos operadores clasificando el mismo ingreso
 * {@code PENDIENTE} insertan los dos juegos de líneas y el ingreso queda con el doble de ropa
 * de la que entró — todo lo que viene después (saldos, tandas, salidas) trabaja sobre ese
 * número inflado.</p>
 */
public class ClasificacionLavaderoDAO {

    private static final String SQL_INSERTAR_ELEMENTO =
        "INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES (?, ?, ?)";

    /**
     * Guarda de concurrencia: sólo un ingreso todavía {@code PENDIENTE} se puede clasificar.
     * Cero filas afectadas significa que otro operador ya lo clasificó (o que el ingreso dejó
     * de existir), y en los dos casos lo que sigue no hay que insertarlo.
     */
    private static final String SQL_MARCAR_CLASIFICADO =
        "UPDATE ingresos_lavadero SET estado = '" + EstadoIngresoLavadero.CLASIFICADO + "' "
        + "WHERE id = ? AND estado = '" + EstadoIngresoLavadero.PENDIENTE + "'";

    /**
     * Persiste la clasificación entera en una sola transacción.
     *
     * <p>El cambio de estado va <b>antes</b> del batch de inserts a propósito: si el ingreso ya
     * no está {@code PENDIENTE} no hay nada que insertar, y hacerlo al revés dejaría las líneas
     * escritas para tener que volverlas atrás.</p>
     *
     * @throws com.example.common.exception.ConflictoConcurrenciaException si el ingreso ya no
     *         está {@code PENDIENTE}; no queda ninguna línea insertada
     * @throws DatabaseException si falla el SQL
     */
    public void guardar(int ingresoId, List<ElementoClasificacion> elementos) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            marcarClasificado(conn, ingresoId);
            insertarElementos(conn, ingresoId, elementos);
            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al guardar la clasificación del ingreso " + ingresoId, e);
        }
    }

    private void marcarClasificado(Connection conn, int ingresoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_MARCAR_CLASIFICADO)) {
            ps.setInt(1, ingresoId);
            ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(),
                Constantes.Mensajes.CONFLICTO_CLASIFICACION);
        }
    }

    private void insertarElementos(Connection conn, int ingresoId,
                                   List<ElementoClasificacion> elementos) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_ELEMENTO)) {
            for (ElementoClasificacion e : elementos) {
                ps.setInt(1, ingresoId);
                ps.setInt(2, e.getElementoId());
                ps.setInt(3, e.getCantidad());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
