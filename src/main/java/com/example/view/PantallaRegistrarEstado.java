package com.example.view;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

import com.example.constants.Constantes;
import com.example.model.Equipo;
import com.example.view.helpers.Estilos;
import com.example.view.helpers.PanelEquipoMaterial;
import com.example.view.helpers.PanelHeader;

/**
 * Pantalla para registrar cambios de estado en materiales.
 * 
 * Características:
 * - Permite avanzar materiales al siguiente estado
 * - Los cambios se almacenan en un buffer hasta que el usuario confirme
 * - El estado del equipo se recalcula automáticamente según el material más atrasado
 * - Usa PanelEquipoMaterial para reutilizar la lógica de visualización
 */
public class PantallaRegistrarEstado extends JPanel {
    
    private PanelEquipoMaterial panelTablas;
    private JButton btnAvanzar;
    private JButton btnConfirmar;
    private JButton btnCancelar;
    private JLabel lblCambiosPendientes;
    
    public PantallaRegistrarEstado(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        // Header reutilizable
        PanelHeader header = new PanelHeader(
            Constantes.Titulos.REGISTRAR_ESTADO, 
            navegador, 
            contenedor, 
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        // Panel central con tablas reutilizables
        panelTablas = new PanelEquipoMaterial(
            "Equipos / Clientes", 
            "Materiales del Equipo (Seleccione para avanzar)", 
            true  // Materiales editables
        );
        
        add(panelTablas, BorderLayout.CENTER);

        // Panel inferior con botones de acción
        add(crearPanelBotones(), BorderLayout.SOUTH);
        
    }

    /**
     * Crea el panel con los botones de acción.
     */
    private JPanel crearPanelBotones() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        
        // Panel izquierdo: información de cambios pendientes
        JPanel panelInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblCambiosPendientes = new JLabel("Cambios pendientes: 0");
        lblCambiosPendientes.setFont(Estilos.Fuentes.LABEL);
        panelInfo.add(lblCambiosPendientes);
        panel.add(panelInfo, BorderLayout.WEST);
        
        // Panel derecho: botones de acción
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        
        btnAvanzar = new JButton("Avanzar Material Seleccionado");
        btnAvanzar.setFont(Estilos.Fuentes.BOTON);
        btnAvanzar.setEnabled(false);
        
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnCancelar.setFont(Estilos.Fuentes.BOTON);
        btnCancelar.setEnabled(false);
        
        btnConfirmar = new JButton("Confirmar y Guardar");
        btnConfirmar.setFont(Estilos.Fuentes.BOTON);
        btnConfirmar.setEnabled(false);
        
        panelBotones.add(btnAvanzar);
        panelBotones.add(btnCancelar);
        panelBotones.add(btnConfirmar);
        
        panel.add(panelBotones, BorderLayout.EAST);
        
        return panel;
    }

    // ==================== Métodos para el Controller ====================

    public void actualizarEquipos(List<Equipo> equipos) {
        panelTablas.actualizarEquipos(equipos);
    }

    public Equipo getEquipoSeleccionado() {
        return panelTablas.getEquipoSeleccionado();
    }

    public int getMaterialSeleccionadoIndex() {
        return panelTablas.getMaterialSeleccionadoIndex();
    }

    public void recargarMateriales() {
        panelTablas.recargarMateriales();
    }

    public void setOnEquipoSeleccionado(Consumer<Equipo> listener) {
        panelTablas.setOnEquipoSeleccionado(listener);
    }

    public void setOnAvanzar(java.awt.event.ActionListener listener) {
        btnAvanzar.addActionListener(listener);
    }

    public void setOnConfirmar(java.awt.event.ActionListener listener) {
        btnConfirmar.addActionListener(listener);
    }

    public void setOnCancelar(java.awt.event.ActionListener listener) {
        btnCancelar.addActionListener(listener);
    }

    public void setCambiosPendientesCount(int total) {
        lblCambiosPendientes.setText("Cambios pendientes: " + total);
    }

    public void setAvanzarEnabled(boolean enabled) {
        btnAvanzar.setEnabled(enabled);
    }

    public void setConfirmarEnabled(boolean enabled) {
        btnConfirmar.setEnabled(enabled);
    }

    public void setCancelarEnabled(boolean enabled) {
        btnCancelar.setEnabled(enabled);
    }

    public void mostrarAdvertencia(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public boolean confirmar(String mensaje, String titulo) {
        int respuesta = JOptionPane.showConfirmDialog(this, mensaje, titulo,
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return respuesta == JOptionPane.YES_OPTION;
    }
}
