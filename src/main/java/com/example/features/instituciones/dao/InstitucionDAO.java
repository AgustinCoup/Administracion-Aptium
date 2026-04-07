package com.example.features.instituciones.dao;

import com.example.common.dao.SimpleEntityDAO;
import com.example.features.instituciones.model.Institucion;

/**
 * DAO para instituciones. Extiende SimpleEntityDAO para operaciones CRUD estándar.
 */
public class InstitucionDAO extends SimpleEntityDAO<Institucion> {

    @Override
    protected String getTableName() { return "instituciones"; }

    @Override
    protected String getEntityName() { return "Institución"; }

    @Override
    protected Institucion newInstance() { return new Institucion(); }
}
