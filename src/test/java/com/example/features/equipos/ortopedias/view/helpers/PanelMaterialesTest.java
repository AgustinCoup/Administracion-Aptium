package com.example.features.equipos.ortopedias.view.helpers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.*;

class PanelMaterialesTest {

    private PanelMateriales panel;

    @BeforeEach
    void setUp() {
        panel = new PanelMateriales(new Font(Font.SANS_SERIF, Font.PLAIN, 12), 24);
    }

    @Test
    void unaSolaFila_noHayDuplicados() {
        panel.getMaterialRows().get(0).numero.setText("400");

        assertFalse(panel.tieneDuplicados());
    }

    @Test
    void dosFilasConMismoCodigo_seDetectanComoDuplicados() {
        panel.agregarFilaMaterial();
        panel.getMaterialRows().get(0).numero.setText("400");
        panel.getMaterialRows().get(1).numero.setText("400");

        assertTrue(panel.tieneDuplicados());
    }

    @Test
    void dosFilasConCodigoDistinto_noSonDuplicados() {
        panel.agregarFilaMaterial();
        panel.getMaterialRows().get(0).numero.setText("400");
        panel.getMaterialRows().get(1).numero.setText("401");

        assertFalse(panel.tieneDuplicados());
    }
}
