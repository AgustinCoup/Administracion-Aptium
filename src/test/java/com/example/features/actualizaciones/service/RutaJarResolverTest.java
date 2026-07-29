package com.example.features.actualizaciones.service;

import com.example.features.actualizaciones.exception.ActualizacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nota sobre el alcance: corriendo desde la suite de tests (igual que desde el IDE), el
 * CodeSource de la app apunta al directorio de clases (ej. target/classes), no a un JAR
 * empaquetado — por eso resolverJarActual() debe rechazar ese caso en vez de intentar
 * reemplazarlo. El comportamiento sobre un fat JAR real solo se puede confirmar en la
 * prueba manual de la fase.
 */
class RutaJarResolverTest {

    private final RutaJarResolver resolver = new RutaJarResolver();

    @Test
    @DisplayName("resolverJarActual rechaza correr desde el classpath de clases (no es un JAR empaquetado)")
    void resolverJarActual_corriendoDesdeClasspath_lanzaActualizacionException() {
        assertThrows(ActualizacionException.class, resolver::resolverJarActual);
    }

    @Test
    @DisplayName("resolverLockActualizacion deriva el path del lock a partir del JAR target")
    void resolverLockActualizacion_derivaPathDelLock(@TempDir Path base) {
        Path jarTarget = base.resolve("aptium.jar");

        Path lock = resolver.resolverLockActualizacion(jarTarget);

        assertEquals(base.resolve("aptium.jar.lock"), lock);
    }
}
