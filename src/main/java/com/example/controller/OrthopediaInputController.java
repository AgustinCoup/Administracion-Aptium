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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.view.PantallaIngresoOrtopedia;
import com.example.view.dialogs.AgregarAutocompletableDialog;
import com.example.view.helpers.AutocompleteListener;
import com.example.controller.helpers.GestorValidacionFormulario;
import com.example.controller.helpers.ConstructorEquipo;
import com.example.controller.helpers.GestorNuevasEntidades;
import com.example.controller.listeners.OnEquipoGuardadoListener;

import java.awt.CardLayout;
import java.awt.Frame;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Controlador para el panel de ingreso de ortopedia.
 * Coordina entre la vista (PantallaIngresoOrtopedia) y el modelo (AppModel).
 * Gestiona validación de datos y guardado en base de datos.
 */
public class OrthopediaInputController {

    private static final Logger log = LoggerFactory.getLogger(OrthopediaInputController.class);
    
    private PantallaIngresoOrtopedia panel;
    private AppModel model;
    private CardLayout navegador;
    private JPanel contenedor;
    private OnEquipoGuardadoListener onEquipoGuardadoListener;
    
    /**
     * Gestor de validación del formulario de ingreso de ortopedia.
     * Encapsula toda la lógica de validación.
     */
    private GestorValidacionFormulario gestorValidacion;
    
    /**
     * Constructor de equipos a partir de los datos del formulario.
     * Encapsula la lógica de mapeo Vista → Modelo.
     */
    private ConstructorEquipo constructorEquipo;
    
    /**
     * Gestores de nuevas entidades (uno por cada tipo).
     * Encapsulan la lógica de crear Cliente, Profesional, Institución.
     */
    private GestorNuevasEntidades<Cliente> gestorNuevosClientes;
    private GestorNuevasEntidades<Profesional> gestorNuevosProfesionales;
    private GestorNuevasEntidades<Institucion> gestorNuevasInstituciones;
    
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
        
        // Inicializar clases auxiliares
        this.gestorValidacion = new GestorValidacionFormulario(panel);
        this.constructorEquipo = new ConstructorEquipo(panel, model);
        
        // Los gestores se completarán en inicializarEventos() después de crear los autocompletados
        
        inicializarEventos();
    }
    
    private void inicializarEventos() {
        panel.getBtnGuardar().addActionListener(e -> guardarOrtopedia());

        panel.setOnRequiereLavadoChanged(e -> {
            boolean lavado = panel.isRequiereLavado();
            if (lavado) {
                panel.setRequiereEmpaqueSelected(true);
                panel.setRequiereEmpaqueEnabled(false);
            } else {
                panel.setRequiereEmpaqueEnabled(true);
            }
        });
        
        /**
         * Registro del listener para búsqueda de material en tiempo real.
         * Patrón MVC: La Vista notifica al Controller cuando cambia el código,
         * el Controller consulta el Modelo y actualiza la Vista.
         */
        panel.getPanelMateriales().setOnNumeroChangedListener((codigo, campoDescripcion) -> {
            String descripcion = model.getCatalogoService().obtenerDescripcion(codigo);
            campoDescripcion.setText(descripcion != null ? descripcion : Constantes.Mensajes.AUTOCOMPLETE_DESCONOCIDO);
        });
        
        /**
         * Configuración del autocompletado de clientes.
         * 
         * Componentes:
         * - searchFunction: Consulta el Modelo para obtener clientes que coincidan
         * - onClienteSelected: Callback que notifica al Controller la selección
         * - onNoMatch: Callback si el usuario ingresa un cliente no existente
         * 
         * Patrón Callback: El autocompletado no accede directamente a la Vista,
         * solo notifica al Controller a través del callback.
         */
        autocompleteClientListener = new AutocompleteListener<>(
            panel.getTxtCliente(),
            texto -> model.buscarClientes(texto),
            cliente -> panel.setSelectedClienteId(cliente.getId()),
            nombreNoExistente -> gestorNuevosClientes.manejarEntidadNoExistente(nombreNoExistente)
        );
        
        panel.getTxtCliente().getDocument().addDocumentListener(autocompleteClientListener);
        
        /**
         * Configuración del autocompletado de profesionales.
         * Mismo patrón que clientes pero con búsqueda de profesionales.
         */
        autocompleteProfesionalListener = new AutocompleteListener<>(
            panel.getTxtProfesional(),
            texto -> model.buscarProfesionales(texto),
            profesional -> panel.setSelectedProfesionalId(profesional.getId()),
            nombreNoExistente -> gestorNuevosProfesionales.manejarEntidadNoExistente(nombreNoExistente)
        );
        
        panel.getTxtProfesional().getDocument().addDocumentListener(autocompleteProfesionalListener);
        
        /**
         * Configuración del autocompletado de instituciones.
         * Mismo patrón que clientes pero con búsqueda de instituciones.
         */
        autocompleteInstitucionListener = new AutocompleteListener<>(
            panel.getTxtInstitucion(),
            texto -> model.buscarInstituciones(texto),
            institucion -> panel.setSelectedInstitucionId(institucion.getId()),
            nombreNoExistente -> gestorNuevasInstituciones.manejarEntidadNoExistente(nombreNoExistente)
        );
        
        panel.getTxtInstitucion().getDocument().addDocumentListener(autocompleteInstitucionListener);
        
        /**
         * AHORA inicializamos completamente los gestores de nuevas entidades,
         * después de crear los autocompletados, con todas las referencias necesarias.
         */
        initializeEntityManagers();
    }
    
    /**
     * Completa la inicialización de los gestores de nuevas entidades.
     * Debe llamarse DESPUÉS de crear los listeners de autocompletado.
     */
    private void initializeEntityManagers() {
        // Gestor de nuevos clientes
        this.gestorNuevosClientes = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_CLIENTE,
            nombre -> panel.getTxtCliente().setText(nombre),
            id -> panel.setSelectedClienteId(id),
            autocompleteClientListener,
            cliente -> model.guardarCliente(cliente),
            Cliente::new
        );
        
        // Gestor de nuevos profesionales
        this.gestorNuevosProfesionales = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_PROFESIONAL,
            nombre -> panel.getTxtProfesional().setText(nombre),
            id -> panel.setSelectedProfesionalId(id),
            autocompleteProfesionalListener,
            profesional -> model.guardarProfesional(profesional),
            Profesional::new
        );
        
        // Gestor de nuevas instituciones
        this.gestorNuevasInstituciones = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_INSTITUCION,
            nombre -> panel.getTxtInstitucion().setText(nombre),
            id -> panel.setSelectedInstitucionId(id),
            autocompleteInstitucionListener,
            institucion -> model.guardarInstitucion(institucion),
            Institucion::new
        );
    }
    
    /**
     * Lógica principal de guardar: valida, mapea datos, guarda y navega.
     */
    private void guardarOrtopedia() {
        if (!gestorValidacion.validar()) {
            return;
        }
        
        // Mapear datos del formulario a objeto Equipo
        Equipo equipo = constructorEquipo.construir();
        
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
            log.info("Equipo guardado exitosamente desde formulario");
        } else {
            // Error: mostrar mensaje de error
            JOptionPane.showMessageDialog(panel, 
                Constantes.Mensajes.ERROR_GUARDAR_EQUIPO,
                Constantes.Mensajes.TITULO_ERROR_GUARDAR,
                JOptionPane.ERROR_MESSAGE);
            log.error("Fallo al guardar equipo desde formulario");
        }
    }
    
    /**
     * Obtiene la ventana parente del panel para usarla en diálogos modales.
     * 
     * @return Frame que contiene este componente
     */
    private Frame obtenerVentanaParente() {
        return (Frame) SwingUtilities.getWindowAncestor(panel);
    }

    /**
     * Maneja la situación cuando el usuario ingresa un cliente que no existe en la BD.
     * Ofrece un diálogo para crearlo.
     * 
     * @param nombreCliente Nombre del cliente escrito por el usuario
     */
}
