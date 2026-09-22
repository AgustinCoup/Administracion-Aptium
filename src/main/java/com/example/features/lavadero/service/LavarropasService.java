package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.LavarropasDAO;
import com.example.features.lavadero.model.Lavarropas;

import java.util.List;

public class LavarropasService {

    private final LavarropasDAO dao;

    public LavarropasService(LavarropasDAO dao) {
        if (dao == null) throw new IllegalArgumentException("LavarropasDAO no puede ser nulo");
        this.dao = dao;
    }

    /** Los que dibuja la grilla de Ciclos. Ver {@code LavarropasDAO.obtenerDibujables()}. */
    public List<Lavarropas> obtenerDibujables() {
        return dao.obtenerDibujables();
    }

    /** Todos, activos y de baja: es lo que muestra Ajustes. */
    public List<Lavarropas> obtenerTodos() {
        return dao.obtenerTodos();
    }

    /**
     * Da de alta un lavarropas con el número que eligió el operador.
     *
     * <p>Lo único que se valida acá es que el número sea positivo. <b>El techo se lo pone la base,
     * no una constante</b>: desde que los lavarropas se dan de alta, "cuántos hay" es un dato, no
     * un número compilado. Y que el número esté libre lo decide la PK dentro del {@code INSERT},
     * no un {@code SELECT} previo — eso sería una ventana TOCTOU con forma de validación.</p>
     */
    public void agregar(int numero) {
        exigirNumeroValido(numero);
        dao.agregar(numero);
    }

    public void darDeBaja(int numero) {
        exigirNumeroValido(numero);
        dao.darDeBaja(numero);
    }

    public void reactivar(int numero) {
        exigirNumeroValido(numero);
        dao.reactivar(numero);
    }

    private static void exigirNumeroValido(int numero) {
        ValidationException.builder()
            .addErrorIf(numero < 1, "El número de lavarropas debe ser mayor o igual a 1.")
            .throwIfHasErrors();
    }
}
