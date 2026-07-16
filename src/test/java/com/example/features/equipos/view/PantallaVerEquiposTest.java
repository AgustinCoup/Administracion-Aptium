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
    void limpiarFiltros_muestraTodosLosEstadosSinExcepcion() {
        panel.aplicarFiltroInicial();
        panel.limpiarFiltros();

        assertTrue(panel.getCmbEstados().getSelectedItems().isEmpty());
    }
}
