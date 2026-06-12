package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CatalogoElementosLavaderoDAO;
import com.example.features.lavadero.dao.ClasificacionLavaderoDAO;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.model.ElementoClasificacion;

import java.util.List;

public class ClasificacionLavaderoService {

    private final ClasificacionLavaderoDAO     clasificacionDAO;
    private final CatalogoElementosLavaderoDAO catalogoDAO;

    public ClasificacionLavaderoService(ClasificacionLavaderoDAO clasificacionDAO,
                                        CatalogoElementosLavaderoDAO catalogoDAO) {
        if (clasificacionDAO == null || catalogoDAO == null) {
            throw new IllegalArgumentException("ClasificacionLavaderoService requiere DAOs no nulos");
        }
        this.clasificacionDAO = clasificacionDAO;
        this.catalogoDAO      = catalogoDAO;
    }

    public List<ElementoCatalogo> obtenerCatalogo() {
        return catalogoDAO.findAll();
    }

    public boolean guardar(int ingresoId, List<ElementoClasificacion> elementos) {
        ValidationException.Builder builder = ValidationException.builder()
            .addErrorIf(ingresoId <= 0, "Debe seleccionar un ingreso.")
            .addErrorIf(elementos == null || elementos.isEmpty(), "Debe agregar al menos un elemento.")
            .addErrorIf(
                elementos != null && elementos.stream().anyMatch(e -> e.getCantidad() <= 0),
                "La cantidad de cada elemento debe ser mayor a cero."
            );
        builder.throwIfHasErrors();

        return clasificacionDAO.guardar(ingresoId, elementos);
    }
}
