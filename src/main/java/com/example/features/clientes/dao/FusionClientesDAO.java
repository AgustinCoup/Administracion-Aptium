package com.example.features.clientes.dao;

import com.example.common.exception.DatabaseException;
import com.example.infrastructure.db.TransactionalConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class FusionClientesDAO {

    public void fusionar(int idOrigen, int idDestino) {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            ejecutarUpdate(tx, "UPDATE equipos SET nro_cliente = ? WHERE nro_cliente = ?", idDestino, idOrigen);
            ejecutarUpdate(tx, "UPDATE equipo_otros SET nro_cliente = ? WHERE nro_cliente = ?", idDestino, idOrigen);
            ejecutarUpdate(tx, "DELETE FROM clientes WHERE id = ?", idOrigen);
            tx.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Error al fusionar clientes " + idOrigen + " → " + idDestino, e);
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
