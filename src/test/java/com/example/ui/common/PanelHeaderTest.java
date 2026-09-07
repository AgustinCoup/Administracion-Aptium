package com.example.ui.common;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sólo cubre lo que corre headless: visibilidad del botón "Actualizar", que
 * {@code setAccionRefrescar} lo muestre y dispare la acción, y el formato del
 * cartelito. Ningún test abre un {@code JOptionPane} real (la rama con guard y
 * pendientes se prueba en {@link GuardaRefrescoTest}).
 */
class PanelHeaderTest {

    @Test
    void botonActualizar_ocultoPorDefecto() throws Exception {
        PanelHeader header = new PanelHeader("Título");

        assertFalse(boton(header, "btnRefrescar").isVisible());
        assertFalse(label(header, "lblActualizado").isVisible());
    }

    @Test
    void setAccionRefrescar_muestraElBotonYLoCableaALaAccion() throws Exception {
        PanelHeader header = new PanelHeader("Título");
        int[] corridas = {0};

        header.setAccionRefrescar(() -> corridas[0]++);

        JButton btn = boton(header, "btnRefrescar");
        assertTrue(btn.isVisible());
        btn.doClick();
        assertEquals(1, corridas[0]);
    }

    @Test
    void setGuardRefresco_sinPendientes_dejaPasarLaAccion() throws Exception {
        PanelHeader header = new PanelHeader("Título");
        int[] corridas = {0};
        header.setAccionRefrescar(() -> corridas[0]++);
        header.setGuardRefresco(() -> false, "mensaje", null);

        boton(header, "btnRefrescar").doClick();

        assertEquals(1, corridas[0]);
    }

    @Test
    void marcarActualizado_muestraElLabelConFormatoHoraMinuto() throws Exception {
        PanelHeader header = new PanelHeader("Título");

        header.marcarActualizado();

        JLabel lbl = label(header, "lblActualizado");
        assertTrue(lbl.isVisible());
        assertTrue(lbl.getText().matches("Actualizado \\d{2}:\\d{2}"),
            "texto inesperado: " + lbl.getText());
    }

    private static JButton boton(PanelHeader header, String campo) throws Exception {
        return (JButton) leer(header, campo);
    }

    private static JLabel label(PanelHeader header, String campo) throws Exception {
        return (JLabel) leer(header, campo);
    }

    private static Object leer(PanelHeader header, String campo) throws Exception {
        Field f = PanelHeader.class.getDeclaredField(campo);
        f.setAccessible(true);
        return f.get(header);
    }
}
