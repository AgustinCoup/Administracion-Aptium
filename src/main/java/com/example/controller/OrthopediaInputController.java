package com.example.controller;

import javax.swing.JOptionPane;

import com.example.constants.Constantes;
import com.example.model.AppModel;
import com.example.model.Equipo;
import com.example.model.Material;
import com.example.util.Validador;
import com.example.util.Logger;
import com.example.view.PantallaIngresoOrtopedia;

import java.awt.CardLayout;
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
    
    public OrthopediaInputController(PantallaIngresoOrtopedia panel, AppModel model, CardLayout navegador, JPanel contenedor) {
        this.panel = panel;
        this.model = model;
        this.navegador = navegador;
        this.contenedor = contenedor;
        inicializarEventos();
    }
    
    private void inicializarEventos() {
        // Conectar el botón guardar con la lógica de validación y guardado
        panel.getBtnGuardar().addActionListener(e -> guardarOrtopedia());
        
        // Registrar listener para búsqueda de descripciones en tiempo real (respeta MVC)
        // La Vista notifica cuando cambia el número, el Controller consulta el Modelo
        panel.getPanelMateriales().setOnNumeroChangedListener((codigo, campoDescripcion) -> {
            String descripcion = model.getCatalogoService().obtenerDescripcion(codigo);
            campoDescripcion.setText(descripcion != null ? descripcion : "Desconocido");
        });
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
            navegador.show(contenedor, Constantes.Pantallas.MENU_PRINCIPAL);
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
     * Valida que todos los campos del formulario sean correctos.
     * 
     * @return true si el formulario es válido
     */
    public boolean validarFormulario() {
        // 1. Verificar campos vacíos
        if (!Validador.noEstaVacio(panel.getTxtCliente().getText()) ||
            !Validador.noEstaVacio(panel.getTxtProfesional().getText()) ||
            !Validador.noEstaVacio(panel.getTxtPaciente().getText())) {
        
            JOptionPane.showMessageDialog(panel, Constantes.Mensajes.CAMPOS_INCOMPLETOS);
            return false;
        }

        // 2. Validar formato "Apellido Nombre"
        if (!Validador.esFormatoNombre(panel.getTxtProfesional().getText())) {
            JOptionPane.showMessageDialog(panel, Constantes.Mensajes.FORMATO_PROFESIONAL_INVALIDO);
            return false;
        }
        
        if (!Validador.esFormatoNombre(panel.getTxtPaciente().getText())) {
            JOptionPane.showMessageDialog(panel, Constantes.Mensajes.FORMATO_PACIENTE_INVALIDO);
            return false;
        }

        // 3. Validar que haya al menos un material
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
     * @return Equipo con todos los datos del formulario
     */
    private Equipo construirEquipoDesdeFormulario() {
        Equipo equipo = new Equipo();
        
        // Datos básicos del cliente
        equipo.setClienteNombre(panel.getTxtCliente().getText().trim());
        equipo.setNroCliente(1); // TODO: En futuro, permitir seleccionar cliente existente
        
        // Datos médicos
        equipo.setProfesionalNombre(panel.getTxtProfesional().getText().trim());
        equipo.setPacienteNombre(panel.getTxtPaciente().getText().trim());
        
        // El estado inicial es NUEVO (definido en constructor de Equipo)
        
        // Agregar materiales desde las filas del formulario
        panel.getMaterialRows().forEach(row -> {
            String numeroStr = row.numero.getText().trim();
            
            // Solo agregar si el número no está vacío
            if (!numeroStr.isEmpty() && Validador.soloNumeros(numeroStr)) {
                int codigoMaterial = Integer.parseInt(numeroStr);
                String descripcion = row.descripcion.getText().trim();
                Object litrosObj = row.litros.getValue();
                
                int cantidad = 0;
                if (litrosObj instanceof Number) {
                    cantidad = ((Number) litrosObj).intValue();
                }
                
                // El idRelacionado será generado por EquipoDAO en base de datos
                String idRel = equipo.getCodigoEquipo() + "-" + codigoMaterial;
                Material material = new Material(idRel, codigoMaterial, descripcion, cantidad);
                equipo.agregarMaterial(material);
            }
        });
        
        return equipo;
    }
}
