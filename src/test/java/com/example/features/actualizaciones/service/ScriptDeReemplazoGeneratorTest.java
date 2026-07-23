package com.example.features.actualizaciones.service;

import com.example.common.constants.Constantes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica el <em>contenido</em> del script generado (no su ejecución real: la espera de
 * proceso, el UAC y el relanzamiento solo se prueban manualmente en Windows, ver la fase).
 */
class ScriptDeReemplazoGeneratorTest {

    private final ScriptDeReemplazoGenerator generator = new ScriptDeReemplazoGenerator();

    @Test
    @DisplayName("genera el script junto al JAR staged y con todos los pasos clave")
    void generar_produceScriptConLosPasosClave(@TempDir Path staging, @TempDir Path instalacion) throws IOException {
        long pid = 12345L;
        Path jarStaged = staging.resolve("aptium-v9.9.9.jar");
        Path jarTarget = instalacion.resolve("aptium.jar");
        Path javaHome = instalacion.resolve("jdk-17");

        Path script = generator.generar(pid, jarStaged, jarTarget, javaHome);

        assertEquals(staging, script.getParent(), "el script debe quedar en el directorio de staging");
        assertTrue(Files.exists(script));

        String contenido = Files.readString(script);
        // Parámetros embebidos
        assertTrue(contenido.contains("12345"), "debe embeber el PID a esperar");
        assertTrue(contenido.contains(jarStaged.toString()), "debe embeber la ruta del JAR staged");
        assertTrue(contenido.contains(jarTarget.toString()), "debe embeber la ruta del JAR target");
        assertTrue(contenido.contains(jarTarget.getFileName() + ".bak"), "debe referenciar el backup .bak");
        assertTrue(contenido.contains(javaHome.toString()), "debe embeber java.home para relanzar");
        // Pasos del flujo
        assertTrue(contenido.contains("Wait-Process"), "debe esperar a que la JVM actual termine");
        assertTrue(contenido.contains("Move-Item"), "debe mover el JAR staged sobre el target");
        assertTrue(contenido.contains("Remove-Item -Path $target -Force"),
            "debe borrar el target antes de mover: Move-Item -Force no sobreescribe un destino existente");
        assertTrue(contenido.contains("-Verb RunAs"), "debe reintentar el move con elevación UAC");
        assertTrue(contenido.contains("Copy-Item"), "debe respaldar y poder restaurar el JAR original");
        assertTrue(contenido.contains("javaw.exe"), "debe relanzar con javaw.exe (sin consola) al terminar");
        // Fallback manual: página del release
        String releaseUrl = "https://github.com/"
            + Constantes.Actualizaciones.GITHUB_OWNER + "/"
            + Constantes.Actualizaciones.GITHUB_REPO + "/releases/latest";
        assertTrue(contenido.contains(releaseUrl), "debe abrir la página del release como fallback");
    }

    @Test
    @DisplayName("escapa comillas simples en las rutas para no romper los literales PowerShell")
    void generar_escapaComillasSimplesEnRutas(@TempDir Path base) throws IOException {
        Path staging = base.resolve("carpeta'con'comilla");
        Files.createDirectories(staging);
        Path jarStaged = staging.resolve("aptium-nuevo.jar");
        Path jarTarget = base.resolve("aptium.jar");
        Path javaHome = base.resolve("jdk");

        Path script = generator.generar(7L, jarStaged, jarTarget, javaHome);

        String contenido = Files.readString(script);
        assertTrue(contenido.contains("carpeta''con''comilla"),
            "las comillas simples deben duplicarse para el literal PowerShell");
    }

    @Test
    @DisplayName("escribe el script con BOM UTF-8, requerido por Windows PowerShell 5.1 para no corromper tildes")
    void generar_escribeConBomUtf8(@TempDir Path staging, @TempDir Path instalacion) throws IOException {
        Path script = generator.generar(1L, staging.resolve("aptium-nuevo.jar"),
            instalacion.resolve("aptium.jar"), instalacion.resolve("jdk"));

        byte[] primerosBytes = Files.readAllBytes(script);
        assertTrue(primerosBytes.length >= 3, "el script no puede estar vacío");
        assertEquals((byte) 0xEF, primerosBytes[0]);
        assertEquals((byte) 0xBB, primerosBytes[1]);
        assertEquals((byte) 0xBF, primerosBytes[2]);
    }

    @Test
    @DisplayName("rechaza argumentos nulos")
    void generar_argumentosNulos_lanzaIllegalArgument(@TempDir Path base) {
        Path jar = base.resolve("aptium.jar");
        assertThrows(IllegalArgumentException.class, () -> generator.generar(1L, null, jar, base));
        assertThrows(IllegalArgumentException.class, () -> generator.generar(1L, jar, null, base));
        assertThrows(IllegalArgumentException.class, () -> generator.generar(1L, jar, base, null));
    }
}
