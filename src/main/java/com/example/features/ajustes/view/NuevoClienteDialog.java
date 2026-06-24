package com.example.features.ajustes.view;

import javax.swing.*;
import java.awt.*;

public class NuevoClienteDialog extends JDialog {

    private final JTextField txtNombre = new JTextField(25);
    private String resultado;

    public NuevoClienteDialog(Window parent) {
        super(parent, "Nuevo cliente", ModalityType.APPLICATION_MODAL);
        resultado = null;
        construirUI();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
    }

    public String obtenerNombre() { return resultado; }

    private void construirUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(txtNombre, gbc);

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e  -> confirmar());
        btnCancelar.addActionListener(e -> dispose());
        txtNombre.addActionListener(e   -> confirmar());

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(btnGuardar, gbc);
        gbc.gridx = 1;
        panel.add(btnCancelar, gbc);

        add(panel);
    }

    private void confirmar() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El nombre no puede estar vacío.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        resultado = nombre;
        dispose();
    }
}
