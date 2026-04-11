package com.example.features.autoclaves.service;

import com.example.features.autoclaves.model.Autoclave;
import com.example.features.autoclaves.dao.AutoclaveDAO;

import java.util.List;

public class AutoclaveService {

    private final AutoclaveDAO autoclaveDAO;

    public AutoclaveService(AutoclaveDAO autoclaveDAO) {
        if (autoclaveDAO == null) {
            throw new IllegalArgumentException("AutoclaveDAO no puede ser nulo");
        }
        this.autoclaveDAO = autoclaveDAO;
    }

    public List<Autoclave> obtenerTodos() {
        return autoclaveDAO.obtenerTodos();
    }
}


