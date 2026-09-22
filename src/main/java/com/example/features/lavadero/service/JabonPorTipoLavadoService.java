package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.JabonPorTipoLavadoDAO;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;

import java.util.Map;

/**
 * Jabón por defecto de cada tipo de lavado: lo lee la pantalla de Ciclos (para la carga
 * automática) y lo escribe la pestaña de Ajustes.
 */
public class JabonPorTipoLavadoService {

    private final JabonPorTipoLavadoDAO dao;

    public JabonPorTipoLavadoService(JabonPorTipoLavadoDAO dao) {
        if (dao == null) throw new IllegalArgumentException("JabonPorTipoLavadoDAO no puede ser nulo");
        this.dao = dao;
    }

    /**
     * Un tipo sin default no aparece en el mapa. Puede traer jabones dados de baja: eso no se
     * filtra acá — ver el javadoc de {@code JabonPorTipoLavadoDAO}.
     */
    public Map<TipoLavado, JabonCatalogo> obtenerDefaults() {
        return dao.obtenerDefaults();
    }

    public void guardar(TipoLavado tipo, int jabonId) {
        ValidationException.builder()
            .addErrorIf(tipo == null, "Debe indicar el tipo de lavado.")
            .addErrorIf(jabonId <= 0, "Debe indicar un jabón válido.")
            .throwIfHasErrors();
        dao.guardar(tipo, jabonId);
    }

    /** La opción "(sin default)": el tipo deja de cargar jabón solo. */
    public void borrar(TipoLavado tipo) {
        ValidationException.builder()
            .addErrorIf(tipo == null, "Debe indicar el tipo de lavado.")
            .throwIfHasErrors();
        dao.borrar(tipo);
    }
}
