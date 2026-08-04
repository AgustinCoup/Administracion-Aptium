package com.example.features.actualizaciones.service;

import com.example.features.actualizaciones.exception.ActualizacionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resuelve el directorio de staging de actualizaciones ({@code %LOCALAPPDATA%/Aptium/updates}),
 * el único lugar que el usuario actual siempre puede escribir sin importar dónde esté instalada
 * la app — a diferencia del JAR target, que puede vivir en una carpeta con ACL restringida
 * (ej. Program Files).
 */
public class DirectorioStagingResolver {

    /**
     * @return el directorio de staging, ya creado si no existía
     * @throws ActualizacionException si {@code LOCALAPPDATA} no está definida o no se puede crear el directorio
     */
    public Path resolver() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            throw new ActualizacionException("La variable de entorno LOCALAPPDATA no está definida");
        }
        Path directorio = Path.of(localAppData, "Aptium", "updates");
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            throw new ActualizacionException("No se pudo crear el directorio de staging: " + directorio, e);
        }
        return directorio;
    }
}
