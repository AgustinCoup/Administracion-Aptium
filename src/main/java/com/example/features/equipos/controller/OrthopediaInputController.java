package com.example.features.equipos.controller;

import javax.swing.JOptionPane;

import com.example.common.constants.Constantes;
import com.example.app.AppModel;
import com.example.features.clientes.model.Cliente;
import com.example.features.profesionales.model.Profesional;
import com.example.features.instituciones.model.Institucion;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.model.Material;
import com.example.common.exception.ValidationException;
import com.example.common.util.Validador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.features.equipos.view.PantallaIngresoOrtopedia;
import com.example.ui.dialogs.AgregarAutocompletableDialog;
import com.example.ui.common.AutocompleteListener;
import com.example.features.equipos.controller.helpers.GestorValidacionFormulario;
import com.example.features.equipos.controller.helpers.CatalogoLookup;
import com.example.features.equipos.controller.helpers.ConstructorEquipo;
import com.example.features.equipos.controller.helpers.GestorNuevasEntidades;
import com.example.ui.events.OnEquipoGuardadoListener;

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
    
    private GestorValidacionFormulario gestorValidacion;
    private ConstructorEquipo constructorEquipo;
    
    private GestorNuevasEntidades<Cliente>     gestorNuevosClientes;
    private GestorNuevasEntidades<Profesional> gestorNuevosProfesionales;
    private GestorNuevasEntidades<Institucion> gestorNuevasInstituciones;
    
    private AutocompleteListener<Cliente>     autocompleteClientListener;
    private AutocompleteListener<Profesional> autocompleteProfesionalListener;
    private AutocompleteListener<Institucion> autocompleteInstitucionListener;
    
    public OrthopediaInputController(PantallaIngresoOrtopedia panel, AppModel model,
                                     CardLayout navegador, JPanel contenedor,
                                     OnEquipoGuardadoListener onEquipoGuardadoListener) {
        this.panel                   = panel;
        this.model                   = model;
        this.navegador               = navegador;
        this.contenedor              = contenedor;
        this.onEquipoGuardadoListener = onEquipoGuardadoListener;
        
        CatalogoLookup catalogoLookup = codigo -> model.obtenerDescripcionMaterial(codigo) != null;
        this.gestorValidacion  = new GestorValidacionFormulario(panel, catalogoLookup);
        this.constructorEquipo = new ConstructorEquipo(panel, model);
        
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
        
        panel.getPanelMateriales().setOnNumeroChangedListener((codigo, campoDescripcion) -> {
            String descripcion = model.getCatalogoService().obtenerDescripcion(codigo);
            campoDescripcion.setText(
                descripcion != null ? descripcion : Constantes.Mensajes.AUTOCOMPLETE_DESCONOCIDO);
        });
        
        autocompleteClientListener = new AutocompleteListener<>(
            panel.getTxtCliente(),
            texto -> model.buscarClientes(texto),
            cliente -> panel.setSelectedClienteId(cliente.getId()),
            nombreNoExistente -> gestorNuevosClientes.manejarEntidadNoExistente(nombreNoExistente)
        );
        panel.getTxtCliente().getDocument().addDocumentListener(autocompleteClientListener);
        
        autocompleteProfesionalListener = new AutocompleteListener<>(
            panel.getTxtProfesional(),
            texto -> model.buscarProfesionales(texto),
            profesional -> panel.setSelectedProfesionalId(profesional.getId()),
            nombreNoExistente -> gestorNuevosProfesionales.manejarEntidadNoExistente(nombreNoExistente)
        );
        panel.getTxtProfesional().getDocument().addDocumentListener(autocompleteProfesionalListener);
        
        autocompleteInstitucionListener = new AutocompleteListener<>(
            panel.getTxtInstitucion(),
            texto -> model.buscarInstituciones(texto),
            institucion -> panel.setSelectedInstitucionId(institucion.getId()),
            nombreNoExistente -> gestorNuevasInstituciones.manejarEntidadNoExistente(nombreNoExistente)
        );
        panel.getTxtInstitucion().getDocument().addDocumentListener(autocompleteInstitucionListener);
        
        initializeEntityManagers();
    }
    
    private void initializeEntityManagers() {
        this.gestorNuevosClientes = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_CLIENTE,
            nombre -> panel.getTxtCliente().setText(nombre),
            id -> panel.setSelectedClienteId(id),
            autocompleteClientListener,
            cliente -> model.guardarCliente(cliente),
            Cliente::new
        );
        
        this.gestorNuevosProfesionales = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_PROFESIONAL,
            nombre -> panel.getTxtProfesional().setText(nombre),
            id -> panel.setSelectedProfesionalId(id),
            autocompleteProfesionalListener,
            profesional -> model.guardarProfesional(profesional),
            Profesional::new
        );
        
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
     *
     * Orden de validaciones:
     * 1. Validación de formulario general (campos obligatorios, formato, existencia en catálogo)
     * 2. Validación de códigos duplicados en la tabla de materiales
     *    — Los campos con código repetido ya se habrán pintado en rojo por PanelMateriales
     *      durante la edición, así que el mensaje de error refuerza la acción a tomar.
     */
    private void guardarOrtopedia() {
        // 1. Validaciones generales del formulario
        if (!gestorValidacion.validar()) {
            return;
        }

        // 2. Verificar que no haya códigos de catálogo duplicados en la tabla de materiales.
        //    tieneDuplicados() también actualiza el color visual de cada fila en ese mismo momento.
        if (panel.getPanelMateriales().tieneDuplicados()) {
            JOptionPane.showMessageDialog(
                panel,
                "Hay materiales con el mismo código de catálogo.\n" +
                "Unifique las filas marcadas en rojo antes de guardar.",
                Constantes.Mensajes.TITULO_ADVERTENCIA,
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // 3. Mapear y persistir
        Equipo equipo = constructorEquipo.construir();
        
        boolean guardoExitoso;
        try {
            guardoExitoso = model.guardarEquipo(equipo);
        } catch (ValidationException e) {
            String mensaje = e.getValidationErrors().isEmpty()
                ? Constantes.Mensajes.ERROR_GUARDAR_EQUIPO
                : String.join("\n", e.getValidationErrors());
            JOptionPane.showMessageDialog(panel, mensaje,
                Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
            log.warn("Validación de negocio al guardar equipo: {}", mensaje);
            return;
        }
        
        if (guardoExitoso) {
            JOptionPane.showMessageDialog(panel,
                Constantes.Mensajes.DATOS_GUARDADOS,
                Constantes.Mensajes.TITULO_EXITO,
                JOptionPane.INFORMATION_MESSAGE);
            
            panel.limpiarFormulario();
            
            if (onEquipoGuardadoListener != null) {
                onEquipoGuardadoListener.onEquipoGuardado();
            }
            
            navegador.show(contenedor, Constantes.Pantallas.ESTERILIZACION);
            log.info("Equipo guardado exitosamente desde formulario");
        } else {
            JOptionPane.showMessageDialog(panel,
                Constantes.Mensajes.ERROR_GUARDAR_EQUIPO,
                Constantes.Mensajes.TITULO_ERROR_GUARDAR,
                JOptionPane.ERROR_MESSAGE);
            log.error("Fallo al guardar equipo desde formulario");
        }
    }
    
    private Frame obtenerVentanaParente() {
        return (Frame) SwingUtilities.getWindowAncestor(panel);
    }
}