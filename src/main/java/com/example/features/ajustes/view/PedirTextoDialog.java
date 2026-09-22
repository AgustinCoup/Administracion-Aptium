package com.example.features.ajustes.view;

import javax.swing.*;
import java.awt.*;

/** Diálogo genérico de un solo campo de texto: nombre de un jabón, un insumo, etc. */
public class PedirTextoDialog extends JDialog {

    private final JTextField txtValor = new JTextField(25);
    private String resultado;

    public PedirTextoDialog(Window parent, String titulo, String etiqueta) {
        super(parent, titulo, ModalityType.APPLICATION_MODAL);
        construirUI(etiqueta);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
    }

    public String obtenerValor() { return resultado; }

    private void construirUI(String etiqueta) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel(etiqueta), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(txtValor, gbc);

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e  -> confirmar());
        btnCancelar.addActionListener(e -> dispose());
        txtValor.addActionListener(e    -> confirmar());

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(btnGuardar, gbc);
        gbc.gridx = 1;
        panel.add(btnCancelar, gbc);

        add(panel);
    }

    private void confirmar() {
        String valor = txtValor.getText().trim();
        if (valor.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El valor no puede estar vacío.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        resultado = valor;
        dispose();
    }
}
