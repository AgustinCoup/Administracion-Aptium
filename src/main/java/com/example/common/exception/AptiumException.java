package com.example.common.exception;

/**
 * Excepción base para la aplicación.
 * Todas las excepciones de negocio deben heredar de esta clase.
 * 
 * ESTRATEGIA DE ERRORES:
 * 1. DAOs lanzan DataAccessException (problemas de BD)
 * 2. Services lanzan BusinessException (problemas de lógica)
 * 3. Controllers capturan y muestran al usuario
 * 4. Nunca silenciar excepciones en catch sin relanzar
 */
public class AptiumException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Código de error para identificar el tipo de problema.
     * Permite UI mostrar mensajes específicos al usuario.
     */
    private final String errorCode;
    
    /**
     * Contexto adicional para debugging.
     */
    private final String context;
    
    /**
     * Constructor básico.
     */
    public AptiumException(String mensaje) {
        super(mensaje);
        this.errorCode = "ERROR_GENERAL";
        this.context = "";
    }
    
    /**
     * Constructor con código de error.
     */
    public AptiumException(String errorCode, String mensaje) {
        super(mensaje);
        this.errorCode = errorCode;
        this.context = "";
    }
    
    /**
     * Constructor completo.
     */
    public AptiumException(String errorCode, String mensaje, String context, Throwable causa) {
        super(mensaje, causa);
        this.errorCode = errorCode;
        this.context = context;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getContext() {
        return context;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s | Contexto: %s", errorCode, getMessage(), context);
    }
}
