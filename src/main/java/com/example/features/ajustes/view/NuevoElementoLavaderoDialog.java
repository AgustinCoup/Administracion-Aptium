package com.example.features.ajustes.view;

import com.example.features.lavadero.model.CategoriaElementoLavadero;

import javax.swing.*;
import java.awt.*;

/** Alta de un elemento del catálogo de Clasificación de Lavadero: nombre + categoría. */
public class NuevoElementoLavaderoDialog extends JDialog {

    private final JTextField txtNombre = new JTextField(25);
    private final JComboBox<CategoriaElementoLavadero> cmbCategoria =
        new JComboBox<>(CategoriaElementoLavadero.values());

    private String                    nombreResultado;
    private CategoriaElementoLavadero categoriaResultado;

    public NuevoElementoLavaderoDialog(Window parent) {
        super(parent, "Nuevo elemento de catálogo", ModalityType.APPLICATION_MODAL);
        construirUI();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
    }

    public String obtenerNombre() { return nombreResultado; }
    public CategoriaElementoLavadero obtenerCategoria() { return categoriaResultado; }

    private void construirUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Categoría:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(cmbCategoria, gbc);

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e  -> confirmar());
        btnCancelar.addActionListener(e -> dispose());

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
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
        nombreResultado    = nombre;
        categoriaResultado = (CategoriaElementoLavadero) cmbCategoria.getSelectedItem();
        dispose();
    }
}
