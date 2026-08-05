package com.example.features.actualizaciones.service;

import com.example.common.VersionInfo;
import com.example.features.actualizaciones.model.ReleaseInfo;
import com.example.features.actualizaciones.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Único punto de entrada público del feature de auto-actualización.
 * Compone internamente todos los colaboradores del flujo (chequeo → descarga →
 * verificación → instalación) para que la UI nunca orqueste el orden de los pasos,
 * ni dependa directamente de {@link DescargaService} o {@link ActualizacionInstaller}.
 */
public class ActualizacionService {

    private static final Logger log = LoggerFactory.getLogger(ActualizacionService.class);

    private final IReleaseRepository releaseRepository;
    private final VersionInfo versionInfo;
    private final DescargaService descargaService;
    private final ActualizacionInstaller installer;

    public ActualizacionService(
        IReleaseRepository releaseRepository,
        VersionInfo versionInfo,
        DescargaService descargaService,
        ActualizacionInstaller installer
    ) {
        if (releaseRepository == null) {
            throw new IllegalArgumentException("IReleaseRepository no puede ser nulo");
        }
        if (versionInfo == null) {
            throw new IllegalArgumentException("VersionInfo no puede ser nulo");
        }
        if (descargaService == null) {
            throw new IllegalArgumentException("DescargaService no puede ser nulo");
        }
        if (installer == null) {
            throw new IllegalArgumentException("ActualizacionInstaller no puede ser nulo");
        }
        this.releaseRepository = releaseRepository;
        this.versionInfo = versionInfo;
        this.descargaService = descargaService;
        this.installer = installer;
    }

    /**
     * @return el release si es más nuevo que la versión actual, vacío si no hay novedades
     * @throws com.example.features.actualizaciones.exception.ActualizacionException si falla el chequeo
     */
    public Optional<ReleaseInfo> hayActualizacionDisponible() {
        ReleaseInfo release = releaseRepository.obtenerUltimoRelease();
        Optional<Version> disponible = parsearVersion(release.tag(), "tag del release");
        if (disponible.isEmpty()) {
            return Optional.empty();
        }
        // Sin un app.version numérico embebido (builds locales de desarrollo, o cualquier
        // JAR instalado antes de que esta feature existiera) no hay con qué comparar: se
        // trata la versión actual como desconocida y se ofrece igual el último release,
        // en vez de romper el chequeo entero con un error de formato.
        Optional<Version> actual = parsearVersion(versionInfo.actual(), "versión actual");
        boolean hayActualizacion = actual.isEmpty() || disponible.get().compareTo(actual.get()) > 0;
        return hayActualizacion ? Optional.of(release) : Optional.empty();
    }

    private Optional<Version> parsearVersion(String texto, String contexto) {
        try {
            return Optional.of(Version.parse(texto));
        } catch (IllegalArgumentException e) {
            log.warn("No se pudo interpretar la {} ('{}') como versión, se trata como desconocida", contexto, texto);
            return Optional.empty();
        }
    }

    /**
     * Descarga el JAR del release y verifica su checksum.
     *
     * @param release        release a descargar, ya confirmado por el usuario
     * @param onProgreso     callback de bytes descargados; puede ser {@code null}
     * @return ruta del JAR descargado y verificado, listo para instalar
     * @throws com.example.features.actualizaciones.exception.ActualizacionException si falla la descarga o el checksum no coincide
     */
    public Path descargarActualizacion(ReleaseInfo release, Consumer<Long> onProgreso) {
        return descargaService.descargarYVerificar(release, onProgreso);
    }

    /**
     * Dispara el reemplazo del JAR y cierra la app para que el script externo lo reinicie.
     * Único método del flujo que termina la JVM — solo debe llamarse tras la confirmación
     * final del usuario, avisando antes que la app va a cerrarse.
     *
     * @param jarVerificado JAR ya descargado y verificado (ver {@link #descargarActualizacion})
     * @throws com.example.features.actualizaciones.exception.ActualizacionException si no se puede lanzar el instalador (la app NO se cierra)
     */
    public void instalarActualizacion(Path jarVerificado) {
        installer.instalarYReiniciar(jarVerificado);
    }
}
