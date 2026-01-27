package com.example.service;

import com.example.model.CatalogoDAO;
import com.example.util.Logger;
import java.util.Map;

/**
 * Servicio de negocio para operaciones con Catálogo de Materiales.
 * Encapsula toda la lógica relacionada con el catálogo.
 * 
 * Esta clase tiene una única responsabilidad: gestionar el catálogo de materiales.
 * Delega el acceso a datos en CatalogoDAO.
 */
public class CatalogoService {

    private CatalogoDAO catalogoDAO;

    public CatalogoService() {
        this.catalogoDAO = new CatalogoDAO();
    }

    /**
     * Obtiene todas las descripciones del catálogo de materiales.
     * Mapea código de material → descripción del material.
     * 
     * @return Mapa completo del catálogo
     */
    public Map<Integer, String> obtenerCatalogo() {
        try {
            return catalogoDAO.obtenerTodasLasDescripciones();
        } catch (Exception e) {
            Logger.error("Error al obtener catálogo", e);
            return Map.of(); // Retorna mapa vacío en caso de error
        }
    }

    /**
     * Obtiene la descripción de un material específico.
     * 
     * @param codigo Código del material
     * @return Descripción del material, null si no existe
     */
    public String obtenerDescripcion(int codigo) {
        try {
            return catalogoDAO.obtenerDescripcion(codigo);
        } catch (Exception e) {
            Logger.error("Error al obtener descripción del material: " + codigo, e);
            return null;
        }
    }

    /**
     * Guarda una nueva descripción en el catálogo.
     * 
     * @param codigo Código del material
     * @param descripcion Descripción del material
     * @return true si se guardó correctamente
     */
    public boolean guardarDescripcion(int codigo, String descripcion) {
        if (descripcion == null || descripcion.trim().isEmpty()) {
            Logger.warning("Intento de guardar descripción vacía para código: " + codigo);
            return false;
        }
        
        try {
            return catalogoDAO.guardarDescripcion(codigo, descripcion);
        } catch (Exception e) {
            Logger.error("Error al guardar descripción", e);
            return false;
        }
    }

    /**
     * Obtiene el total de materiales en el catálogo.
     * 
     * @return Cantidad de materiales
     */
    public long contar() {
        try {
            return catalogoDAO.contar();
        } catch (Exception e) {
            Logger.error("Error al contar materiales del catálogo", e);
            return 0;
        }
    }
}
