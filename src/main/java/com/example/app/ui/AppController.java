package com.example.app.ui;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.example.common.constants.Constantes;
import com.example.app.AppContext;
import com.example.infrastructure.db.ConnectionPool;
import com.example.ui.common.Estilos;
import com.example.ui.shell.PantallaPrincipal;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    private final AppContext context;
    private PantallaPrincipal vista;

    public AppController(AppContext context) {
        this.context = context;
    }

    public void iniciarAplicacion() {
        // El pool ya fue inicializado en App.main; acá solo se confirma que responde.
        if (!ConnectionPool.validarConexion()) {
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
        UiCoordinator coordinator = new UiCoordinator(context, vista);
        coordinator.inicializar();
        vista.setVisible(true);
    }

    private void configurarLookAndFeel() {
        try {
            FlatLaf.registerCustomDefaultsSource("com.example.app");
            FlatLightLaf.setup();
            configurarFuentesDialogos();
        } catch (Exception e) {
            log.warn("No se pudo aplicar el Look and Feel", e);
        }
    }

    private void configurarFuentesDialogos() {
        UIManager.put("OptionPane.messageFont", Estilos.Fuentes.INPUT);
        UIManager.put("OptionPane.buttonFont", Estilos.Fuentes.INPUT);
        UIManager.put("OptionPane.yesButtonText", Constantes.Botones.SI);
        UIManager.put("OptionPane.noButtonText", Constantes.Botones.NO);
        UIManager.put("OptionPane.cancelButtonText", Constantes.Botones.CANCELAR);
        UIManager.put("OptionPane.okButtonText", Constantes.Botones.ACEPTAR);
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


