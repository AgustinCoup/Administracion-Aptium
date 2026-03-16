package com.example.features.catalogo.service;

import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Servicio para el catálogo de materiales "otros".
 * Encapsula las reglas de negocio del catálogo libre.
 */
public class CatalogoOtrosService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoOtrosService.class);

    private final CatalogoOtrosDAO dao;

    public CatalogoOtrosService(CatalogoOtrosDAO dao) {
        if (dao == null) throw new IllegalArgumentException("CatalogoOtrosDAO no puede ser nulo");
        this.dao = dao;
    }

    /**
     * Busca descripciones en el catálogo que contengan el texto dado.
     * Activa a partir de 1 carácter.
     *
     * @param texto Fragmento a buscar
     * @return Lista de descripciones coincidentes
     */
    public List<String> buscarPorDescripcionParcial(String texto) {
        if (texto == null || texto.trim().isEmpty()) return List.of();
        try {
            return dao.buscarPorDescripcionParcial(texto.trim());
        } catch (Exception e) {
            log.error("Error buscando en catalogo_otros: '{}'", texto, e);
            return List.of();
        }
    }
}