package com.example.features.clientes.dao;

import com.example.common.constants.Constantes;
import com.example.common.dao.ControlConcurrencia;
import com.example.common.dao.ErroresSql;
import com.example.common.dao.SimpleEntityDAO;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ReferentialIntegrityException;
import com.example.features.clientes.model.Cliente;
import com.example.infrastructure.db.ConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * DAO para clientes. Extiende SimpleEntityDAO para operaciones CRUD estándar.
 * actualizar() y eliminar() no están soportados en este contexto de negocio.
 */
public class ClienteDAO extends SimpleEntityDAO<Cliente> {

    @Override
    protected String getTableName() { return "clientes"; }

    @Override
    protected String getEntityName() { return "Cliente"; }

    @Override
    protected Cliente newInstance() { return new Cliente(); }

    @Override
    public boolean actualizar(Cliente entity) {
        throw new UnsupportedOperationException("Actualizar cliente no implementado");
    }

    /**
     * Borrado guardado para Ajustes: CAS de una sola sentencia contra el {@code nombre} que la
     * grilla tenía a la vista. No se agrega a {@link #eliminar(Integer)} porque esa firma es
     * {@code @Override} de {@code DAO<T,ID>} y la comparten instituciones y profesionales, que
     * no tienen borrado alcanzable — cambiarle la firma les impondría un parámetro que no usan.
     *
     * <p>Replica el mapeo a {@link ReferentialIntegrityException} de {@link #eliminar(Integer)}:
     * si el cliente está referenciado, la FK falla antes de que importe el CAS.
     */
    public void eliminarConNombre(int id, String nombre) {
        String sql = "DELETE FROM clientes WHERE id = ? AND nombre = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, nombre);
            ControlConcurrencia.exigirFilaAfectada(
                pstmt.executeUpdate(), Constantes.Mensajes.CONFLICTO_CLIENTE);
        } catch (SQLException e) {
            if (ErroresSql.esViolacionDeIntegridad(e)) {
                throw new ReferentialIntegrityException(
                    "Cliente con ID " + id + " está referenciado por otras tablas", e);
            }
            throw new DatabaseException("Error al eliminar Cliente con ID: " + id, e);
        }
    }
}
