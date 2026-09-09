package com.example.app;

import com.example.features.actualizaciones.exception.ActualizacionException;
import com.example.features.actualizaciones.model.ReleaseInfo;
import com.example.features.actualizaciones.service.ActualizacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los diálogos de Swing de {@link OfertaActualizacionAlArrancar} no son testeables acá: el
 * {@code JOptionPane} de la oferta dispara {@code HeadlessException} en el entorno de test
 * (mismo motivo que documenta {@code AjustesControllerTest} para el flujo manual del botón). Lo
 * que estas pruebas verifican es la garantía real que le importa a este flujo: sea cual sea el
 * motivo —sin actualización, chequeo caído, o el diálogo reventando en headless—, siempre termina
 * en el fallback y nunca deja al arranque colgado sin avisar.
 */
@ExtendWith(MockitoExtension.class)
class OfertaActualizacionAlArrancarTest {

    @Mock ActualizacionService actualizacionService;

    @Test
    @DisplayName("sin actualización disponible, cae al fallback")
    void sinActualizacion_caeAlFallback() {
        when(actualizacionService.hayActualizacionDisponible()).thenReturn(Optional.empty());
        Runnable fallback = mock(Runnable.class);

        new OfertaActualizacionAlArrancar(actualizacionService, fallback).intentar();

        verify(actualizacionService, timeout(2000)).hayActualizacionDisponible();
        verify(fallback, timeout(2000)).run();
    }

    @Test
    @DisplayName("si el chequeo falla (sin red), cae al fallback")
    void chequeoFalla_caeAlFallback() {
        when(actualizacionService.hayActualizacionDisponible())
            .thenThrow(new ActualizacionException("sin conexión"));
        Runnable fallback = mock(Runnable.class);

        new OfertaActualizacionAlArrancar(actualizacionService, fallback).intentar();

        verify(fallback, timeout(2000)).run();
    }

    @Test
    @DisplayName("con actualización disponible, ofrece instalar y no se cuelga aunque el diálogo falle en headless")
    void hayActualizacion_ofreceInstalar() {
        ReleaseInfo release = new ReleaseInfo(
            "v1.2.3", Map.of("aptium.jar", "https://example.test/aptium.jar"), "changelog");
        when(actualizacionService.hayActualizacionDisponible()).thenReturn(Optional.of(release));
        Runnable fallback = mock(Runnable.class);

        new OfertaActualizacionAlArrancar(actualizacionService, fallback).intentar();

        verify(actualizacionService, timeout(2000)).hayActualizacionDisponible();
        verify(fallback, timeout(2000)).run();
    }
}
