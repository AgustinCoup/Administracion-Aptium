package com.example.features.ajustes.view;

import com.example.ui.common.RestriccionesCampo;

import javax.swing.*;
import java.awt.*;

/** Alta de un lavarropas: sólo pide el número, que elige el operador. */
public class NuevoLavarropasDialog extends JDialog {

    private final JTextField txtNumero = new JTextField(10);
    private Integer resultado;

    public NuevoLavarropasDialog(Window parent) {
        super(parent, "Nuevo lavarropas", ModalityType.APPLICATION_MODAL);
        RestriccionesCampo.soloNumeros(txtNumero);
        construirUI();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
    }

    public Integer obtenerNumero() { return resultado; }

    private void construirUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Número:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(txtNumero, gbc);

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e  -> confirmar());
        btnCancelar.addActionListener(e -> dispose());
        txtNumero.addActionListener(e   -> confirmar());

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(btnGuardar, gbc);
        gbc.gridx = 1;
        panel.add(btnCancelar, gbc);

        add(panel);
    }

    private void confirmar() {
        String texto = txtNumero.getText().trim();
        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El número no puede estar vacío.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            resultado = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Número inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
    }
}
