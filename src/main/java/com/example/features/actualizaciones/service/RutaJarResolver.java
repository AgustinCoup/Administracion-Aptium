package com.example.features.actualizaciones.service;

import com.example.app.App;
import com.example.features.actualizaciones.exception.ActualizacionException;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

/**
 * Resuelve en runtime la ruta del artefacto desde el cual se está ejecutando la app,
 * sin asumir nunca una ruta fija de instalación (ver decisión #2 del plan de auto-update).
 *
 * <p>Empaquetada como fat JAR, {@code resolverJarActual()} devuelve la ruta del
 * {@code aptium.jar} en ejecución. Corriendo desde el classpath (tests o IDE) devuelve
 * el directorio de clases — el mecanismo es el mismo, solo cambia el destino real.
 */
public class RutaJarResolver {

    /**
     * @return ruta del JAR (o directorio de clases) desde el cual corre la app actual
     * @throws ActualizacionException si no se puede determinar la ubicación del código
     */
    public Path resolverJarActual() {
        CodeSource codeSource = App.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new ActualizacionException(
                "No se pudo determinar la ubicación del JAR en ejecución (CodeSource no disponible)");
        }
        try {
            return Paths.get(codeSource.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new ActualizacionException("La ubicación del JAR en ejecución no es una URI válida", e);
        }
    }
}
