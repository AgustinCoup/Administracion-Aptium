package com.example.infrastructure.db;

import java.sql.SQLException;

/**
 * La tarea de fondo ya estaba cancelada cuando iba a tocar la base: no se abre la conexión, o no
 * se ejecuta la sentencia. No es un fallo, es el resultado buscado.
 *
 * <p>Es {@link SQLException} a propósito: viaja por los mismos {@code catch} de los DAOs que
 * cualquier error JDBC, así que ninguno necesita saber que existe. Que no ensucie
 * {@code error.log} lo resuelve {@link FiltroTareaCancelada}, no los DAOs.
 */
public class TareaCanceladaException extends SQLException {

    private static final long serialVersionUID = 1L;

    TareaCanceladaException(TokenTarea token, String momento) {
        super("Tarea '" + token + "' cancelada " + momento);
    }
}
