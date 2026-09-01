package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.CategoriaElementoLavadero;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.model.IngresoLavaderoResumen;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PantallaClasificacionLavadero extends JPanel {

    /** Resultado del diálogo de alta de catálogo. */
    public record NuevoElementoCatalogo(String nombre, CategoriaElementoLavadero categoria) { }

    private final JComboBox<IngresoLavaderoResumen> cmbIngreso;
    private       PanelElementosClasificacion       panelElementos;
    private final JButton                           btnGuardar;
    private final JButton                           btnCancelar;
    private final JButton                           btnNuevoCatalogo;
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
        JLabel lblIngreso = new JLabel(Constantes.Textos.LABEL_INGRESO);
        lblIngreso.setFont(Estilos.Fuentes.LABEL);
        centerPanel.add(lblIngreso, gbc);

        cmbIngreso = new JComboBox<>();
        cmbIngreso.setFont(Estilos.Fuentes.LABEL);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        centerPanel.add(cmbIngreso, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel lblElementos = new JLabel(Constantes.Textos.LABEL_ELEMENTOS);
        lblElementos.setFont(Estilos.Fuentes.LABEL);
        centerPanel.add(lblElementos, gbc);

        panelElementos = new PanelElementosClasificacion(List.of());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        centerPanel.add(panelElementos, gbc);

        add(centerPanel, BorderLayout.CENTER);

        btnNuevoCatalogo = new JButton(Constantes.Botones.ANADIR_ELEMENTO_CATALOGO);
        btnNuevoCatalogo.setFont(Estilos.Fuentes.BOTON);
        JPanel southWest = new JPanel(new FlowLayout(FlowLayout.LEFT));
        southWest.add(btnNuevoCatalogo);

        btnGuardar  = new JButton(Constantes.Botones.GUARDAR);
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnGuardar.setFont(Estilos.Fuentes.BOTON);
        btnCancelar.setFont(Estilos.Fuentes.BOTON);
        JPanel southEast = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        southEast.add(btnCancelar);
        southEast.add(btnGuardar);

        JPanel south = new JPanel(new BorderLayout());
        south.add(southWest, BorderLayout.WEST);
        south.add(southEast, BorderLayout.CENTER);
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

    public PanelElementosClasificacion getPanelElementos()   { return panelElementos; }
    public JButton                     getBtnGuardar()       { return btnGuardar; }
    public JButton                     getBtnCancelar()      { return btnCancelar; }
    public JButton                     getBtnNuevoCatalogo() { return btnNuevoCatalogo; }

    /**
     * Pide nombre y categoría para un elemento nuevo del catálogo.
     *
     * @return los datos ingresados, o {@code null} si se canceló
     */
    public NuevoElementoCatalogo pedirNuevoElementoCatalogo() {
        JTextField txtNombre = new JTextField(20);
        txtNombre.setFont(Estilos.Fuentes.INPUT);
        JComboBox<CategoriaElementoLavadero> cmbCategoria =
            new JComboBox<>(CategoriaElementoLavadero.values());
        cmbCategoria.setFont(Estilos.Fuentes.INPUT);

        JLabel lblNombre = new JLabel(Constantes.Textos.LABEL_NOMBRE_ELEMENTO);
        lblNombre.setFont(Estilos.Fuentes.LABEL);
        JLabel lblCategoria = new JLabel(Constantes.Textos.LABEL_CATEGORIA);
        lblCategoria.setFont(Estilos.Fuentes.LABEL);

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(lblNombre);
        form.add(txtNombre);
        form.add(lblCategoria);
        form.add(cmbCategoria);

        int opcion = JOptionPane.showConfirmDialog(this, form,
            Constantes.Titulos.NUEVO_ELEMENTO_CATALOGO,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) return null;

        return new NuevoElementoCatalogo(
            txtNombre.getText(),
            (CategoriaElementoLavadero) cmbCategoria.getSelectedItem());
    }

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }
}
