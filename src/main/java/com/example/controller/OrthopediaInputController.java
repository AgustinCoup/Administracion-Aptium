package com.example.controller;

import javax.swing.JOptionPane;

import com.example.model.AppModel;
import com.example.view.PanelIngresoOrtopedia;

import java.awt.CardLayout;
import javax.swing.JPanel;

public class OrthopediaInputController {
    
    private PanelIngresoOrtopedia panel;
    private AppModel model;
    private CardLayout navegador;
    private JPanel contenedor;
    
    public OrthopediaInputController(PanelIngresoOrtopedia panel, AppModel model, CardLayout navegador, JPanel contenedor) {
        this.panel = panel;
        this.model = model;
        this.navegador = navegador;
        this.contenedor = contenedor;
        inicializarEventos();
    }
    
    private void inicializarEventos() {
        // Conectar el botón guardar con la lógica de validación y guardado
        panel.getBtnGuardar().addActionListener(e -> guardarOrtopedia());
    }
    
    private void guardarOrtopedia() {
        if (!validarFormulario()) {
            return;
        }
        
        // Aquí iría la lógica para guardar los datos en el modelo
        // model.guardarOrtopedia(...)
        
        JOptionPane.showMessageDialog(panel, "Datos guardados correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
        panel.limpiarFormulario();
        navegador.show(contenedor, "MENU_PRINCIPAL");
    }
    
    public boolean validarFormulario() {
        // 1. Verificar campos vacíos
        if (panel.getTxtCliente().getText().trim().isEmpty() ||
            panel.getTxtProfesional().getText().trim().isEmpty() ||
            panel.getTxtPaciente().getText().trim().isEmpty()) {
        
            JOptionPane.showMessageDialog(panel, "Por favor, complete todos los campos obligatorios.");
            return false;
        }

        // 2. Validar formato "Apellido Nombre"
        if (!validarFormatoNombre(panel.getTxtProfesional().getText())) {
            JOptionPane.showMessageDialog(panel, "El Profesional debe seguir el formato: Apellido Nombre");
            return false;
        }
        
        if (!validarFormatoNombre(panel.getTxtPaciente().getText())) {
            JOptionPane.showMessageDialog(panel, "El Paciente debe seguir el formato: Apellido Nombre");
            return false;
        }

        // 3. Validar que haya al menos un material
        if (panel.getMaterialRows().isEmpty()) {
            JOptionPane.showMessageDialog(panel, "Debe agregar al menos un material al equipo.");
            return false;
        }

        return true;
    }
    
    /**
     * Valida que un nombre siga el formato "Apellido Nombre" (sin coma).
     * Permite letras con acentos, ñ y espacios entre palabras.
     */
    private boolean validarFormatoNombre(String nombre) {
        // Regex: Al menos una palabra (apellido) + espacio + al menos una palabra (nombre)
        // ^: inicio, [a-zA-Z...]+: una o más letras, \s+: uno o más espacios, $: fin
        String regex = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ]+(?:\\s+[a-zA-ZáéíóúÁÉÍÓÚñÑ]+)+$";
        return nombre.matches(regex);
    }
}
