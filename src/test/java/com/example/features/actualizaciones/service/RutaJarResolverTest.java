package com.example.features.actualizaciones.service;

import com.example.features.actualizaciones.exception.ActualizacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Nota sobre el alcance: corriendo desde la suite de tests (igual que desde el IDE), el
 * CodeSource de la app apunta al directorio de clases (ej. target/classes), no a un JAR
 * empaquetado — por eso resolverJarActual() debe rechazar ese caso en vez de intentar
 * reemplazarlo. El comportamiento sobre un fat JAR real solo se puede confirmar en la
 * prueba manual de la fase.
 */
@ExtendWith(MockitoExtension.class)
class RutaJarResolverTest {

    @Mock DirectorioStagingResolver stagingResolver;

    @Test
    @DisplayName("resolverJarActual rechaza correr desde el classpath de clases (no es un JAR empaquetado)")
    void resolverJarActual_corriendoDesdeClasspath_lanzaActualizacionException() {
        assertThrows(ActualizacionException.class, new RutaJarResolver(stagingResolver)::resolverJarActual);
    }

    @Test
    @DisplayName("resolverLockActualizacion ubica el lock en el directorio de staging, no al lado del target")
    void resolverLockActualizacion_ubicaElLockEnElDirectorioDeStaging(@TempDir Path staging) {
        when(stagingResolver.resolver()).thenReturn(staging);

        Path lock = new RutaJarResolver(stagingResolver).resolverLockActualizacion();

        assertEquals(staging.resolve("aptium.jar.lock"), lock);
    }

    @Test
    @DisplayName("rechaza constructor con stagingResolver nulo")
    void constructor_stagingResolverNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RutaJarResolver(null));
    }
}
