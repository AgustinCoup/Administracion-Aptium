package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CatalogoElementosLavaderoDAO;
import com.example.features.lavadero.dao.ClasificacionLavaderoDAO;
import com.example.features.lavadero.model.CategoriaElementoLavadero;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.model.ElementoClasificacion;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClasificacionLavaderoService {

    private static final int NOMBRE_MAX = 100;

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

    /**
     * Da de alta un elemento nuevo en el catálogo de lavadero.
     *
     * @return el elemento creado, ya con su id
     * @throws ValidationException si el nombre es inválido o ya existe uno con ese nombre
     */
    public ElementoCatalogo agregarElementoCatalogo(String nombre, CategoriaElementoLavadero categoria) {
        String limpio = nombre == null ? "" : nombre.trim();

        ValidationException.builder()
            .addErrorIf(limpio.isEmpty(), "El nombre del elemento no puede estar vacío.")
            .addErrorIf(limpio.length() > NOMBRE_MAX,
                "El nombre no puede superar los " + NOMBRE_MAX + " caracteres.")
            .addErrorIf(categoria == null, "Debe elegir una categoría.")
            .throwIfHasErrors();

        if (catalogoDAO.buscarPorNombre(limpio).isPresent()) {
            throw new ValidationException("Ya existe un elemento de catálogo llamado \"" + limpio + "\".");
        }

        return catalogoDAO.agregar(limpio, categoria);
    }

    private boolean hasDuplicates(List<ElementoClasificacion> elementos) {
        Set<Integer> vistos = new HashSet<>();
        for (ElementoClasificacion e : elementos) {
            if (!vistos.add(e.getElementoId())) return true;
        }
        return false;
    }

    public boolean guardar(int ingresoId, List<ElementoClasificacion> elementos) {
        ValidationException.Builder builder = ValidationException.builder()
            .addErrorIf(ingresoId <= 0, "Debe seleccionar un ingreso.")
            .addErrorIf(elementos == null || elementos.isEmpty(), "Debe agregar al menos un elemento.")
            .addErrorIf(
                elementos != null && elementos.stream().anyMatch(e -> e.getCantidad() <= 0),
                "La cantidad de cada elemento debe ser mayor a cero."
            )
            .addErrorIf(
                elementos != null && hasDuplicates(elementos),
                "No puede haber elementos repetidos. Usá la cantidad para indicar más de uno."
            );
        builder.throwIfHasErrors();

        return clasificacionDAO.guardar(ingresoId, elementos);
    }
}
