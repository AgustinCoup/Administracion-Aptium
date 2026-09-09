package com.example.common.dao;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Clasificación de {@link SQLException} sin depender del motor.
 *
 * <p>Una sola definición de "violación de integridad" para toda la app: la tenían
 * {@code SimpleEntityDAO} (para traducir una FK en {@code ReferentialIntegrityException}) y la
 * necesita {@code LoteDAO} (para detectar el duplicado de {@code id_negocio} y reintentar la
 * secuencia). Duplicarla invita a que las dos se desincronicen.</p>
 *
 * <p>No abre conexiones ni ejecuta SQL: recibe la excepción que el DAO ya tiene en la mano.</p>
 */
public final class ErroresSql {

    /** Clase de SQLState que el estándar SQL reserva para integrity constraint violation. */
    private static final String SQLSTATE_VIOLACION_INTEGRIDAD = "23";

    private ErroresSql() {}

    /**
     * Detecta una violación de restricción de integridad sin depender del motor.
     *
     * <p>El estándar SQL reserva la clase {@code 23} de SQLState para integrity constraint
     * violation, así que esto cubre tanto MySQL ({@code 23000}) como H2 ({@code 23503},
     * {@code 23505}) sin usar códigos de error propietarios.</p>
     *
     * <p><b>No distingue qué restricción se violó.</b> Una FK y un UNIQUE de la misma sentencia
     * dan los dos {@code true}: quien necesite saber cuál fue tiene que averiguarlo por su
     * cuenta — ver {@code LoteDAO.lanzarLote}, que lo hace con una lectura fuera de la
     * transacción fallida.</p>
     */
    public static boolean esViolacionDeIntegridad(SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException) return true;
        String sqlState = e.getSQLState();
        return sqlState != null && sqlState.startsWith(SQLSTATE_VIOLACION_INTEGRIDAD);
    }
}
