package com.example.app.ui;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.example.common.constants.Constantes;
import com.example.app.AppModel;
import com.example.ui.shell.PantallaPrincipal;

public class AppController {

    private final AppModel model;
    private PantallaPrincipal vista;

    public AppController(AppModel model) {
        this.model = model;
    }

    public void iniciarAplicacion() {
        // Validar conexión y inicializar BD (el model se encarga de la inicialización)
        if (!model.validarConexion()) {
            mostrarErrorConexion();
            return;
        }

        configurarLookAndFeel();
        SwingUtilities.invokeLater(this::inicializarVista);
    }

    public PantallaPrincipal getVista() {
        return vista;
    }

    private void inicializarVista() {
        vista = new PantallaPrincipal();
        UiCoordinator coordinator = new UiCoordinator(model, vista);
        coordinator.inicializar();
        vista.setVisible(true);
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
                Constantes.Mensajes.ERROR_CONEXION_BD,
                Constantes.Mensajes.TITULO_ERROR_CONEXION,
                JOptionPane.ERROR_MESSAGE);
        System.exit(0);
    }
}


