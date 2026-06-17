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
                cargarDatos();
            }
        });
    }

    private void cargarDatos() {
        List<Cliente> clientes = model.obtenerTodosLosClientes();
        SwingUtilities.invokeLater(() -> panel.setDatos(clientes));
    }

    private void agregarCliente() {
        NuevoClienteDialog dialog = new NuevoClienteDialog(SwingUtilities.getWindowAncestor(vista));
        dialog.setVisible(true);
        String nombre = dialog.obtenerNombre();
        if (nombre != null) {
            model.guardarCliente(new Cliente(0, nombre));
            cargarDatos();
        }
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

        try {
            model.eliminarCliente(cliente.getId());
            cargarDatos();
        } catch (ApplicationException ex) {
            JOptionPane.showMessageDialog(vista, ex.getMessage(),
                "No se puede eliminar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fusionarCliente() {
        Cliente origen = panel.getClienteSeleccionado();
        if (origen == null) {
            JOptionPane.showMessageDialog(vista, "Seleccione el cliente a fusionar (el que se eliminará).",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Cliente> todos = model.obtenerTodosLosClientes();
        List<Cliente> candidatos = todos.stream()
            .filter(c -> c.getId() != origen.getId())
            .collect(Collectors.toList());

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

        model.fusionarClientes(origen.getId(), destino.getId());
        cargarDatos();
    }
}
