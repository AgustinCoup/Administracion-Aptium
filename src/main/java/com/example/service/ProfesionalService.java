package com.example.service;

import com.example.model.Profesional;
import com.example.model.ProfesionalDAO;

import java.util.Collections;
import java.util.List;

/**
 * Capa de servicio para lógica de negocio relacionada con profesionales.
 * Valida reglas de negocio antes de delegar al DAO.
 */
public class ProfesionalService {
    
    private final ProfesionalDAO profesionalDAO;
    
    public ProfesionalService() {
        this.profesionalDAO = new ProfesionalDAO();
    }
    
    /**
     * Busca profesionales por nombre con validación de mínimo 3 caracteres.
     * 
     * @param nombre Texto a buscar
     * @return Lista de profesionales que coinciden, o lista vacía si el texto es muy corto
     */
    public List<Profesional> buscarProfesionales(String nombre) {
        if (nombre == null || nombre.trim().length() < 3) {
            return Collections.emptyList();
        }
        return profesionalDAO.buscarPorNombre(nombre.trim());
    }
    
    /**
     * Obtiene un profesional por su ID.
     * 
     * @param id ID del profesional
     * @return Profesional encontrado, o null si no existe
     */
    public Profesional obtenerProfesionalPorId(int id) {
        return profesionalDAO.obtenerPorId(id);
    }
    
    /**
     * Obtiene todos los profesionales ordenados por nombre.
     * 
     * @return Lista de todos los profesionales
     */
    public List<Profesional> obtenerTodosLosProfesionales() {
        return profesionalDAO.obtenerTodos();
    }
}
