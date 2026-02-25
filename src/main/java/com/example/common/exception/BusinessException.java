package com.example.common.exception;

/**
 * Excepción lanzada por infracciones de lógica de negocio.
 * 
 * EJEMPLOS:
 * - Un equipo no puede estar en 2 lugares a la vez
 * - No se puede registrar equipo sin materiales
 * - Un lote no puede contener más de X autoclaves
 * 
 * CÓDIGOS DE ERROR:
 * - VALIDATION_FAILED: Validación de datos falló
 * - BUSINESS_RULE_VIOLATED: Regla de negocio violada
 * - ESTADO_INVALIDO: Estado inválido para la operación
 * - DATOS_INCONSISTENTES: Datos inconsistentes
 */
public class BusinessException extends AptiumException {
    
    private static final long serialVersionUID = 1L;
    
    // Códigos de error específicos
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String BUSINESS_RULE_VIOLATED = "BUSINESS_RULE_VIOLATED";
    public static final String ESTADO_INVALIDO = "ESTADO_INVALIDO";
    public static final String DATOS_INCONSISTENTES = "DATOS_INCONSISTENTES";
    
    public BusinessException(String errorCode, String mensaje) {
        super(errorCode, mensaje);
    }
    
    public BusinessException(String errorCode, String mensaje, String context) {
        super(errorCode, mensaje, context, null);
    }
    
    /**
     * Factory method para validación fallida.
     */
    public static BusinessException validacionFallida(String campo, String razon) {
        return new BusinessException(
            VALIDATION_FAILED,
            String.format("Validación fallida: %s", razon),
            String.format("Campo: %s", campo)
        );
    }
    
    /**
     * Factory method para regla de negocio violada.
     */
    public static BusinessException reglaViolada(String regla) {
        return new BusinessException(
            BUSINESS_RULE_VIOLATED,
            String.format("Regla de negocio violada: %s", regla),
            ""
        );
    }
    
    /**
     * Factory method para estado inválido.
     */
    public static BusinessException estadoInvalido(String entidad, String estadoActual, String estadoRequerido) {
        return new BusinessException(
            ESTADO_INVALIDO,
            String.format("No se puede cambiar %s de estado %s a %s", entidad, estadoActual, estadoRequerido),
            ""
        );
    }
}
