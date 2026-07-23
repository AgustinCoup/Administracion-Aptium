package com.example.features.actualizaciones.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nota sobre el alcance: corriendo desde la suite de tests, {@code resolverJarActual()}
 * devuelve el directorio de clases (ej. {@code target/classes}), no un JAR empaquetado.
 * Estos tests validan que el método resuelve una ruta válida y existente sin explotar;
 * el comportamiento bajo un fat JAR real solo se puede confirmar en la prueba manual de la fase.
 */
class RutaJarResolverTest {

    private final RutaJarResolver resolver = new RutaJarResolver();

    @Test
    @DisplayName("resolverJarActual devuelve una ruta absoluta y existente")
    void resolverJarActual_devuelveRutaValidaYExistente() {
        Path ruta = resolver.resolverJarActual();

        assertNotNull(ruta);
        assertTrue(ruta.isAbsolute(), "la ruta del código en ejecución debe ser absoluta");
        assertTrue(Files.exists(ruta), "la ruta resuelta debe existir en el filesystem");
    }

    @Test
    @DisplayName("resolverJarActual es determinista entre invocaciones")
    void resolverJarActual_esDeterministaEntreInvocaciones() {
        assertEquals(resolver.resolverJarActual(), resolver.resolverJarActual());
    }
}
