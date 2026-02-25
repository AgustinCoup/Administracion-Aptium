package com.example.common.exception;

/**
 * Excepción lanzada cuando hay un error de base de datos.
 * 
 * CASOS DE USO:
 * - Conexión perdida durante operación
 * - Error de SQL syntax (debería ser raro si usas PreparedStatement)
 * - Violación de constraints (FK, UNIQUE, etc.)
 * - Timeout de transacción
 * 
 * ESTRATEGIA DE MANEJO:
 * En capa DAO: Lanzar con contexto específico
 * En capa Service: Propagar o transformar según necesidad
 * En capa Controller: Capturar y mostrar mensaje amigable al usuario
 */
public class DatabaseException extends ApplicationException {
    
    public DatabaseException(String message) {
        super(message);
    }
    
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructor con contexto de operación.
     * 
     * Ejemplo:
     * throw new DatabaseException("guardar", "Equipo", equipo.getId(), sqlException);
     * 
     * Genera mensaje: "Error al guardar Equipo con ID 123"
     */
    public DatabaseException(String operation, String entity, Object id, Throwable cause) {
        super(String.format("Error al %s %s con ID %s", operation, entity, id), cause);
    }
}


