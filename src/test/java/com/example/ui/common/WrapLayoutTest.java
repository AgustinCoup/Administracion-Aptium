package com.example.ui.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El defecto que ataja esta clase es <b>un botón que no se dibuja</b>, y un botón que no se dibuja
 * sigue existiendo para {@code isShowing()} y para cualquier test que lo busque por getter. Por eso
 * lo que se afirma acá es la <b>altura declarada</b>: es el único número que separa "envuelve y se
 * ve" de "envuelve y se pierde abajo del recorte".
 *
 * <p>Los hijos llevan tamaño preferido fijo a propósito — con botones reales el resultado
 * dependería de las fuentes instaladas en la máquina que corre los tests.
 *
 * <p><b>Lo que estos tests NO cubren:</b> la revalidación que hace la fábrica
 * {@code WrapLayout.panel(...)}. Esa parte sólo se manifiesta en una pasada de layout completa, y
 * {@code Container.validate()} no hace nada sin peer, o sea sin pantalla. Verificada a mano contra
 * {@code PantallaSalidasLavadero} a 1280×720, 1600×900 y 1920×1030.
 */
class WrapLayoutTest {

    private static final int ANCHO_HIJO = 100;
    private static final int ALTO_HIJO  = 40;
    private static final int HGAP = 10;
    private static final int VGAP = 5;

    private static JComponent hijo() {
        JPanel c = new JPanel();
        c.setPreferredSize(new Dimension(ANCHO_HIJO, ALTO_HIJO));
        return c;
    }

    private static JPanel panelCon(int ancho, int cantidadDeHijos) {
        JPanel panel = WrapLayout.panel(FlowLayout.RIGHT, HGAP, VGAP);
        for (int i = 0; i < cantidadDeHijos; i++) {
            panel.add(hijo());
        }
        panel.setSize(ancho, 1);
        return panel;
    }

    @Test
    @DisplayName("tres hijos que no entran a lo ancho declaran la altura de las dos filas")
    void tresHijosQueNoEntran_declaranDosFilas() {
        // Arrange — 320 px de contenido (100+10+100+10+100) contra 260 de ancho útil
        JPanel panel = panelCon(280, 3);

        // Act
        int alto = panel.getPreferredSize().height;

        // Assert — dos filas de 40 + el vgap entre ellas + el vgap de arriba y abajo
        assertEquals(ALTO_HIJO * 2 + VGAP + VGAP * 2, alto);
    }

    @Test
    @DisplayName("los mismos tres hijos en un panel ancho siguen siendo una sola fila")
    void tresHijosQueEntran_siguenEnUnaFila() {
        JPanel panel = panelCon(600, 3);

        assertEquals(ALTO_HIJO + VGAP * 2, panel.getPreferredSize().height);
    }

    @Test
    @DisplayName("antes de tener ancho asume una fila, que es el default de FlowLayout")
    void sinAnchoTodavia_asumeUnaFila() {
        JPanel panel = WrapLayout.panel(FlowLayout.RIGHT, HGAP, VGAP);
        for (int i = 0; i < 3; i++) {
            panel.add(hijo());
        }

        assertEquals(ALTO_HIJO + VGAP * 2, panel.getPreferredSize().height);
    }
}
