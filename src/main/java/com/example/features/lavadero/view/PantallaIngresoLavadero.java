package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class PantallaIngresoLavadero extends JPanel {

    private JTextField  txtCliente;
    private PanelBolsas panelBolsas;
    private JButton     btnGuardar;
    private JButton     btnCancelar;

    private final CardLayout navegador;
    private final JPanel     contenedor;

    private int selectedClienteId = -1;

    public PantallaIngresoLavadero(CardLayout navegador, JPanel contenedor) {
        this.navegador  = navegador;
        this.contenedor = contenedor;

        setLayout(new BorderLayout());

        PanelHeader header = new PanelHeader(
            Constantes.Titulos.INGRESO_LAVADERO,
            navegador,
            contenedor,
            Constantes.Pantallas.LAVADERO
        );
        configurarVolverConLimpieza(header);
        add(header, BorderLayout.NORTH);

        add(construirFormulario(), BorderLayout.CENTER);
        add(construirPanelBotones(), BorderLayout.SOUTH);
    }

    private JPanel construirFormulario() {
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(Estilos.Espaciados.BORDE_FORMULARIO);

        Font labelFont   = Estilos.Fuentes.LABEL;
        Font inputFont   = Estilos.Fuentes.INPUT;
        int  inputHeight = Estilos.Dimensiones.calcularAlturaInput();

        txtCliente = new JTextField();
        txtCliente.setFont(inputFont);
        txtCliente.setColumns(20);
        txtCliente.setMargin(Estilos.Espaciados.INSETS_INPUT);
        txtCliente.setPreferredSize(new Dimension(0, inputHeight));

        panelBolsas = new PanelBolsas(inputFont, inputHeight);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 10);

        // Fila 0: Cliente
        JLabel lblCliente = new JLabel(Constantes.Textos.LABEL_CLIENTE);
        lblCliente.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.NONE;
        formulario.add(lblCliente, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formulario.add(txtCliente, gbc);

        // Fila 1: Bolsas
        JLabel lblBolsas = new JLabel("Bolsas:");
        lblBolsas.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill   = GridBagConstraints.NONE;
        gbc.insets = new Insets(8, 5, 2, 10);
        formulario.add(lblBolsas, gbc);

        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill   = GridBagConstraints.BOTH;
        gbc.insets = new Insets(2, 5, 5, 5);
        formulario.add(panelBolsas, gbc);

        return formulario;
    }

    private JPanel construirPanelBotones() {
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        btnGuardar  = new JButton(Constantes.Botones.GUARDAR);
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnGuardar.setFont(Estilos.Fuentes.INPUT);
        btnCancelar.setFont(Estilos.Fuentes.INPUT);

        btnCancelar.addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
        });

        botones.add(btnGuardar);
        botones.add(btnCancelar);
        return botones;
    }

    private void configurarVolverConLimpieza(PanelHeader header) {
        for (ActionListener al : header.getBtnVolver().getActionListeners()) {
            header.getBtnVolver().removeActionListener(al);
        }
        header.getBtnVolver().addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
        });
    }

    public JTextField  getTxtCliente()              { return txtCliente; }
    public PanelBolsas getPanelBolsas()             { return panelBolsas; }
    public JButton     getBtnGuardar()              { return btnGuardar; }
    public JButton     getBtnCancelar()             { return btnCancelar; }
    public int         getSelectedClienteId()       { return selectedClienteId; }
    public void        setSelectedClienteId(int id) { this.selectedClienteId = id; }

    public void limpiarFormulario() {
        txtCliente.setText("");
        selectedClienteId = -1;
        panelBolsas.limpiar();
    }

    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }

    public void mostrarAdvertencia(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje,
            Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }
}
