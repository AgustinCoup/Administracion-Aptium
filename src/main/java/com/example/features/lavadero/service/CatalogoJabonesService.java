package com.example.features.lavadero.service;

import com.example.features.lavadero.dao.CatalogoJabonesDAO;
import com.example.features.lavadero.model.JabonCatalogo;

import java.util.List;

public class CatalogoJabonesService {

    private final CatalogoJabonesDAO dao;

    public CatalogoJabonesService(CatalogoJabonesDAO dao) {
        if (dao == null) throw new IllegalArgumentException("CatalogoJabonesDAO no puede ser nulo");
        this.dao = dao;
    }

    public List<JabonCatalogo> obtenerTodos() {
        return dao.findAll();
    }
}
