package com.example.features.profesionales.service;

import com.example.features.profesionales.model.Profesional;
import com.example.features.profesionales.dao.ProfesionalDAO;

import java.util.Collections;
import java.util.List;

/**
 * Capa de servicio para lógica de negocio relacionada con profesionales.
 * Valida reglas de negocio antes de delegar al DAO.
 * 
 * DEPENDENCY INJECTION:
 * - Recibe DAO por constructor para permitir testing
 */
public class ProfesionalService {
    
    private final ProfesionalDAO profesionalDAO;
    
    /**
     * Constructor con inyección de dependencias.
     * 
     * @param profesionalDAO DAO para acceso a datos
     */
    public ProfesionalService(ProfesionalDAO profesionalDAO) {
        if (profesionalDAO == null) {
            throw new IllegalArgumentException("ProfesionalDAO no puede ser nulo");
        }
        this.profesionalDAO = profesionalDAO;
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

    /**
     * Guarda un nuevo profesional en la base de datos.
     * 
     * Valida que el nombre no sea vacío.
     * 
     * @param profesional Profesional a guardar
     * @return true si se guardó exitosamente, false en caso contrario
     * @throws IllegalArgumentException si el profesional es nulo o el nombre está vacío
     */
    public boolean guardarProfesional(Profesional profesional) {
        if (profesional == null) {
            throw new IllegalArgumentException("Profesional no puede ser nulo");
        }
        
        if (profesional.getNombre() == null || profesional.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("Nombre del profesional no puede estar vacío");
        }
        
        return profesionalDAO.guardar(profesional);
    }
}


