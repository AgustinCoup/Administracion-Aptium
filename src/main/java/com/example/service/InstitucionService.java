package com.example.service;

import com.example.model.Institucion;
import com.example.model.InstitucionDAO;

import java.util.Collections;
import java.util.List;

/**
 * Capa de servicio para lógica de negocio relacionada con instituciones.
 * Valida reglas de negocio antes de delegar al DAO.
 * 
 * DEPENDENCY INJECTION:
 * - Recibe DAO por constructor para permitir testing
 */
public class InstitucionService {
    
    private final InstitucionDAO institucionDAO;
    
    /**
     * Constructor con inyección de dependencias.
     * 
     * @param institucionDAO DAO para acceso a datos
     */
    public InstitucionService(InstitucionDAO institucionDAO) {
        if (institucionDAO == null) {
            throw new IllegalArgumentException("InstitucionDAO no puede ser nulo");
        }
        this.institucionDAO = institucionDAO;
    }
    
    /**
     * Busca instituciones por nombre con validación de mínimo 3 caracteres.
     * 
     * @param nombre Texto a buscar
     * @return Lista de instituciones que coinciden, o lista vacía si el texto es muy corto
     */
    public List<Institucion> buscarInstituciones(String nombre) {
        if (nombre == null || nombre.trim().length() < 3) {
            return Collections.emptyList();
        }
        return institucionDAO.buscarPorNombre(nombre.trim());
    }
    
    /**
     * Obtiene una institución por su ID.
     * 
     * @param id ID de la institución
     * @return Institución encontrada, o null si no existe
     */
    public Institucion obtenerInstitucionPorId(int id) {
        return institucionDAO.obtenerPorId(id);
    }
    
    /**
     * Obtiene todas las instituciones ordenadas por nombre.
     * 
     * @return Lista de todas las instituciones
     */
    public List<Institucion> obtenerTodasLasInstituciones() {
        return institucionDAO.obtenerTodos();
    }
}
