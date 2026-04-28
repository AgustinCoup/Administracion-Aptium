package com.example.features.equipos.common.controller;

import com.example.app.AppModel;
import com.example.common.constants.Constantes;
import com.example.features.clientes.model.Cliente;
import com.example.features.equipos.common.view.PantallaIngresoBase;
import com.example.features.equipos.ortopedias.controller.helpers.GestorNuevasEntidades;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.events.OnEquipoGuardadoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Window;
import javax.swing.SwingUtilities;

public abstract class EquipoInputControllerBase<P extends JPanel & PantallaIngresoBase> {

    protected static final Logger log = LoggerFactory.getLogger(EquipoInputControllerBase.class);

    protected final P                        panel;
    protected final AppModel                 model;
    protected final CardLayout               navegador;
    protected final JPanel                   contenedor;
    protected final OnEquipoGuardadoListener onEquipoGuardadoListener;

    private AutocompleteListener<Cliente>   autocompleteClienteListener;
    private GestorNuevasEntidades<Cliente>  gestorNuevosClientes;

    protected EquipoInputControllerBase(P panel, AppModel model, CardLayout navegador,
                                        JPanel contenedor, OnEquipoGuardadoListener listener) {
        this.panel                    = panel;
        this.model                    = model;
        this.navegador                = navegador;
        this.contenedor               = contenedor;
        this.onEquipoGuardadoListener = listener;
    }

    protected final void inicializarEventosComunes() {
        panel.getBtnGuardar().addActionListener(e -> guardar());
        configurarLavadoEmpaque();
        inicializarClienteAutocomplete();
    }

    private void configurarLavadoEmpaque() {
        panel.setOnRequiereLavadoChanged(e -> {
            if (panel.isRequiereLavado()) {
                panel.setRequiereEmpaqueSelected(true);
                panel.setRequiereEmpaqueEnabled(false);
            } else {
                panel.setRequiereEmpaqueEnabled(true);
            }
        });
    }

    protected final void inicializarClienteAutocomplete() {
        autocompleteClienteListener = new AutocompleteListener<>(
            panel.getTxtCliente(),
            texto -> model.buscarClientes(texto),
            cliente -> panel.setSelectedClienteId(cliente.getId()),
            nombre  -> gestorNuevosClientes.manejarEntidadNoExistente(nombre)
        );
        panel.getTxtCliente().getDocument().addDocumentListener(autocompleteClienteListener);

        gestorNuevosClientes = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_CLIENTE,
            nombre -> panel.getTxtCliente().setText(nombre),
            id     -> panel.setSelectedClienteId(id),
            autocompleteClienteListener,
            cliente -> model.guardarCliente(cliente),
            Cliente::new
        );
    }

    protected final void manejarResultadoGuardado(boolean exito, String mensajeExito,
                                                   String pantallaDestino, Object contextoLog) {
        if (exito) {
            panel.mostrarInfo(mensajeExito);
            panel.limpiarFormulario();
            if (onEquipoGuardadoListener != null) onEquipoGuardadoListener.onEquipoGuardado();
            navegador.show(contenedor, pantallaDestino);
            log.info("Equipo guardado exitosamente ({})", contextoLog);
        } else {
            panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_EQUIPO);
            log.error("Falló guardar equipo ({})", contextoLog);
        }
    }

    protected final Window obtenerVentanaParente() {
        return (Window) SwingUtilities.getWindowAncestor(panel);
    }

    protected abstract void guardar();
}
