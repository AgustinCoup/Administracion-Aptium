package com.example.features.actualizaciones.service;

import com.example.common.constants.Constantes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica el <em>contenido</em> del script generado (no su ejecución real completa: la
 * espera de proceso, el UAC y el relanzamiento solo se prueban manualmente en Windows, ver
 * la fase). Los comandos elevados sí se ejecutan de verdad en tests puntuales más abajo.
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
        Path lock = instalacion.resolve("aptium.jar.lock");

        Path script = generator.generar(pid, jarStaged, jarTarget, javaHome, lock);

        assertEquals(staging, script.getParent(), "el script debe quedar en el directorio de staging");
        assertTrue(Files.exists(script));

        String contenido = Files.readString(script);
        // Parámetros embebidos
        assertTrue(contenido.contains("12345"), "debe embeber el PID a esperar");
        assertTrue(contenido.contains(jarStaged.toString()), "debe embeber la ruta del JAR staged");
        assertTrue(contenido.contains(jarTarget.toString()), "debe embeber la ruta del JAR target");
        assertTrue(contenido.contains(jarTarget.getFileName() + ".bak"), "debe referenciar el backup .bak");
        assertTrue(contenido.contains(lock.toString()), "debe embeber la ruta del lock de actualización");
        assertTrue(contenido.contains(javaHome.toString()), "debe embeber java.home para relanzar");
        // Pasos del flujo
        assertTrue(contenido.contains("Wait-Process"), "debe esperar a que la JVM actual termine");
        assertTrue(contenido.contains("Move-Item"), "debe mover el JAR staged sobre el target");
        assertTrue(contenido.contains("Remove-Item -Path $target -Force"),
            "debe borrar el target antes de mover: Move-Item -Force no sobreescribe un destino existente");
        assertTrue(contenido.contains("-Verb RunAs"), "debe reintentar el move con elevación UAC");
        assertTrue(contenido.contains("Copy-Item"), "debe respaldar y poder restaurar el JAR original");
        assertTrue(contenido.contains("javaw.exe"), "debe relanzar con javaw.exe (sin consola) al terminar");
        assertEquals(2, contarOcurrencias(contenido, "Remove-Item $lock -Force"),
            "el lock debe liberarse tanto si el reemplazo sale bien como si falla");
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
        Path lock = base.resolve("aptium.jar.lock");

        Path script = generator.generar(7L, jarStaged, jarTarget, javaHome, lock);

        String contenido = Files.readString(script);
        assertTrue(contenido.contains("carpeta''con''comilla"),
            "las comillas simples deben duplicarse para el literal PowerShell");
    }

    @Test
    @DisplayName("escribe el script con BOM UTF-8, requerido por Windows PowerShell 5.1 para no corromper tildes")
    void generar_escribeConBomUtf8(@TempDir Path staging, @TempDir Path instalacion) throws IOException {
        Path script = generator.generar(1L, staging.resolve("aptium-nuevo.jar"),
            instalacion.resolve("aptium.jar"), instalacion.resolve("jdk"), instalacion.resolve("aptium.jar.lock"));

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
        Path lock = base.resolve("aptium.jar.lock");
        assertThrows(IllegalArgumentException.class, () -> generator.generar(1L, null, jar, base, lock));
        assertThrows(IllegalArgumentException.class, () -> generator.generar(1L, jar, null, base, lock));
        assertThrows(IllegalArgumentException.class, () -> generator.generar(1L, jar, base, null, lock));
        assertThrows(IllegalArgumentException.class, () -> generator.generar(1L, jar, base, lock, null));
    }

    @Test
    @DisplayName("el comando elevado de move sobrevive un path con apóstrofe (ejecución real de PowerShell)")
    void generar_comandoMoveElevado_ejecutaConApostrofeEnRuta(@TempDir Path baseCorto) throws Exception {
        String powershell = powerShellDisponible();
        assumeTrue(powershell != null, "requiere powershell o pwsh instalado en el PATH");

        // toRealPath(): en cuentas Windows con tilde en el nombre de usuario, java.io.tmpdir
        // resuelve al alias corto 8.3 (AGUSTN~1), que Remove-Item -Path no siempre parsea bien
        // — un problema del entorno de test ajeno al apóstrofe que este test verifica.
        Path base = baseCorto.toRealPath();
        Path staging = base.resolve("carpeta'con'comilla");
        Files.createDirectories(staging);
        Path jarStaged = staging.resolve("aptium-nuevo.jar");
        Files.writeString(jarStaged, "contenido-nuevo");
        Path jarTarget = base.resolve("aptium.jar");
        Files.writeString(jarTarget, "contenido-viejo");
        Path lock = base.resolve("aptium.jar.lock");

        Path script = generator.generar(1L, jarStaged, jarTarget, base.resolve("jdk"), lock);
        List<String> comandos = extraerComandosCodificados(Files.readString(script));
        assertEquals(2, comandos.size(), "el script debe llevar un comando codificado para move y otro para restore");

        // Se ejecuta el comando elevado directamente (sin -Verb RunAs: la carpeta de test ya
        // es escribible por el usuario actual, no hace falta UAC para probar que el literal
        // generado parsea y hace el move real correctamente).
        Process proceso = ejecutarComandoCodificado(powershell, comandos.get(0));
        assertTrue(proceso.waitFor(30, TimeUnit.SECONDS), "el proceso de PowerShell no debería colgarse");
        assertEquals(0, proceso.exitValue(),
            "el comando elevado debe ejecutar sin errores de parseo con apóstrofe en el path");
        assertEquals("contenido-nuevo", Files.readString(jarTarget), "el move real debe haber sobreescrito el target");
        assertFalse(Files.exists(jarStaged), "el staged debe haberse movido, no copiado");
    }

    @Test
    @DisplayName("el fallback reintenta el restore con elevación y el comando restaura bien (ejecución real)")
    void generar_restoreElevado_reintentaYRestauraCorrectamente(@TempDir Path base) throws Exception {
        String powershell = powerShellDisponible();
        assumeTrue(powershell != null, "requiere powershell o pwsh instalado en el PATH");

        Path jarStaged = base.resolve("staged").resolve("aptium-nuevo.jar");
        Files.createDirectories(jarStaged.getParent());
        Files.writeString(jarStaged, "nuevo");
        Path jarTarget = base.resolve("aptium.jar");
        Path backup = base.resolve("aptium.jar.bak");
        Files.writeString(backup, "contenido-de-backup");
        Path lock = base.resolve("aptium.jar.lock");
        // jarTarget no existe: simula que el move ya lo borró antes de fallar.

        Path script = generator.generar(1L, jarStaged, jarTarget, base.resolve("jdk"), lock);
        String contenido = Files.readString(script);

        assertEquals(2, contarOcurrencias(contenido, "Start-Process powershell -Verb RunAs"),
            "tanto el move como el restore deben poder reintentarse con elevación");
        List<String> comandos = extraerComandosCodificados(contenido);
        assertEquals(2, comandos.size());

        Process proceso = ejecutarComandoCodificado(powershell, comandos.get(1));
        assertTrue(proceso.waitFor(30, TimeUnit.SECONDS), "el proceso de PowerShell no debería colgarse");
        assertEquals(0, proceso.exitValue(), "el comando de restore debe ejecutar sin errores de parseo");
        assertEquals("contenido-de-backup", Files.readString(jarTarget),
            "debe haber restaurado el backup sobre el target ausente");
    }

    /** Busca un ejecutable de PowerShell en el PATH; devuelve {@code null} si no hay ninguno. */
    private static String powerShellDisponible() {
        for (String candidato : new String[] {"pwsh", "powershell"}) {
            try {
                Process p = new ProcessBuilder(candidato, "-NoProfile", "-Command", "1")
                    .redirectErrorStream(true).start();
                if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidato;
                }
            } catch (IOException | InterruptedException ignored) {
                // Ejecutable no disponible en este entorno: se prueba el siguiente candidato.
            }
        }
        return null;
    }

    /** Extrae, en orden, los payloads Base64 pasados a {@code -EncodedCommand} en el script generado. */
    private static List<String> extraerComandosCodificados(String contenidoScript) {
        Matcher m = Pattern.compile("-EncodedCommand',\\s*'([A-Za-z0-9+/=]+)'").matcher(contenidoScript);
        List<String> resultado = new ArrayList<>();
        while (m.find()) {
            resultado.add(m.group(1));
        }
        return resultado;
    }

    private static long contarOcurrencias(String texto, String buscado) {
        return Pattern.compile(Pattern.quote(buscado)).matcher(texto).results().count();
    }

    private static Process ejecutarComandoCodificado(String powershell, String base64) throws IOException {
        return new ProcessBuilder(powershell, "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-EncodedCommand", base64)
            .redirectErrorStream(true)
            .start();
    }
}
