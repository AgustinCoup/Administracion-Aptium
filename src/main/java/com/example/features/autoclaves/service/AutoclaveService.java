package com.example.features.autoclaves.service;

import com.example.features.autoclaves.model.Autoclave;
import com.example.features.autoclaves.dao.AutoclaveDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AutoclaveService {

    private static final Logger log = LoggerFactory.getLogger(AutoclaveService.class);

    private final AutoclaveDAO autoclaveDAO;

    public AutoclaveService(AutoclaveDAO autoclaveDAO) {
        if (autoclaveDAO == null) {
            throw new IllegalArgumentException("AutoclaveDAO no puede ser nulo");
        }
        this.autoclaveDAO = autoclaveDAO;
    }

    public List<Autoclave> obtenerTodos() {
        try {
            return autoclaveDAO.obtenerTodos();
        } catch (Exception e) {
            log.error("Error al obtener autoclaves", e);
            return List.of();
        }
    }
}


