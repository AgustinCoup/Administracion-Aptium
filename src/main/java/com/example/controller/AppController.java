package com.example.controller;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.example.constants.Constantes;
import com.example.controller.listeners.OnEquipoGuardadoListener;
import com.example.controller.listeners.OnEstadosActualizadosListener;
import com.example.model.AppModel;
import com.example.view.PantallaPrincipal;

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
        SwingUtilities.invokeLater(() -> {
            vista = new PantallaPrincipal();
            
            // Controller para PantallaVerCDEv2 - carga datos del modelo
            CDEViewController cdeViewController = new CDEViewController(
                vista.getPantallaVerCDEv2(),
                model
            );
            
            // Controller para PantallaRegistrarEstado - sin callback inicialmente
            RegistrarEstadoController registrarEstadoController = new RegistrarEstadoController(
                vista.getPantallaRegistrarEstado(),
                model,
                null
            );
            
            // Callback maestro que mantiene ambas pantallas actualizadas
            Runnable refrescarPantallas = () -> {
                cdeViewController.cargarDatos();
                registrarEstadoController.cargarEquipos();
            };
            
            OnEstadosActualizadosListener refrescarPantallasEstados = refrescarPantallas::run;
            OnEquipoGuardadoListener refrescarPantallasEquipos = refrescarPantallas::run;
            
            // Asignar el callback al RegistrarEstadoController (después de su construcción)
            registrarEstadoController.setOnEstadosActualizados(refrescarPantallasEstados);
            
            // Crear controladores específicos para cada panel
            new OrthopediaInputController(
                vista.getPanelIngresoOrtopedia(), 
                model, 
                vista.getNavegador(), 
                vista.getContenedor(),
                refrescarPantallasEquipos  // Callback específico para cuando se guarda un equipo
            );
            
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
                Constantes.Mensajes.ERROR_CONEXION_BD,
                Constantes.Mensajes.TITULO_ERROR_CONEXION,
                JOptionPane.ERROR_MESSAGE);
        System.exit(0);
    }
}