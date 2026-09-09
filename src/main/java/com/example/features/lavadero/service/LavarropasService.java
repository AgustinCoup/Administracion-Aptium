package com.example.features.lavadero.service;

import com.example.features.lavadero.dao.LavarropasDAO;
import com.example.features.lavadero.model.Lavarropas;

import java.util.List;

public class LavarropasService {

    private final LavarropasDAO dao;

    public LavarropasService(LavarropasDAO dao) {
        if (dao == null) throw new IllegalArgumentException("LavarropasDAO no puede ser nulo");
        this.dao = dao;
    }

    public List<Lavarropas> obtenerTodos() {
        return dao.obtenerTodos();
    }
}
