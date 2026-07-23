package com.example.features.actualizaciones.service;

import com.example.common.VersionInfo;
import com.example.features.actualizaciones.exception.ActualizacionException;
import com.example.features.actualizaciones.model.ReleaseInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActualizacionServiceTest {

    @Mock IReleaseRepository releaseRepository;
    @Mock VersionInfo versionInfo;
    @Mock DescargaService descargaService;
    @Mock ActualizacionInstaller installer;

    private ActualizacionService crearService() {
        return new ActualizacionService(releaseRepository, versionInfo, descargaService, installer);
    }

    @Test
    void hayActualizacionDisponible_releaseMasNuevo_devuelveOptionalPresente() {
        when(versionInfo.actual()).thenReturn("1.0.0");
        ReleaseInfo release = new ReleaseInfo("v1.1.0", Map.of(), "changelog");
        when(releaseRepository.obtenerUltimoRelease()).thenReturn(release);

        Optional<ReleaseInfo> resultado = crearService().hayActualizacionDisponible();

        assertTrue(resultado.isPresent());
        assertEquals(release, resultado.get());
    }

    @Test
    void hayActualizacionDisponible_releaseIgual_devuelveOptionalVacio() {
        when(versionInfo.actual()).thenReturn("1.0.0");
        when(releaseRepository.obtenerUltimoRelease())
            .thenReturn(new ReleaseInfo("v1.0.0", Map.of(), "changelog"));

        assertTrue(crearService().hayActualizacionDisponible().isEmpty());
    }

    @Test
    void hayActualizacionDisponible_releaseMasViejo_devuelveOptionalVacio() {
        when(versionInfo.actual()).thenReturn("2.0.0");
        when(releaseRepository.obtenerUltimoRelease())
            .thenReturn(new ReleaseInfo("v1.9.9", Map.of(), "changelog"));

        assertTrue(crearService().hayActualizacionDisponible().isEmpty());
    }

    @Test
    void hayActualizacionDisponible_repositorioLanzaExcepcion_sePropaga() {
        when(releaseRepository.obtenerUltimoRelease())
            .thenThrow(new ActualizacionException("Error de red"));

        assertThrows(ActualizacionException.class, () -> crearService().hayActualizacionDisponible());
    }

    @Test
    void constructor_releaseRepositoryNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ActualizacionService(null, versionInfo, descargaService, installer));
    }

    @Test
    void constructor_versionInfoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ActualizacionService(releaseRepository, null, descargaService, installer));
    }

    @Test
    void constructor_descargaServiceNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ActualizacionService(releaseRepository, versionInfo, null, installer));
    }

    @Test
    void constructor_installerNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ActualizacionService(releaseRepository, versionInfo, descargaService, null));
    }

    @Test
    void descargarActualizacion_delegaEnDescargaService() {
        ReleaseInfo release = new ReleaseInfo("v1.1.0", Map.of(), "changelog");
        Path esperado = Path.of("C:\\staging\\aptium-v1.1.0.jar");
        Consumer<Long> onProgreso = bytes -> { };
        when(descargaService.descargarYVerificar(release, onProgreso)).thenReturn(esperado);

        Path resultado = crearService().descargarActualizacion(release, onProgreso);

        assertEquals(esperado, resultado);
    }

    @Test
    void instalarActualizacion_delegaEnInstaller() {
        Path jarVerificado = Path.of("C:\\staging\\aptium-v1.1.0.jar");

        crearService().instalarActualizacion(jarVerificado);

        verify(installer).instalarYReiniciar(jarVerificado);
    }
}
