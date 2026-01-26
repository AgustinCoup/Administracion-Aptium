package com.example.model;

import com.example.database.Conexion;
import java.sql.Connection;
import java.sql.SQLException;

public class AppModel {

    /**
     * Verifica que la aplicacion pueda abrir una conexion basica.
     * También inicializa la estructura de la base de datos si es necesario.
     */
    public boolean validarConexion() {
        // Primero inicializar la estructura de base de datos (crea BD y tablas si no existen)
        Conexion.inicializarBaseDeDatos();
        
        // Luego verificar que la conexión funcione correctamente
        Connection conn = Conexion.conectar();
        if (conn == null) {
            return false;
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
            // Ignoramos errores al cerrar la prueba de conexion.
        }
        return true;
    }

    /**
     * Devuelve una nueva conexion para operaciones posteriores.
     */
    public Connection nuevaConexion() {
        return Conexion.conectar();
    }
}
