package com.example.app.ui;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.example.common.constants.Constantes;
import com.example.app.AppModel;
import com.example.ui.common.Estilos;
import com.example.ui.shell.PantallaPrincipal;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

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
            FlatArcOrangeIJTheme.setup();
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


