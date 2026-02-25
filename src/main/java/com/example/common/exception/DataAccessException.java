package com.example.common.exception;

/**
 * Excepción lanzada por operaciones de acceso a datos (DAOs).
 * 
 * CÓDIGOS DE ERROR:
 * - DB_CONNECTION_FAILED: No se pudo conectar a BD
 * - DB_QUERY_FAILED: Query falló
 * - DB_INSERT_FAILED: Insert falló
 * - DB_UPDATE_FAILED: Update falló
 * - DB_DELETE_FAILED: Delete falló
 * - DB_NOT_FOUND: Registro no encontrado
 */
public class DataAccessException extends AptiumException {
    
    private static final long serialVersionUID = 1L;
    
    // Códigos de error específicos
    public static final String DB_CONNECTION_FAILED = "DB_CONNECTION_FAILED";
    public static final String DB_QUERY_FAILED = "DB_QUERY_FAILED";
    public static final String DB_INSERT_FAILED = "DB_INSERT_FAILED";
    public static final String DB_UPDATE_FAILED = "DB_UPDATE_FAILED";
    public static final String DB_DELETE_FAILED = "DB_DELETE_FAILED";
    public static final String DB_NOT_FOUND = "DB_NOT_FOUND";
    
    public DataAccessException(String errorCode, String mensaje) {
        super(errorCode, mensaje);
    }
    
    public DataAccessException(String errorCode, String mensaje, String context) {
        super(errorCode, mensaje, context, null);
    }
    
    public DataAccessException(String errorCode, String mensaje, String context, Throwable causa) {
        super(errorCode, mensaje, context, causa);
    }
    
    /**
     * Factory method para error de conexión.
     */
    public static DataAccessException conexionFallida(String host, Throwable causa) {
        return new DataAccessException(
            DB_CONNECTION_FAILED,
            "No se pudo conectar a la base de datos",
            String.format("Host: %s", host),
            causa
        );
    }
    
    /**
     * Factory method para error en query.
     */
    public static DataAccessException queryFallida(String sql, Throwable causa) {
        return new DataAccessException(
            DB_QUERY_FAILED,
            "Error al ejecutar query",
            String.format("SQL: %s", sql.length() > 100 ? sql.substring(0, 100) + "..." : sql),
            causa
        );
    }
    
    /**
     * Factory method para registro no encontrado.
     */
    public static DataAccessException noEncontrado(String tipoEntidad, int id) {
        return new DataAccessException(
            DB_NOT_FOUND,
            String.format("%s con ID %d no encontrado", tipoEntidad, id),
            ""
        );
    }
}
