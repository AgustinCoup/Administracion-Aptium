package com.example.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Conexion {
    // Reemplaza 'localhost' por la IP de la PC servidor cuando estés en la empresa
    private static final Properties PROPS = new Properties();

    static {
        // Carga config.properties desde el directorio demo
        try {
            Path p = Paths.get("demo/config.properties");
            if (!Files.exists(p)) {
                p = Paths.get("config.properties");
            }
            if (Files.exists(p)) {
                InputStream is = Files.newInputStream(p);
                PROPS.load(is);
                is.close();
            } else {
                System.out.println("No se encontró config.properties en demo/; usando valores por defecto.");
            }
        } catch (IOException e) {
            System.out.println("No se pudo cargar config.properties: " + e.getMessage());
        }
    }

    private static String getDbIp() {
        return PROPS.getProperty("db.ip", "localhost");
    }

    private static String getDbUser() {
        return PROPS.getProperty("db.user", "root");
    }

    private static String getDbPassword() {
        return PROPS.getProperty("db.pass", "tu_password_aqui");
    }

    public static Connection conectar() {
        try {
            String url = "jdbc:mysql://" + getDbIp() + ":3306/sistema_empresa?serverTimezone=UTC";
            Connection conn = DriverManager.getConnection(url, getDbUser(), getDbPassword());
            System.out.println("¡Conexión exitosa a MySQL!");
            return conn;
        } catch (SQLException e) {
            System.out.println("Error al conectar a MySQL: " + e.getMessage());
            return null;
        }
    }
}