package com.example.features.equipos.ortopedias.view.helpers;

import com.example.ui.common.Estilos;

import javax.swing.*;
import java.awt.*;

/**
 * Panel de formulario para el diálogo "Agregar Material" en equipos de tipo Otros.
 * A diferencia de {@link AgregarMaterialDialog}, la descripción es texto libre.
 */
public class AgregarMaterialOtrosDialog {

    public final JPanel panel;
    private final JTextField txtDesc;
    private final JSpinner   spinCant;
    private final JTextArea  txtMot;

    public AgregarMaterialOtrosDialog() {
        panel    = new JPanel(new GridBagLayout());
        txtDesc  = new JTextField();
        spinCant = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        txtMot   = new JTextArea(3, 25);
        construirPanel();
    }

    public String getDescripcion() { return txtDesc.getText().trim(); }
    public int    getCantidad()    { return (Integer) spinCant.getValue(); }
    public String getMotivo()      { return txtMot.getText().trim(); }

    private void construirPanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel lblDesc = new JLabel("Descripción:");
        lblDesc.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblDesc, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtDesc.setFont(Estilos.Fuentes.INPUT);
        panel.add(txtDesc, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        JLabel lblCant = new JLabel("Cantidad:");
        lblCant.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblCant, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spinCant.setFont(Estilos.Fuentes.INPUT);
        panel.add(spinCant, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        JLabel lblMot = new JLabel("Motivo:");
        lblMot.setFont(Estilos.Fuentes.LABEL);
        panel.add(lblMot, gbc);
        gbc.gridx = 1; gbc.weightx = 0.7; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        txtMot.setFont(Estilos.Fuentes.INPUT);
        txtMot.setLineWrap(true);
        txtMot.setWrapStyleWord(true);
        panel.add(new JScrollPane(txtMot), gbc);
    }
}
