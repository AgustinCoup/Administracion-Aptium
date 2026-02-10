package com.example.view;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.function.BiConsumer;
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
            Constantes.Textos.TABLA_EQUIPOS_TITULO,
            Constantes.Textos.TABLA_MATERIALES_TITULO,
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
        lblCambiosPendientes = new JLabel(String.format(Constantes.Textos.CAMBIOS_PENDIENTES, 0));
        lblCambiosPendientes.setFont(Estilos.Fuentes.LABEL);
        panelInfo.add(lblCambiosPendientes);
        panel.add(panelInfo, BorderLayout.WEST);
        
        // Panel derecho: botones de acción
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        
        btnAvanzar = new JButton(Constantes.Textos.BOTON_SELECCIONE_MATERIAL);
        btnAvanzar.setFont(Estilos.Fuentes.BOTON);
        btnAvanzar.setEnabled(false);
        btnAvanzar.setVisible(false);
        
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnCancelar.setFont(Estilos.Fuentes.BOTON);
        btnCancelar.setEnabled(false);
        
        btnConfirmar = new JButton(Constantes.Botones.CONFIRMAR_GUARDAR);
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

    public void refrescarEstadosEquipos() {
        panelTablas.refrescarEstadosEquipos();
    }

    public void setOnEquipoSeleccionado(Consumer<Equipo> listener) {
        panelTablas.setOnEquipoSeleccionado(listener);
    }

    public void setOnMaterialSeleccionado(ListSelectionListener listener) {
        panelTablas.getTablaMateriales().getSelectionModel().addListSelectionListener(listener);
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
        lblCambiosPendientes.setText(String.format(Constantes.Textos.CAMBIOS_PENDIENTES, total));
    }

    public void setAvanzarEnabled(boolean enabled) {
        btnAvanzar.setEnabled(enabled);
    }

    public void setAvanzarVisible(boolean visible) {
        btnAvanzar.setVisible(visible);
    }

    public void setAvanzarTexto(String texto) {
        btnAvanzar.setText(texto);
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

    public Integer pedirCantidadParaAvanzar(String descripcion, int cantidadDisponible,
                                            BiConsumer<JCheckBox, JSpinner> configurarTodos) {
        if (cantidadDisponible <= 1) {
            return cantidadDisponible;
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel lbl = new JLabel(String.format(Constantes.Mensajes.CANTIDAD_AVANZAR_PROMPT, descripcion, cantidadDisponible));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(lbl, gbc);

        SpinnerNumberModel model = new SpinnerNumberModel(1, 1, cantidadDisponible, 1);
        JSpinner spinner = new JSpinner(model);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        spinner.setEditor(editor);
        editor.getTextField().setColumns(4);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(spinner, gbc);

        JCheckBox chkTodos = new JCheckBox(Constantes.Mensajes.CANTIDAD_AVANZAR_TODOS);
        if (configurarTodos != null) {
            configurarTodos.accept(chkTodos, spinner);
        }

        gbc.gridx = 1;
        panel.add(chkTodos, gbc);

        int opcion = JOptionPane.showConfirmDialog(
            this,
            panel,
            Constantes.Mensajes.TITULO_AVANZAR_SUBCANTIDAD,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (opcion != JOptionPane.OK_OPTION) {
            return null;
        }

        int cantidad = (Integer) spinner.getValue();
        if (cantidad <= 0 || cantidad > cantidadDisponible) {
            mostrarAdvertencia(String.format(Constantes.Mensajes.CANTIDAD_AVANZAR_RANGO, cantidadDisponible));
            return null;
        }

        return cantidad;
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
