package com.example.features.actualizaciones.service;

import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.exception.ActualizacionException;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Dispara el reemplazo del fat JAR y el reinicio de la app.
 *
 * <p>Compone {@link RutaJarResolver} (dónde está el JAR a reemplazar) y
 * {@link ScriptDeReemplazoGenerator} (qué script hará el trabajo fuera de la JVM),
 * lanza el script como proceso desacoplado y cierra la app con {@code System.exit(0)}.
 *
 * <p>Antes de arrancar, toma un lock de archivo (en el directorio de staging, ver
 * {@link RutaJarResolver#resolverLockActualizacion()}) para que dos reemplazos no corran
 * a la vez — ej. dos clicks del botón, o dos instancias de la app.
 * El lock lo libera el script generado (que sigue vivo después de que esta JVM cierra), o
 * este mismo método si falla antes de llegar a lanzarlo. Si el lock ya existe y es reciente,
 * la instalación se rechaza; si es viejo (una corrida anterior que no lo limpió, ej. por un
 * crash), se recicla en vez de bloquear la actualización para siempre.
 *
 * <p>Es el único punto de la app que decide cerrarse por una actualización: solo debe
 * invocarse desde el flujo de instalación confirmado por el usuario (Fase 4). Si el
 * lanzamiento del script falla, se propaga {@link ActualizacionException} <em>sin</em>
 * cerrar la app, para que nunca quede terminada sin un mecanismo de reemplazo corriendo.
 */
public class ActualizacionInstaller {

    private static final Duration LOCK_OBSOLETO_DESPUES =
        Duration.ofMinutes(Constantes.Actualizaciones.LOCK_OBSOLETO_MINUTOS);

    private final RutaJarResolver rutaJarResolver;
    private final ScriptDeReemplazoGenerator scriptGenerator;

    public ActualizacionInstaller() {
        this(new RutaJarResolver(), new ScriptDeReemplazoGenerator());
    }

    public ActualizacionInstaller(RutaJarResolver rutaJarResolver, ScriptDeReemplazoGenerator scriptGenerator) {
        if (rutaJarResolver == null || scriptGenerator == null) {
            throw new IllegalArgumentException("rutaJarResolver y scriptGenerator no pueden ser nulos");
        }
        this.rutaJarResolver = rutaJarResolver;
        this.scriptGenerator = scriptGenerator;
    }

    /**
     * Genera y lanza el script de reemplazo, luego cierra la JVM para liberar el JAR.
     *
     * @param jarVerificado JAR nuevo, ya descargado y verificado (ver {@link DescargaService})
     * @throws ActualizacionException si ya hay una actualización en curso, o si no se puede
     *     generar o lanzar el script (la app NO se cierra en ningún caso de error)
     */
    public void instalarYReiniciar(Path jarVerificado) {
        if (jarVerificado == null) {
            throw new IllegalArgumentException("jarVerificado no puede ser nulo");
        }
        Path jarTarget = rutaJarResolver.resolverJarActual();
        Path lock = rutaJarResolver.resolverLockActualizacion();
        adquirirLock(lock);

        try {
            long pid = ProcessHandle.current().pid();
            Path javaHome = Path.of(System.getProperty("java.home"));
            Path script = scriptGenerator.generar(pid, jarVerificado, jarTarget, javaHome, lock);
            lanzarScriptDesacoplado(script);
        } catch (RuntimeException e) {
            liberarLock(lock);
            throw e;
        }
        System.exit(0);
    }

    /**
     * Crea el lock de forma atómica para que dos instalaciones concurrentes no se pisen.
     * Si ya existe y es reciente, se rechaza la instalación; si es más viejo que
     * {@link #LOCK_OBSOLETO_DESPUES}, se asume abandonado por una corrida anterior y se recicla.
     */
    private void adquirirLock(Path lock) {
        try {
            Files.createFile(lock);
            return;
        } catch (FileAlreadyExistsException e) {
            if (!esLockObsoleto(lock)) {
                throw new ActualizacionException("Ya hay una actualización en curso (lock: " + lock + ")");
            }
        } catch (IOException e) {
            throw new ActualizacionException("No se pudo crear el lock de actualización: " + lock, e);
        }
        try {
            Files.deleteIfExists(lock);
            Files.createFile(lock);
        } catch (IOException e) {
            throw new ActualizacionException(
                "Ya hay una actualización en curso (lock obsoleto, no se pudo reciclar: " + lock + ")", e);
        }
    }

    private boolean esLockObsoleto(Path lock) {
        try {
            Instant modificado = Files.getLastModifiedTime(lock).toInstant();
            return Instant.now().isAfter(modificado.plus(LOCK_OBSOLETO_DESPUES));
        } catch (IOException e) {
            return false;
        }
    }

    private void liberarLock(Path lock) {
        try {
            Files.deleteIfExists(lock);
        } catch (IOException ignored) {
            // Best-effort: si no se puede borrar, el próximo intento lo tratará como obsoleto
            // pasado el timeout en vez de quedar bloqueado para siempre.
        }
    }

    /**
     * Lanza el script en un proceso PowerShell oculto, sin heredar streams, de modo que
     * sobreviva al cierre de esta JVM. Se lanza directo (sin {@code cmd /c start}) para
     * evitar el flash de consola que produce ese intermediario.
     */
    private void lanzarScriptDesacoplado(Path script) {
        ProcessBuilder pb = new ProcessBuilder(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden", "-File", script.toAbsolutePath().toString());
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("NUL")));
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            pb.start();
        } catch (IOException e) {
            throw new ActualizacionException("No se pudo lanzar el script de reemplazo del JAR", e);
        }
    }
}
