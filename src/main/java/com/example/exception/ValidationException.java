package com.example.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Excepción lanzada cuando los datos de entrada no son válidos.
 * 
 * CASOS DE USO:
 * - Campos obligatorios vacíos
 * - Formato incorrecto (email, teléfono, etc.)
 * - Valores fuera de rango
 * - Reglas de negocio violadas
 * 
 * CÓDIGO HTTP EQUIVALENTE: 400 Bad Request
 * 
 * VENTAJA: Puede contener múltiples errores de validación
 * para mostrarlos todos de una vez al usuario.
 */
public class ValidationException extends ApplicationException {
    
    private final List<String> validationErrors;
    
    public ValidationException(String message) {
        super(message);
        this.validationErrors = new ArrayList<>();
        this.validationErrors.add(message);
    }
    
    public ValidationException(List<String> validationErrors) {
        super("Errores de validación: " + String.join(", ", validationErrors));
        this.validationErrors = new ArrayList<>(validationErrors);
    }
    
    /**
     * Obtiene lista de errores de validación.
     * Útil para mostrar todos los problemas al usuario de una vez.
     */
    public List<String> getValidationErrors() {
        return Collections.unmodifiableList(validationErrors);
    }
    
    /**
     * Builder para construir ValidationException con múltiples errores.
     */
    public static class Builder {
        private final List<String> errors = new ArrayList<>();
        
        public Builder addError(String error) {
            errors.add(error);
            return this;
        }
        
        public Builder addErrorIf(boolean condition, String error) {
            if (condition) {
                errors.add(error);
            }
            return this;
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public ValidationException build() {
            if (errors.isEmpty()) {
                throw new IllegalStateException("No hay errores para construir ValidationException");
            }
            return new ValidationException(errors);
        }
        
        /**
         * Lanza la excepción solo si hay errores.
         * Si no hay errores, no hace nada.
         */
        public void throwIfHasErrors() {
            if (hasErrors()) {
                throw build();
            }
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}
