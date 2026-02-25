package com.example.common.exception;

/**
 * Excepción lanzada cuando un recurso solicitado no existe.
 * 
 * CASOS DE USO:
 * - Buscar equipo por ID inexistente
 * - Cliente no encontrado en autocompletado
 * - Material no existe en catálogo
 * 
 * CÓDIGO HTTP EQUIVALENTE: 404 Not Found
 * 
 * ESTRATEGIA DE MANEJO:
 * En DAOs: Lanzar cuando obtenerPorId() no encuentra nada
 * En Controllers: Mostrar mensaje "No se encontró el recurso solicitado"
 */
public class ResourceNotFoundException extends ApplicationException {
    
    private final String resourceType;
    private final Object resourceId;
    
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s con ID '%s' no encontrado", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public Object getResourceId() {
        return resourceId;
    }
}


