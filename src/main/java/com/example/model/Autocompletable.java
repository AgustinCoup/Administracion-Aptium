package com.example.model;

/**
 * Interfaz base para las entidades que se pueden crear desde el autocompletado.
 * Permite que el diálogo y gestor de nuevas entidades sean genéricos.
 */
public interface Autocompletable {
    /**
     * Obtiene el identificador único de la entidad.
     */
    Integer getId();
    
    /**
     * Establece el identificador único de la entidad.
     */
    void setId(Integer id);
    
    /**
     * Obtiene el nombre de la entidad.
     */
    String getNombre();
    
    /**
     * Establece el nombre de la entidad.
     */
    void setNombre(String nombre);
}
