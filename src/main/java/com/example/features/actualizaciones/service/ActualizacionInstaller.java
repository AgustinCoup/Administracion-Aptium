package com.example.features.actualizaciones.service;

import com.example.features.actualizaciones.exception.ActualizacionException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Dispara el reemplazo del fat JAR y el reinicio de la app.
 *
 * <p>Compone {@link RutaJarResolver} (dónde está el JAR a reemplazar) y
 * {@link ScriptDeReemplazoGenerator} (qué script hará el trabajo fuera de la JVM),
 * lanza el script como proceso desacoplado y cierra la app con {@code System.exit(0)}.
 *
 * <p>Es el único punto de la app que decide cerrarse por una actualización: solo debe
 * invocarse desde el flujo de instalación confirmado por el usuario (Fase 4). Si el
 * lanzamiento del script falla, se propaga {@link ActualizacionException} <em>sin</em>
 * cerrar la app, para que nunca quede terminada sin un mecanismo de reemplazo corriendo.
 */
public class ActualizacionInstaller {

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
     * @throws ActualizacionException si no se puede generar o lanzar el script (la app NO se cierra)
     */
    public void instalarYReiniciar(Path jarVerificado) {
        if (jarVerificado == null) {
            throw new IllegalArgumentException("jarVerificado no puede ser nulo");
        }
        long pid = ProcessHandle.current().pid();
        Path jarTarget = rutaJarResolver.resolverJarActual();
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path script = scriptGenerator.generar(pid, jarVerificado, jarTarget, javaHome);

        lanzarScriptDesacoplado(script);
        System.exit(0);
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
