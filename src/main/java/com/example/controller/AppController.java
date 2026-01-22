package com.example.controller;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.example.model.AppModel;
import com.example.view.PantallaPrincipal;

public class AppController {

    private final AppModel model;
    private PantallaPrincipal vista;

    public AppController(AppModel model) {
        this.model = model;
    }

    public void iniciarAplicacion() {
        if (!model.validarConexion()) {
            mostrarErrorConexion();
            return;
        }

        configurarLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            vista = new PantallaPrincipal();
            vista.setVisible(true);
        });
    }

    public PantallaPrincipal getVista() {
        return vista;
    }

    private void configurarLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarErrorConexion() {
        JOptionPane.showMessageDialog(
                null,
                "No se pudo conectar con el servidor de base de datos.\n"
                        + "Por favor, verifique que la PC Servidor esté encendida.",
                "Error de Conexión",
                JOptionPane.ERROR_MESSAGE);
        System.exit(0);
    }
}
