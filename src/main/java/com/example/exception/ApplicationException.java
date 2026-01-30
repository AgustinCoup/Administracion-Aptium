package com.example.exception;

/**
 * Excepción base para todas las excepciones de la aplicación.
 * 
 * Usar RuntimeException (unchecked) permite:
 * - Código más limpio sin try-catch en cada método
 * - Propagación automática hasta el handler apropiado
 * - Spring/frameworks modernos esperan unchecked exceptions
 */
public class ApplicationException extends RuntimeException {
    
    private final String errorCode;
    
    public ApplicationException(String message) {
        super(message);
        this.errorCode = null;
    }
    
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }
    
    public ApplicationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Código de error opcional para logging estructurado.
     * Ejemplo: "DB001", "VAL002", etc.
     */
    public String getErrorCode() {
        return errorCode;
    }
}
