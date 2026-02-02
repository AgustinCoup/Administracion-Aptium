package com.example.controller;

import javax.swing.JOptionPane;

import com.example.constants.Constantes;
import com.example.model.AppModel;
import com.example.model.Cliente;
import com.example.model.Profesional;
import com.example.model.Institucion;
import com.example.model.Equipo;
import com.example.model.Material;
import com.example.util.Validador;
import com.example.util.Logger;
import com.example.view.PantallaIngresoOrtopedia;
import com.example.view.helpers.AutocompleteListener;
import com.example.controller.listeners.OnEquipoGuardadoListener;

import java.awt.CardLayout;
import java.util.List;
import javax.swing.JPanel;

/**
 * Controlador para el panel de ingreso de ortopedia.
 * Coordina entre la vista (PantallaIngresoOrtopedia) y el modelo (AppModel).
 * Gestiona validación de datos y guardado en base de datos.
 */
public class OrthopediaInputController {
    
    private PantallaIngresoOrtopedia panel;
    private AppModel model;
    private CardLayout navegador;
    private JPanel contenedor;
    private OnEquipoGuardadoListener onEquipoGuardadoListener;
    
    /**
     * Listener del componente de autocompletado.
     * Gestiona la búsqueda y selección de clientes en el formulario.
     */
    private AutocompleteListener<Cliente> autocompleteClientListener;
    
    /**
     * Listener del componente de autocompletado.
     * Gestiona la búsqueda y selección de profesionales en el formulario.
     */
    private AutocompleteListener<Profesional> autocompleteProfesionalListener;
    
    /**
     * Listener del componente de autocompletado.
     * Gestiona la búsqueda y selección de instituciones en el formulario.
     */
    private AutocompleteListener<Institucion> autocompleteInstitucionListener;
    
    public OrthopediaInputController(PantallaIngresoOrtopedia panel, AppModel model, CardLayout navegador, JPanel contenedor, OnEquipoGuardadoListener onEquipoGuardadoListener) {
        this.panel = panel;
        this.model = model;
        this.navegador = navegador;
        this.contenedor = contenedor;
        this.onEquipoGuardadoListener = onEquipoGuardadoListener;
        inicializarEventos();
    }
    
    private void inicializarEventos() {
        panel.getBtnGuardar().addActionListener(e -> guardarOrtopedia());
        
        /**
         * Registro del listener para búsqueda de material en tiempo real.
         * Patrón MVC: La Vista notifica al Controller cuando cambia el código,
         * el Controller consulta el Modelo y actualiza la Vista.
         */
        panel.getPanelMateriales().setOnNumeroChangedListener((codigo, campoDescripcion) -> {
            String descripcion = model.getCatalogoService().obtenerDescripcion(codigo);
            campoDescripcion.setText(descripcion != null ? descripcion : "Desconocido");
        });
        
        /**
         * Configuración del autocompletado de clientes.
         * 
         * Componentes:
         * - searchFunction: Consulta el Modelo para obtener clientes que coincidan
         * - onClienteSelected: Callback que notifica al Controller la selección
         * 
         * Patrón Callback: El autocompletado no accede directamente a la Vista,
         * solo notifica al Controller a través del callback.
         */
        autocompleteClientListener = new AutocompleteListener<>(
            panel.getTxtCliente(),
            texto -> model.buscarClientes(texto),
            cliente -> panel.setSelectedClienteId(cliente.getId())
        );
        
        panel.getTxtCliente().getDocument().addDocumentListener(autocompleteClientListener);
        
        /**
         * Configuración del autocompletado de profesionales.
         * Mismo patrón que clientes pero con búsqueda de profesionales.
         */
        autocompleteProfesionalListener = new AutocompleteListener<>(
            panel.getTxtProfesional(),
            texto -> model.buscarProfesionales(texto),
            profesional -> panel.setSelectedProfesionalId(profesional.getId())
        );
        
        panel.getTxtProfesional().getDocument().addDocumentListener(autocompleteProfesionalListener);
        
        /**
         * Configuración del autocompletado de instituciones.
         * Mismo patrón que clientes pero con búsqueda de instituciones.
         */
        autocompleteInstitucionListener = new AutocompleteListener<>(
            panel.getTxtInstitucion(),
            texto -> model.buscarInstituciones(texto),
            institucion -> panel.setSelectedInstitucionId(institucion.getId())
        );
        
        panel.getTxtInstitucion().getDocument().addDocumentListener(autocompleteInstitucionListener);
    }
    
    /**
     * Lógica principal de guardar: valida, mapea datos, guarda y navega.
     */
    private void guardarOrtopedia() {
        if (!validarFormulario()) {
            return;
        }
        
        // Mapear datos del formulario a objeto Equipo
        Equipo equipo = construirEquipoDesdeFormulario();
        
        // Guardar en base de datos a través del modelo
        boolean guardoExitoso = model.guardarEquipo(equipo);
        
        if (guardoExitoso) {
            // Éxito: mostrar mensaje y limpiar
            JOptionPane.showMessageDialog(panel, 
                Constantes.Mensajes.DATOS_GUARDADOS, 
                Constantes.Mensajes.TITULO_EXITO, 
                JOptionPane.INFORMATION_MESSAGE);
            
            panel.limpiarFormulario();
            
            // Notificar al listener que un equipo fue guardado
            if (onEquipoGuardadoListener != null) {
                onEquipoGuardadoListener.onEquipoGuardado();
            }
            
            navegador.show(contenedor, Constantes.Pantallas.ESTERILIZACION);
            Logger.info("Equipo guardado exitosamente desde formulario");
        } else {
            // Error: mostrar mensaje de error
            JOptionPane.showMessageDialog(panel, 
                "Error al guardar el equipo. Por favor, intente de nuevo.", 
                "Error al Guardar", 
                JOptionPane.ERROR_MESSAGE);
            Logger.error("Fallo al guardar equipo desde formulario");
        }
    }
    
    /**
     * Valida que todos los campos obligatorios sean correctos.
     * 
     * Campos OBLIGATORIOS:
     * - Cliente: debe estar no vacío y seleccionado del autocompletado
     * - Institución: debe estar no vacío
     * - Al menos un material agregado
     * 
     * Campos OPCIONALES:
     * - Profesional: puede estar vacío o seleccionado del autocompletado
     * - Paciente: puede estar vacío
     * 
     * @return true si todas las validaciones pasan, false en caso contrario
     */
    public boolean validarFormulario() {
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
    
    /**
     * Construye un objeto Equipo a partir de los datos del formulario.
     * Este método es responsable del mapeo (Vista → Modelo).
     * 
     * Campos obligatorios: cliente
     * Campos opcionales: profesional, paciente (pueden ser null o vacío)
     * 
     * @return Equipo con todos los datos del formulario
     */
    private Equipo construirEquipoDesdeFormulario() {
        Equipo equipo = new Equipo();
        
        equipo.setClienteNombre(panel.getTxtCliente().getText().trim());
        
        /**
         * El identificador del cliente ahora proviene de la selección
         * del autocompletado, no de un valor hardcodeado.
         * 
         * Garantiza que equipoService.nroCliente sea válido según la FK
         * hacia la tabla clientes.
         */
        equipo.setNroCliente(panel.getSelectedClienteId());
        
        /**
         * El profesional es OPCIONAL.
         * Solo se establece si fue seleccionado desde el autocompletado.
         */
        if (panel.getSelectedProfesionalId() != -1) {
            equipo.setNroProfesional(panel.getSelectedProfesionalId());
            equipo.setProfesionalNombre(panel.getTxtProfesional().getText().trim());
        }
        
        /**
         * El paciente es OPCIONAL.
         * Se establece si el campo no está vacío.
         */
        String paciente = panel.getTxtPaciente().getText().trim();
        if (!paciente.isEmpty()) {
            equipo.setPacienteNombre(paciente);
        }
        
        /**
         * La institución es OBLIGATORIA y debe ser seleccionada del autocompletado.
         * El identificador se utiliza como FK en la tabla instituciones.
         */
        equipo.setNroInstitucion(panel.getSelectedInstitucionId());
        
        panel.getMaterialRows().forEach(row -> {
            String numeroStr = row.numero.getText().trim();
            
            if (!numeroStr.isEmpty() && Validador.soloNumeros(numeroStr)) {
                int codigoMaterial = Integer.parseInt(numeroStr);
                String descripcion = row.descripcion.getText().trim();
                Object ElementosObj = row.Elementos.getValue();
                
                int cantidad = 0;
                if (ElementosObj instanceof Number) {
                    cantidad = ((Number) ElementosObj).intValue();
                }
                
                Material material = new Material(codigoMaterial, descripcion, cantidad);
                equipo.agregarMaterial(material);
            }
        });
        
        return equipo;
    }
}
