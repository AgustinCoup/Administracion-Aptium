package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.common.constants.Constantes;
import com.example.common.util.Validador;
import com.example.features.equipos.ortopedias.view.PantallaIngresoOrtopedia;

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
    private final CatalogoLookup catalogoLookup;
    
    public GestorValidacionFormulario(PantallaIngresoOrtopedia panel, CatalogoLookup catalogoLookup) {
        this.panel = panel;
        this.catalogoLookup = catalogoLookup;
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
            JOptionPane.showMessageDialog(panel, Constantes.Mensajes.CAMPO_CLIENTE_OBLIGATORIO);
            return false;
        }

        if (panel.getSelectedClienteId() == -1) {
            JOptionPane.showMessageDialog(panel, 
                Constantes.Mensajes.CLIENTE_NO_SELECCIONADO,
                Constantes.Mensajes.TITULO_CLIENTE_NO_SELECCIONADO,
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        // Institución es OBLIGATORIA
        if (!Validador.noEstaVacio(panel.getTxtInstitucion().getText())) {
            JOptionPane.showMessageDialog(panel, Constantes.Mensajes.CAMPO_INSTITUCION_OBLIGATORIO);
            return false;
        }
        
        // Verificar que institución fue seleccionada del autocompletado
        if (panel.getSelectedInstitucionId() == -1) {
            JOptionPane.showMessageDialog(panel, 
                Constantes.Mensajes.INSTITUCION_NO_SELECCIONADA,
                Constantes.Mensajes.TITULO_INSTITUCION_NO_SELECCIONADA,
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        // Profesional y Paciente son OPCIONALES
        // Si se escribió algo en profesional, validar que sea del autocompletado y formato correcto
        String txtProfesional = panel.getTxtProfesional().getText().trim();
        if (!txtProfesional.isEmpty()) {
            if (panel.getSelectedProfesionalId() == -1) {
                JOptionPane.showMessageDialog(panel, 
                    Constantes.Mensajes.PROFESIONAL_NO_SELECCIONADO,
                    Constantes.Mensajes.TITULO_PROFESIONAL_NO_SELECCIONADO,
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

        // Al menos un material válido es OBLIGATORIO
        // (la UI siempre mantiene una fila vacía por defecto, por eso no alcanza con isEmpty)
        boolean tieneMaterialValido = panel.getMaterialRows().stream().anyMatch(row -> {
            String numero = row.numero.getText().trim();
            return !numero.isEmpty() && Validador.soloNumeros(numero);
        });

        if (!tieneMaterialValido) {
            JOptionPane.showMessageDialog(panel, Constantes.Mensajes.DEBE_AGREGAR_MATERIAL);
            return false;
        }

        String codigosDesconocidos = panel.getMaterialRows().stream()
            .map(row -> row.numero.getText().trim())
            .filter(numero -> !numero.isEmpty() && Validador.soloNumeros(numero))
            .map(Integer::parseInt)
            .distinct()
            .filter(codigo -> !catalogoLookup.existeCodigo(codigo))
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(", "));

        if (!codigosDesconocidos.isEmpty()) {
            JOptionPane.showMessageDialog(
                panel,
                String.format(Constantes.Mensajes.CODIGO_MATERIAL_DESCONOCIDO, codigosDesconocidos),
                Constantes.Mensajes.TITULO_ADVERTENCIA,
                JOptionPane.WARNING_MESSAGE
            );
            return false;
        }

        return true;
    }
}


