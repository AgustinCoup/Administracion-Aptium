package com.example.features.equipos.otros.view.helpers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.*;

class PanelMaterialesOtrosTest {

    private PanelMaterialesOtros panel;

    @BeforeEach
    void setUp() {
        panel = new PanelMaterialesOtros(new Font(Font.SANS_SERIF, Font.PLAIN, 12), 24);
    }

    @Test
    void unaSolaFila_noHayDuplicados() {
        panel.getFilas().get(0).txtDescripcion.setText("Guante");

        assertFalse(panel.tieneDuplicados());
    }

    @Test
    void dosFilasConMismaDescripcionExacta_seDetectanComoDuplicados() {
        panel.agregarFila();
        panel.getFilas().get(0).txtDescripcion.setText("Guante");
        panel.getFilas().get(1).txtDescripcion.setText("Guante");

        assertTrue(panel.tieneDuplicados());
    }

    @Test
    void dosFilasConDistintaMayusculaYEspacios_seDetectanComoDuplicados() {
        panel.agregarFila();
        panel.getFilas().get(0).txtDescripcion.setText("Guante");
        panel.getFilas().get(1).txtDescripcion.setText("  guante ");

        assertTrue(panel.tieneDuplicados());
    }

    @Test
    void dosFilasConDescripcionDistinta_noSonDuplicados() {
        panel.agregarFila();
        panel.getFilas().get(0).txtDescripcion.setText("Guante");
        panel.getFilas().get(1).txtDescripcion.setText("Bisturi");

        assertFalse(panel.tieneDuplicados());
    }

    @Test
    void filasVacias_noSeConsideranDuplicadas() {
        panel.agregarFila();

        assertFalse(panel.tieneDuplicados());
    }
}
