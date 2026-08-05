package com.example.common.exception;

/**
 * Excepción lanzada al intentar eliminar una fila que todavía está referenciada
 * por otras tablas (violación de foreign key).
 *
 * Permite que la capa de servicio distinga "no se puede borrar porque está en uso"
 * de un error de base de datos genérico, sin tener que inspeccionar la
 * {@link java.sql.SQLException} subyacente ni conocer códigos de error del motor.
 *
 * La detección vive en la capa DAO — ver
 * {@link com.example.common.dao.SimpleEntityDAO#eliminar(Integer)}.
 */
public class ReferentialIntegrityException extends DatabaseException {

    public ReferentialIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
