package com.example.features.ajustes.controller;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.exception.PasswordIncorrectaException;
import com.example.features.ajustes.view.PanelPasswordEliminacion;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.seguridad.service.PasswordEliminacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Los diálogos ({@code JOptionPane}) tiran {@code HeadlessException} bajo el
 * {@code -Djava.awt.headless=true} del surefire: {@code TareaUI} la contiene en el hilo de UI y acá
 * se verifica todo lo que ocurre <em>antes</em> del diálogo. Sin extensión de Mockito: los
 * stubs los consume un hilo de fondo y el modo estricto daría falsos rojos.
 */
class PasswordAjustesControllerTest {

    private static final int ESPERA_MS = 3000;

    private PantallaAjustes vista;
    private PanelPasswordEliminacion panel;
    private PasswordEliminacionService service;
    private Runnable onCambiar;
    private Runnable onPestanaSeguridad;

    private char[] actual;
    private char[] nueva;
    private char[] repetida;

    @BeforeEach
    void setUp() {
        vista = mock(PantallaAjustes.class);
        panel = mock(PanelPasswordEliminacion.class);
        service = mock(PasswordEliminacionService.class);

        actual = "actual".toCharArray();
        nueva = "nueva1".toCharArray();
        repetida = "nueva1".toCharArray();
        when(panel.getPasswordActual()).thenReturn(actual);
        when(panel.getPasswordNueva()).thenReturn(nueva);
        when(panel.getPasswordRepetida()).thenReturn(repetida);

        ArgumentCaptor<Runnable> click = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> pestana = ArgumentCaptor.forClass(Runnable.class);
        new PasswordAjustesController(vista, panel, service);
        verify(panel).setOnCambiar(click.capture());
        verify(vista).setOnPestanaSeleccionada(eq(PantallaAjustes.TAB_SEGURIDAD), pestana.capture());
        onCambiar = click.getValue();
        onPestanaSeguridad = pestana.getValue();
    }

    private static boolean todoEnCero(char[] c) {
        for (char ch : c) {
            if (ch != '\0') return false;
        }
        return true;
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (!condicion.getAsBoolean() && System.currentTimeMillis() < limite) {
            Thread.sleep(10);
        }
        assertTrue(condicion.getAsBoolean(), "la condición no se cumplió en " + ESPERA_MS + " ms");
    }

    @Test
    void alMostrarLaPestana_leeSiEsInicialYPintaElAviso() {
        when(service.esInicial()).thenReturn(true);

        onPestanaSeguridad.run();

        verify(panel, timeout(ESPERA_MS)).setAvisoInicialVisible(true);
    }

    @Test
    void alMostrarLaPestana_siLaLecturaFalla_apagaElAviso() {
        when(service.esInicial()).thenThrow(new IllegalStateException("base caída"));

        onPestanaSeguridad.run();

        verify(panel, timeout(ESPERA_MS)).setAvisoInicialVisible(false);
    }

    @Test
    void cambiarBien_limpiaLosCamposYRecalculaElAviso() throws Exception {
        when(service.esInicial()).thenReturn(false);

        onCambiar.run();

        verify(service, timeout(ESPERA_MS)).cambiar(any(char[].class), any(char[].class), any(char[].class));
        // atLeastOnce: el diálogo de éxito tira HeadlessException en el test y TareaUI reenvía a
        // siFalla, que también limpia. En producción el diálogo abre y limpia una sola vez.
        verify(panel, timeout(ESPERA_MS).atLeastOnce()).limpiarCampos();
        verify(panel, timeout(ESPERA_MS)).setAvisoInicialVisible(false);
    }

    @Test
    void cambiarBien_limpiaLosTresArraysDespuesDeUsarlos() throws Exception {
        onCambiar.run();

        verify(service, timeout(ESPERA_MS)).cambiar(any(char[].class), any(char[].class), any(char[].class));
        esperar(() -> todoEnCero(actual) && todoEnCero(nueva) && todoEnCero(repetida));
    }

    @Test
    void cambiarConLaActualIncorrecta_limpiaLosArraysYLosCampos_yNoCambiaNada() throws Exception {
        doThrow(new PasswordIncorrectaException(Mensajes.PASSWORD_ACTUAL_INCORRECTA))
            .when(service).cambiar(any(char[].class), any(char[].class), any(char[].class));

        onCambiar.run();

        verify(panel, timeout(ESPERA_MS).atLeastOnce()).limpiarCampos();
        esperar(() -> todoEnCero(actual) && todoEnCero(nueva) && todoEnCero(repetida));
        // Falló antes de tocar el aviso: nada cambió, así que no hay nada que repintar.
        verify(panel, never()).setAvisoInicialVisible(anyBoolean());
    }

    @Test
    void cambiar_serviceSeLlamaConLosArraysDelPanelYNoConCopiasEnString() throws Exception {
        doAnswer(inv -> {
            // Dentro del lambda de leer, antes del finally: siguen con su contenido.
            assertArrayEquals("actual".toCharArray(), inv.getArgument(0));
            assertArrayEquals("nueva1".toCharArray(), inv.getArgument(1));
            assertArrayEquals("nueva1".toCharArray(), inv.getArgument(2));
            return null;
        }).when(service).cambiar(any(char[].class), any(char[].class), any(char[].class));

        onCambiar.run();

        verify(service, timeout(ESPERA_MS)).cambiar(actual, nueva, repetida);
    }

    @Test
    void cambiar_deshabilitaElBotonMientrasTrabajaYLoReactiva() {
        onCambiar.run();

        InOrder orden = inOrder(panel);
        orden.verify(panel, timeout(ESPERA_MS)).setCambiarHabilitado(false);
        orden.verify(panel, timeout(ESPERA_MS)).setCambiarHabilitado(true);
    }

    @Test
    void camposVacios_noLlamaAlServiceYLimpiaLosArrays() {
        char[] vacia = new char[0];
        when(panel.getPasswordRepetida()).thenReturn(vacia);

        try {
            onCambiar.run();
        } catch (RuntimeException dialogoNoDisponible) {
            // El JOptionPane de error no puede abrirse en el test (headless, y el padre es un mock):
            // lo que importa es lo que pasó antes, que se verifica abajo.
        }

        verifyNoInteractions(service);
        assertTrue(todoEnCero(actual));
        assertTrue(todoEnCero(nueva));
    }

    @Test
    void repeticionDistinta_noLlamaAlService() {
        when(panel.getPasswordRepetida()).thenReturn("otra12".toCharArray());

        try {
            onCambiar.run();
        } catch (RuntimeException dialogoNoDisponible) {
            // ver camposVacios_…
        }

        verifyNoInteractions(service);
    }
}
