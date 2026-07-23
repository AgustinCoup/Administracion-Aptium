package com.example.features.actualizaciones.service;

import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.exception.ActualizacionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Genera el script PowerShell que reemplaza el fat JAR <em>fuera</em> de la JVM en ejecución.
 *
 * <p>El reemplazo no puede hacerse desde la misma JVM que tiene el JAR abierto (Windows
 * bloquea el archivo) ni asumiendo permiso de escritura sobre la ruta target (puede vivir
 * en una carpeta con ACL restringida). El script resuelve ambos problemas de forma
 * desacoplada: espera a que la JVM termine (libera el lock) y solo eleva privilegios (UAC)
 * para el paso puntual de mover el archivo cuando el reemplazo directo es denegado.
 *
 * <p>Pase lo que pase, el script siempre relanza una JVM al final (la nueva si el reemplazo
 * tuvo éxito, la vieja restaurada desde el backup si falló) para que la app nunca quede cerrada.
 */
public class ScriptDeReemplazoGenerator {

    private static final String NOMBRE_SCRIPT = "reemplazar-aptium.ps1";

    /**
     * Escribe el script de reemplazo en el directorio de staging (junto al JAR descargado).
     *
     * @param pidActual  PID de la JVM en ejecución, que el script espera terminar
     * @param jarStaged  JAR nuevo, ya descargado y verificado, a mover sobre el target
     * @param jarTarget  ruta real del JAR en ejecución que será reemplazado
     * @param javaHome   {@code java.home} de la JVM actual, para relanzar sin depender del PATH
     * @return ruta del script {@code .ps1} generado
     * @throws ActualizacionException si no se puede escribir el script
     */
    public Path generar(long pidActual, Path jarStaged, Path jarTarget, Path javaHome) {
        if (jarStaged == null || jarTarget == null || javaHome == null) {
            throw new IllegalArgumentException("jarStaged, jarTarget y javaHome no pueden ser nulos");
        }
        Path directorioStaging = jarStaged.getParent();
        if (directorioStaging == null) {
            throw new IllegalArgumentException("jarStaged debe tener un directorio padre");
        }
        Path script = directorioStaging.resolve(NOMBRE_SCRIPT);
        Path backup = jarTarget.resolveSibling(jarTarget.getFileName() + ".bak");
        String releaseUrl = "https://github.com/"
            + Constantes.Actualizaciones.GITHUB_OWNER + "/"
            + Constantes.Actualizaciones.GITHUB_REPO + "/releases/latest";

        String contenido = plantilla()
            .replace("__PID__", Long.toString(pidActual))
            .replace("__STAGED__", psLiteral(jarStaged))
            .replace("__TARGET__", psLiteral(jarTarget))
            .replace("__BACKUP__", psLiteral(backup))
            .replace("__JAVA_HOME__", psLiteral(javaHome))
            .replace("__RELEASE_URL__", psEscape(releaseUrl));

        try {
            // Windows PowerShell 5.1 (powershell.exe, no pwsh) solo detecta UTF-8 en un .ps1
            // si el archivo empieza con BOM; sin BOM cae al codepage ANSI del sistema y corrompe
            // cualquier caracter no-ASCII embebido (ej. una tilde en el nombre de usuario del path).
            byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
            byte[] textoUtf8 = contenido.getBytes(StandardCharsets.UTF_8);
            byte[] conBom = new byte[bom.length + textoUtf8.length];
            System.arraycopy(bom, 0, conBom, 0, bom.length);
            System.arraycopy(textoUtf8, 0, conBom, bom.length, textoUtf8.length);
            Files.write(script, conBom);
        } catch (IOException e) {
            throw new ActualizacionException("No se pudo escribir el script de reemplazo: " + script, e);
        }
        return script;
    }

    private String psLiteral(Path ruta) {
        return psEscape(ruta.toAbsolutePath().toString());
    }

    /** Escapa comillas simples para embeber un valor dentro de un literal PowerShell {@code '...'}. */
    private String psEscape(String valor) {
        return valor.replace("'", "''");
    }

    private String plantilla() {
        return """
            # Script de reemplazo del fat JAR de Aptium (generado automáticamente).
            # No editar a mano: se regenera en cada actualización.
            $ErrorActionPreference = 'Stop'

            $pidObjetivo = __PID__
            $staged      = '__STAGED__'
            $target      = '__TARGET__'
            $backup      = '__BACKUP__'
            $javaExe     = Join-Path '__JAVA_HOME__' 'bin\\javaw.exe'
            $releaseUrl  = '__RELEASE_URL__'
            $logPath     = Join-Path $PSScriptRoot 'reemplazo.log'

            function Log($msg) {
                try { "$(Get-Date -Format o)  $msg" | Out-File -FilePath $logPath -Append -Encoding utf8 } catch { }
            }

            function Relanzar-App {
                Start-Process -FilePath $javaExe -ArgumentList '-jar', $target
            }

            Log "=== Inicio del script (PID a esperar: $pidObjetivo) ==="

            # 1. Esperar a que la JVM actual termine y libere el lock sobre el JAR.
            try { Wait-Process -Id $pidObjetivo -ErrorAction SilentlyContinue } catch { }
            Log "Wait-Process terminado"

            # 2. Respaldar el JAR target actual antes de tocarlo.
            try {
                if (Test-Path $target) { Copy-Item -Path $target -Destination $backup -Force }
                Log "Backup ok (existe target: $(Test-Path $target))"
            } catch {
                Log "Backup FALLO: $($_.Exception.Message)"
            }

            # 3. Intento de reemplazo directo (sin elevar). Es el camino feliz cuando el
            #    target vive en una carpeta escribible por el usuario actual.
            #    Move-Item -Force NO sobreescribe un destino que ya existe (limitación
            #    documentada del cmdlet, a diferencia de Copy-Item -Force) — el target
            #    siempre existe acá (es el JAR que se está reemplazando), así que hay que
            #    borrarlo antes de mover, o el Move-Item falla siempre con "ya existe".
            $reemplazado = $false
            try {
                if (Test-Path $target) { Remove-Item -Path $target -Force }
                Move-Item -Path $staged -Destination $target -Force
                $reemplazado = $true
                Log "Move-Item directo: OK"
            } catch {
                Log "Move-Item directo FALLO: $($_.Exception.GetType().FullName): $($_.Exception.Message)"
                # 4. Acceso denegado (ruta protegida): reintentar SOLO el move con elevación UAC.
                #    El proceso elevado no hereda $ErrorActionPreference del script padre, así
                #    que hay que fijarlo explícitamente ahí adentro y devolver el resultado real
                #    por código de salida — de lo contrario un Move-Item fallido igual sale con
                #    ExitCode 0 y el fallo pasa desapercibido.
                try {
                    $comandoMove = "`$ErrorActionPreference = 'Stop'; try { if (Test-Path '$target') { Remove-Item -Path '$target' -Force }; Move-Item -Path '$staged' -Destination '$target' -Force; exit 0 } catch { exit 1 }"
                    $proc = Start-Process powershell -Verb RunAs -Wait -PassThru `
                        -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-Command', $comandoMove
                    Log "Move-Item elevado: ExitCode=$($proc.ExitCode), staged aun existe=$(Test-Path $staged)"
                    if ($proc.ExitCode -eq 0 -and -not (Test-Path $staged)) { $reemplazado = $true }
                } catch {
                    # UAC cancelado o la elevación también falló.
                    Log "Elevacion (Start-Process -Verb RunAs) FALLO: $($_.Exception.Message)"
                    $reemplazado = $false
                }
            }

            Log "Resultado final: reemplazado=$reemplazado"

            if ($reemplazado) {
                # 6. Éxito: limpiar el backup y relanzar la app nueva.
                try { if (Test-Path $backup) { Remove-Item $backup -Force } } catch { }
                Relanzar-App
                exit 0
            } else {
                # 5. Fallback: restaurar el backup si el original quedó tocado, abrir la
                #    página del release para descarga manual, y relanzar la app vieja igual.
                try {
                    if ((Test-Path $backup) -and -not (Test-Path $target)) {
                        Copy-Item -Path $backup -Destination $target -Force
                    }
                } catch { }
                try { Start-Process $releaseUrl } catch { }
                Relanzar-App
                exit 1
            }
            """;
    }
}
