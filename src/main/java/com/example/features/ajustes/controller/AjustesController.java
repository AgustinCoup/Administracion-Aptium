package com.example.features.ajustes.controller;

import com.example.app.AppModel;
import com.example.common.exception.ApplicationException;
import com.example.features.ajustes.view.FusionarClienteDialog;
import com.example.features.ajustes.view.NuevoClienteDialog;
import com.example.features.ajustes.view.PanelGestionClientes;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.clientes.model.Cliente;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.stream.Collectors;

public class AjustesController {

    private final PantallaAjustes      vista;
    private final PanelGestionClientes panel;
    private final AppModel             model;
    private       Runnable             onMutacion;

    public AjustesController(PantallaAjustes vista, AppModel model) {
        this.vista  = vista;
        this.panel  = vista.getPanelClientes();
        this.model  = model;

        panel.setOnAgregar(this::agregarCliente);
        panel.setOnEliminar(this::eliminarCliente);
        panel.setOnFusionar(this::fusionarCliente);

        vista.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                panel.limpiarBusqueda();
                cargarDatos();
            }
        });
    }

    public void setOnMutacion(Runnable r) { this.onMutacion = r; }

    private void cargarDatos() {
        new Thread(() -> {
            try {
                List<Cliente> clientes = model.obtenerTodosLosClientes();
                SwingUtilities.invokeLater(() -> panel.setDatos(clientes));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(vista, "Error al cargar clientes.",
                        "Error", JOptionPane.ERROR_MESSAGE));
            }
        }, "ajustes-loader").start();
    }

    private void notificarMutacion() {
        if (onMutacion != null) onMutacion.run();
    }

    private void agregarCliente() {
        NuevoClienteDialog dialog = new NuevoClienteDialog(SwingUtilities.getWindowAncestor(vista));
        dialog.setVisible(true);
        String nombre = dialog.obtenerNombre();
        if (nombre == null) return;

        new Thread(() -> {
            try {
                model.guardarCliente(new Cliente(0, nombre));
                SwingUtilities.invokeLater(() -> {
                    cargarDatos();
                    notificarMutacion();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(vista, "Error al guardar el cliente: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE));
            }
        }, "ajustes-guardar").start();
    }

    private void eliminarCliente() {
        Cliente cliente = panel.getClienteSeleccionado();
        if (cliente == null) {
            JOptionPane.showMessageDialog(vista, "Seleccione un cliente para eliminar.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int resp = JOptionPane.showConfirmDialog(vista,
            "¿Eliminar el cliente \"" + cliente.getNombre() + "\"?",
            "Confirmar eliminación", JOptionPane.YES_NO_OPTION);
        if (resp != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                model.eliminarCliente(cliente.getId());
                SwingUtilities.invokeLater(() -> {
                    cargarDatos();
                    notificarMutacion();
                });
            } catch (ApplicationException ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(vista, ex.getMessage(),
                        "No se puede eliminar", JOptionPane.ERROR_MESSAGE));
            }
        }, "ajustes-eliminar").start();
    }

    private void fusionarCliente() {
        Cliente origen = panel.getClienteSeleccionado();
        if (origen == null) {
            JOptionPane.showMessageDialog(vista, "Seleccione el cliente a fusionar (el que se eliminará).",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                List<Cliente> todos = model.obtenerTodosLosClientes();
                List<Cliente> candidatos = todos.stream()
                    .filter(c -> c.getId() != origen.getId())
                    .collect(Collectors.toList());
                SwingUtilities.invokeLater(() -> mostrarDialogoFusion(origen, candidatos));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(vista, "Error al cargar clientes: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE));
            }
        }, "ajustes-fusion-load").start();
    }

    private void mostrarDialogoFusion(Cliente origen, List<Cliente> candidatos) {
        if (candidatos.isEmpty()) {
            JOptionPane.showMessageDialog(vista, "No hay otros clientes disponibles para fusionar.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        FusionarClienteDialog dialog = new FusionarClienteDialog(
            SwingUtilities.getWindowAncestor(vista), origen, candidatos);
        dialog.setVisible(true);
        Cliente destino = dialog.obtenerClienteDestino();
        if (destino == null) return;

        int resp = JOptionPane.showConfirmDialog(vista,
            "Se eliminará \"" + origen.getNombre() + "\" y sus equipos pasarán a \""
                + destino.getNombre() + "\".\n¿Confirmar?",
            "Confirmar fusión", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (resp != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                model.fusionarClientes(origen.getId(), destino.getId());
                SwingUtilities.invokeLater(() -> {
                    cargarDatos();
                    notificarMutacion();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(vista, "Error al fusionar clientes: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE));
            }
        }, "ajustes-fusion").start();
    }
}
