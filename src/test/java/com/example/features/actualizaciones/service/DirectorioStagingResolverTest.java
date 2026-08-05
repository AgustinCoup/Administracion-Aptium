package com.example.features.actualizaciones.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DirectorioStagingResolverTest {

    private final DirectorioStagingResolver resolver = new DirectorioStagingResolver();

    @Test
    void resolver_devuelveDirectorioBajoLocalAppDataYLoCrea() {
        String localAppData = System.getenv("LOCALAPPDATA");
        assumeTrue(localAppData != null && !localAppData.isBlank(), "requiere LOCALAPPDATA definida (entorno Windows)");

        Path directorio = resolver.resolver();

        assertEquals(Path.of(localAppData, "Aptium", "updates"), directorio);
        assertTrue(Files.isDirectory(directorio));
    }
}
