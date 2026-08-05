package com.example.ui.common;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class Hotkeys {

    private Hotkeys() {}

    /**
     * Registra Escape en el botón volver dado.
     * Simula un clic sobre el botón, respetando cualquier guard de navegación
     * que se haya configurado sobre él.
     */
    public static void registrarVolver(JButton btnVolver) {
        InputMap  im = btnVolver.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = btnVolver.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "volver-escape");
        am.put("volver-escape", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { btnVolver.doClick(); }
        });
    }

    /**
     * Registra Ctrl+Plus y Ctrl+Minus en el panel dado para agregar/quitar filas
     * de materiales. Incluye los equivalentes del teclado numérico.
     * Los bindings usan WHEN_IN_FOCUSED_WINDOW: se activan mientras la ventana
     * que contiene el panel tenga el foco, sin importar qué componente lo tenga.
     */
    public static void registrarMateriales(JPanel panel, Runnable onAgregar, Runnable onQuitar) {
        InputMap  im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = panel.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS,     InputEvent.CTRL_DOWN_MASK), "agregar-material");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD,      InputEvent.CTRL_DOWN_MASK), "agregar-material");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,    InputEvent.CTRL_DOWN_MASK), "quitar-material");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), "quitar-material");

        am.put("agregar-material", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onAgregar.run(); }
        });
        am.put("quitar-material", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onQuitar.run(); }
        });
    }
}
