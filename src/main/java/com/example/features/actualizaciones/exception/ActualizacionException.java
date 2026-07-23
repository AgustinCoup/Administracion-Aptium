package com.example.features.actualizaciones.exception;

import com.example.common.exception.ApplicationException;

/**
 * Error en cualquier paso del flujo de actualización (chequeo, descarga, verificación o instalación).
 */
public class ActualizacionException extends ApplicationException {

    public ActualizacionException(String message) {
        super(message);
    }

    public ActualizacionException(String message, Throwable cause) {
        super(message, cause);
    }
}
