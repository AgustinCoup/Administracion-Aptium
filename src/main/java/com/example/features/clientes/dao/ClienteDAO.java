package com.example.features.clientes.dao;

import com.example.common.dao.SimpleEntityDAO;
import com.example.features.clientes.model.Cliente;

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
}
