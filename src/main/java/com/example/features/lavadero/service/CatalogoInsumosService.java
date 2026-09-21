package com.example.features.lavadero.service;

import com.example.features.lavadero.dao.CatalogoInsumosDAO;
import com.example.features.lavadero.model.InsumoCatalogo;

import java.util.List;

public class CatalogoInsumosService {

    private final CatalogoInsumosDAO dao;

    public CatalogoInsumosService(CatalogoInsumosDAO dao) {
        if (dao == null) throw new IllegalArgumentException("CatalogoInsumosDAO no puede ser nulo");
        this.dao = dao;
    }

    public List<InsumoCatalogo> obtenerTodos() {
        return dao.findAll();
    }
}
