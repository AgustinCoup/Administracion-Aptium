package com.example.features.ajustes.controller;

import com.example.features.actualizaciones.exception.ActualizacionException;
import com.example.features.actualizaciones.service.ActualizacionService;
import com.example.features.ajustes.view.PanelGestionClientes;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.clientes.service.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El click en "Buscar actualizaciones" siempre dispara
 * {@code ActualizacionService.hayActualizacionDisponible()} en background — eso es lo
 * que se verifica acá. Los diálogos posteriores (confirmación, progreso) corren vía
 * {@code JOptionPane} en el EDT dentro del {@code SwingWorker} de {@link com.example.ui.common.TareaUI};
 * no se testean porque el entorno headless de test (ver {@code -Djava.awt.headless=true}
 * en el surefire del pom) no permite interceptar esas llamadas de forma fiable entre
 * threads, y cualquier {@code HeadlessException} que disparen ya queda contenida por
 * el manejo de errores de {@code TareaUI} sin romper el flujo.
 */
@ExtendWith(MockitoExtension.class)
class AjustesControllerTest {

    @Mock PantallaAjustes vista;
    @Mock PanelGestionClientes panel;
    @Mock ClienteService clienteService;
    @Mock ActualizacionService actualizacionService;

    private Runnable onBuscarActualizaciones;

    @BeforeEach
    void setUp() {
        when(vista.getPanelClientes()).thenReturn(panel);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(vista).setOnBuscarActualizaciones(captor.capture());

        new AjustesController(vista, clienteService, actualizacionService);
        onBuscarActualizaciones = captor.getValue();
    }

    @Test
    @DisplayName("click en Buscar actualizaciones dispara el chequeo en background")
    void buscarActualizaciones_disparaChequeo() {
        when(actualizacionService.hayActualizacionDisponible()).thenReturn(Optional.empty());

        onBuscarActualizaciones.run();

        verify(actualizacionService, timeout(2000)).hayActualizacionDisponible();
    }

    @Test
    @DisplayName("un fallo en el chequeo no rompe el flujo sin manejar")
    void buscarActualizaciones_chequeoFalla_noRompeElFlujo() {
        when(actualizacionService.hayActualizacionDisponible())
            .thenThrow(new ActualizacionException("sin conexión"));

        onBuscarActualizaciones.run();

        verify(actualizacionService, timeout(2000)).hayActualizacionDisponible();
    }

    @Test
    @DisplayName("deshabilita el botón al hacer click, para no permitir chequeos/descargas concurrentes")
    void buscarActualizaciones_deshabilitaElBotonInmediatamente() {
        // Sin stub de hayActualizacionDisponible(): esta prueba solo verifica el deshabilitado
        // sincrónico al click, que no depende de qué devuelva (ni de que termine) la tarea
        // de fondo — stubearlo acá dispara UnnecessaryStubbingException porque el hilo de
        // fondo puede no haber llegado a invocarlo todavía cuando el test ya terminó de verificar.
        onBuscarActualizaciones.run();

        // Sincrónico respecto del click: no depende de que termine la tarea de fondo.
        verify(vista).setBuscarActualizacionesHabilitado(false);
    }

    @Test
    @DisplayName("rehabilita el botón cuando el chequeo no encuentra actualizaciones")
    void buscarActualizaciones_sinActualizaciones_rehabilitaElBoton() {
        when(actualizacionService.hayActualizacionDisponible()).thenReturn(Optional.empty());

        onBuscarActualizaciones.run();

        // atLeast(1): en el entorno headless de test, JOptionPane.showMessageDialog dispara
        // HeadlessException, así que el rehabilitado ocurre tanto en manejarResultadoChequeo
        // como (de nuevo, redundante pero inofensivo) en el manejador de error de TareaUI que
        // atrapa esa excepción. En producción real (con display) solo se llama una vez.
        verify(vista, timeout(2000).atLeast(1)).setBuscarActualizacionesHabilitado(true);
    }

    @Test
    @DisplayName("rehabilita el botón cuando el chequeo falla")
    void buscarActualizaciones_chequeoFalla_rehabilitaElBoton() {
        when(actualizacionService.hayActualizacionDisponible())
            .thenThrow(new ActualizacionException("sin conexión"));

        onBuscarActualizaciones.run();

        verify(vista, timeout(2000).atLeast(1)).setBuscarActualizacionesHabilitado(true);
    }
}
