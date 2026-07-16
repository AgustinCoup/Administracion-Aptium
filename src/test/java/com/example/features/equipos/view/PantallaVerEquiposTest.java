package com.example.features.equipos.view;

import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PantallaVerEquiposTest {

    private PantallaVerEquipos panel;

    @BeforeEach
    void setUp() {
        panel = new PantallaVerEquipos(new CardLayout(), new JPanel());
        panel.configurarFiltros(() -> {});
    }

    @Test
    void aplicarFiltroInicial_ocultaEntregadoPeroMuestraElResto() {
        panel.aplicarFiltroInicial();

        List<String> seleccionados = panel.getCmbEstados().getSelectedItems();
        assertFalse(seleccionados.contains(EstadoEquipo.ENTREGADO.getNombre()));
        for (EstadoEquipo estado : EstadoEquipo.values()) {
            if (estado != EstadoEquipo.ENTREGADO) {
                assertTrue(seleccionados.contains(estado.getNombre()));
            }
        }
    }

    @Test
    void aplicarFiltroInicial_sinConfigurarFiltrosPrevio_igualAplicaLaSeleccion() {
        // Regresión: el guard de onCambioRef == null se saltaba también el
        // setSelectedItems, mostrando ENTREGADO si se llamaba antes de configurarFiltros().
        PantallaVerEquipos panelSinConfigurar = new PantallaVerEquipos(new CardLayout(), new JPanel());

        panelSinConfigurar.aplicarFiltroInicial();

        assertFalse(panelSinConfigurar.getCmbEstados().getSelectedItems()
            .contains(EstadoEquipo.ENTREGADO.getNombre()));
    }

    @Test
    void limpiarFiltros_muestraTodosLosEstadosSinExcepcion() {
        panel.aplicarFiltroInicial();
        panel.limpiarFiltros();

        assertTrue(panel.getCmbEstados().getSelectedItems().isEmpty());
    }

    @Test
    void aplicarFiltroInicial_noDisparaElCallback() {
        // Evita el flash de datos viejos: quien navega a la pantalla recarga
        // datos frescos aparte y es quien debe disparar el repintado.
        int[] llamadas = {0};
        panel.configurarFiltros(() -> llamadas[0]++);

        panel.aplicarFiltroInicial();

        assertEquals(0, llamadas[0]);
    }

    @Test
    void aplicarFiltroInicialYNotificar_disparaElCallback() {
        int[] llamadas = {0};
        panel.configurarFiltros(() -> llamadas[0]++);

        panel.aplicarFiltroInicialYNotificar();

        assertEquals(1, llamadas[0]);
    }

    @Test
    void limpiarFiltros_disparaElCallbackUnaSolaVez() {
        // Regresión: JDateChooser.setDate(null) avisa aunque ya fuera null, así
        // que limpiar los dos date choosers sumaba 2 disparos extra del callback
        // antes de la notificación explícita al final de limpiarFiltros().
        int[] llamadas = {0};
        panel.configurarFiltros(() -> llamadas[0]++);

        panel.limpiarFiltros();

        assertEquals(1, llamadas[0]);
    }
}
