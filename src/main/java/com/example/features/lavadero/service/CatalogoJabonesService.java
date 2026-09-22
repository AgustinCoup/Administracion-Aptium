package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CatalogoJabonesDAO;
import com.example.features.lavadero.model.JabonCatalogo;

import java.util.List;

public class CatalogoJabonesService {

    private final CatalogoJabonesDAO dao;

    public CatalogoJabonesService(CatalogoJabonesDAO dao) {
        if (dao == null) throw new IllegalArgumentException("CatalogoJabonesDAO no puede ser nulo");
        this.dao = dao;
    }

    /** Para el combo de la card: sólo los que el operador puede elegir. */
    public List<JabonCatalogo> obtenerActivos() {
        return dao.findActivos();
    }

    /** Todos, activos y de baja: para Ajustes. */
    public List<JabonCatalogo> obtenerTodos() {
        return dao.findAll();
    }

    public JabonCatalogo agregar(String nombre) {
        exigirNombreValido(nombre);
        return dao.agregar(nombre.trim());
    }

    public void darDeBaja(int id) {
        exigirIdValido(id);
        dao.darDeBaja(id);
    }

    public void reactivar(int id) {
        exigirIdValido(id);
        dao.reactivar(id);
    }

    private static void exigirNombreValido(String nombre) {
        ValidationException.builder()
            .addErrorIf(nombre == null || nombre.trim().isEmpty(), "El nombre del jabón no puede estar vacío.")
            .throwIfHasErrors();
    }

    private static void exigirIdValido(int id) {
        ValidationException.builder()
            .addErrorIf(id <= 0, "Debe indicar un jabón válido.")
            .throwIfHasErrors();
    }
}
