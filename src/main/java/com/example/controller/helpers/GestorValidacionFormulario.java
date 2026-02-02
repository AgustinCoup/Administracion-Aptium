package com.example.controller.helpers;

import com.example.constants.Constantes;
import com.example.model.AppModel;
import com.example.util.Validador;
import com.example.view.PantallaIngresoOrtopedia;
import javax.swing.JOptionPane;

/**
 * Encapsula toda la lógica de validación del formulario de ingreso de ortopedia.
 * 
 * Responsabilidad: Validar que todos los campos requeridos estén correctamente completados.
 * 
 * Separación de responsabilidades: 
 * - OrthopediaInputController solo coordina, no valida
 * - Esta clase contiene toda la lógica de validación
 */
public class GestorValidacionFormulario {
    
    private final PantallaIngresoOrtopedia panel;
    
    public GestorValidacionFormulario(PantallaIngresoOrtopedia panel) {
        this.panel = panel;
    }
    
    /**
     * Valida que todos los campos obligatorios sean correctos.
     * 
     * Campos OBLIGATORIOS:
     * - Cliente: debe estar no vacío y seleccionado del autocompletado
     * - Institución: debe estar no vacío y seleccionado
     * - Al menos un material agregado
     * 
     * Campos OPCIONALES:
     * - Profesional: puede estar vacío o seleccionado del autocompletado
     * - Paciente: puede estar vacío
     * 
     * @return true si todas las validaciones pasan, false en caso contrario
     */
    public boolean validar() {
        // Cliente es OBLIGATORIO
        if (!Validador.noEstaVacio(panel.getTxtCliente().getText())) {
            JOptionPane.showMessageDialog(panel, "El campo Cliente es obligatorio.");
            return false;
        }

        if (panel.getSelectedClienteId() == -1) {
            JOptionPane.showMessageDialog(panel, 
                "Debe seleccionar un cliente de la lista de sugerencias.", 
                "Cliente no seleccionado", 
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        // Institución es OBLIGATORIA
        if (!Validador.noEstaVacio(panel.getTxtInstitucion().getText())) {
            JOptionPane.showMessageDialog(panel, "El campo Institución es obligatorio.");
            return false;
        }
        
        // Verificar que institución fue seleccionada del autocompletado
        if (panel.getSelectedInstitucionId() == -1) {
            JOptionPane.showMessageDialog(panel, 
                "Debe seleccionar una institución de la lista de sugerencias.", 
                "Institución no seleccionada", 
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        // Profesional y Paciente son OPCIONALES
        // Si se escribió algo en profesional, validar que sea del autocompletado y formato correcto
        String txtProfesional = panel.getTxtProfesional().getText().trim();
        if (!txtProfesional.isEmpty()) {
            if (panel.getSelectedProfesionalId() == -1) {
                JOptionPane.showMessageDialog(panel, 
                    "Si ingresa un profesional, debe seleccionar uno de la lista de sugerencias.", 
                    "Profesional no seleccionado", 
                    JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        
        String txtPaciente = panel.getTxtPaciente().getText().trim();
        if (!txtPaciente.isEmpty()) {
            if (!Validador.esFormatoNombre(txtPaciente)) {
                JOptionPane.showMessageDialog(panel, Constantes.Mensajes.FORMATO_PACIENTE_INVALIDO);
                return false;
            }
        }

        // Al menos un material es OBLIGATORIO
        if (panel.getMaterialRows().isEmpty()) {
            JOptionPane.showMessageDialog(panel, Constantes.Mensajes.DEBE_AGREGAR_MATERIAL);
            return false;
        }

        return true;
    }
}
