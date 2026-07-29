package com.example.features.actualizaciones.service;

import com.example.app.App;
import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.exception.ActualizacionException;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

/**
 * Resuelve en runtime las rutas relacionadas al reemplazo del JAR en ejecución,
 * sin asumir nunca una ruta fija de instalación (ver decisión #2 del plan de auto-update).
 */
public class RutaJarResolver {

    /**
     * @return ruta del JAR en ejecución
     * @throws ActualizacionException si no se puede determinar la ubicación del código, o si la
     *     app no está corriendo desde un JAR empaquetado (ej. classpath del IDE o de tests): el
     *     mecanismo de reemplazo solo tiene sentido sobre un archivo, no sobre un directorio de
     *     clases completo.
     */
    public Path resolverJarActual() {
        CodeSource codeSource = App.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new ActualizacionException(
                "No se pudo determinar la ubicación del JAR en ejecución (CodeSource no disponible)");
        }
        Path ubicacion;
        try {
            ubicacion = Paths.get(codeSource.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new ActualizacionException("La ubicación del JAR en ejecución no es una URI válida", e);
        }
        if (Files.isDirectory(ubicacion)) {
            throw new ActualizacionException(
                "La actualización automática no está disponible corriendo desde el IDE o el classpath "
                    + "de tests (la app no está empaquetada como JAR): " + ubicacion);
        }
        return ubicacion;
    }

    /** Ruta del lock que evita que dos reemplazos de {@code jarTarget} corran a la vez. */
    public Path resolverLockActualizacion(Path jarTarget) {
        return jarTarget.resolveSibling(jarTarget.getFileName() + Constantes.Actualizaciones.SUFIJO_LOCK);
    }
}
