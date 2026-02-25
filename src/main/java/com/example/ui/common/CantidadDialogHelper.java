package com.example.ui.common;

import javax.swing.*;
import java.awt.*;

import com.example.common.constants.Constantes;

public final class CantidadDialogHelper {

    private CantidadDialogHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Integer pedirCantidad(Component parent, String descripcion, int cantidadDisponible) {
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
        chkTodos.addActionListener(e -> {
            if (chkTodos.isSelected()) {
                spinner.setValue(cantidadDisponible);
                spinner.setEnabled(false);
            } else {
                spinner.setEnabled(true);
            }
        });

        gbc.gridx = 1;
        panel.add(chkTodos, gbc);

        int opcion = JOptionPane.showConfirmDialog(
            parent,
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
            JOptionPane.showMessageDialog(parent,
                String.format(Constantes.Mensajes.CANTIDAD_AVANZAR_RANGO, cantidadDisponible),
                Constantes.Mensajes.TITULO_ADVERTENCIA,
                JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return cantidad;
    }
}


