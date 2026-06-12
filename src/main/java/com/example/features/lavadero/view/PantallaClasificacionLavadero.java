package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.model.IngresoLavaderoResumen;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PantallaClasificacionLavadero extends JPanel {

    private final JComboBox<IngresoLavaderoResumen> cmbIngreso;
    private       PanelElementosClasificacion       panelElementos;
    private final JButton                           btnGuardar;
    private final JButton                           btnCancelar;
    private final JPanel                            centerPanel;

    public PantallaClasificacionLavadero(CardLayout navegador, JPanel contenedor) {
        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.CLASIFICACION_LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.LAVADERO
        );
        add(header, BorderLayout.NORTH);

        centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBorder(Estilos.Espaciados.BORDE_PRINCIPAL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        centerPanel.add(new JLabel("Ingreso:"), gbc);

        cmbIngreso = new JComboBox<>();
        cmbIngreso.setFont(Estilos.Fuentes.LABEL);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        centerPanel.add(cmbIngreso, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        centerPanel.add(new JLabel("Elementos:"), gbc);

        panelElementos = new PanelElementosClasificacion(List.of());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        centerPanel.add(panelElementos, gbc);

        add(centerPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnGuardar  = new JButton(Constantes.Botones.GUARDAR);
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnGuardar.setFont(Estilos.Fuentes.BOTON);
        btnCancelar.setFont(Estilos.Fuentes.BOTON);
        south.add(btnCancelar);
        south.add(btnGuardar);
        add(south, BorderLayout.SOUTH);
    }

    public void refrescar(List<IngresoLavaderoResumen> ingresos, List<ElementoCatalogo> catalogo) {
        cmbIngreso.removeAllItems();
        for (IngresoLavaderoResumen r : ingresos) cmbIngreso.addItem(r);

        GridBagLayout layout = (GridBagLayout) centerPanel.getLayout();
        GridBagConstraints gbc = layout.getConstraints(panelElementos);
        centerPanel.remove(panelElementos);
        panelElementos = new PanelElementosClasificacion(catalogo);
        centerPanel.add(panelElementos, gbc);
        centerPanel.revalidate();
        centerPanel.repaint();
    }

    public void limpiarFormulario() {
        cmbIngreso.setSelectedIndex(-1);
        panelElementos.limpiar();
    }

    public IngresoLavaderoResumen getSelectedIngreso() {
        return (IngresoLavaderoResumen) cmbIngreso.getSelectedItem();
    }

    public PanelElementosClasificacion getPanelElementos() { return panelElementos; }
    public JButton                     getBtnGuardar()     { return btnGuardar; }
    public JButton                     getBtnCancelar()    { return btnCancelar; }

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }
}
