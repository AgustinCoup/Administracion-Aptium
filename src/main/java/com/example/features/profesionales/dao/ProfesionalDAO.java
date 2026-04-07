package com.example.features.profesionales.dao;

import com.example.common.dao.SimpleEntityDAO;
import com.example.features.profesionales.model.Profesional;

/**
 * DAO para profesionales. Extiende SimpleEntityDAO para operaciones CRUD estándar.
 */
public class ProfesionalDAO extends SimpleEntityDAO<Profesional> {

    @Override
    protected String getTableName() { return "profesionales"; }

    @Override
    protected String getEntityName() { return "Profesional"; }

    @Override
    protected Profesional newInstance() { return new Profesional(); }
}
