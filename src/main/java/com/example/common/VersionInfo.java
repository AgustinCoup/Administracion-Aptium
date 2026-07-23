package com.example.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Expone la versión de la app embebida en el classpath en build time
 * (ver src/main/resources/version.properties.template, filtrado por Maven vía -Dapp.version).
 * Nunca lanza excepción: si el recurso falta o está vacío, cae a un valor por defecto
 * para no romper el arranque de la app.
 */
public class VersionInfo {

    private static final String VERSION_POR_DEFECTO = "dev-SNAPSHOT";
    private static final String RECURSO = "version.properties";

    private final String version;

    public VersionInfo() {
        this.version = cargarVersion();
    }

    public String actual() {
        return version;
    }

    private static String cargarVersion() {
        try (InputStream in = VersionInfo.class.getClassLoader().getResourceAsStream(RECURSO)) {
            if (in == null) {
                return VERSION_POR_DEFECTO;
            }
            Properties props = new Properties();
            props.load(in);
            String valor = props.getProperty("app.version");
            return (valor == null || valor.isBlank()) ? VERSION_POR_DEFECTO : valor;
        } catch (IOException e) {
            return VERSION_POR_DEFECTO;
        }
    }
}
