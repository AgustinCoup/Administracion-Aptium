package com.example.ui.dialogs;

import com.example.common.constants.Constantes;

import javax.swing.*;
import java.awt.*;

/**
 * Diálogo modal genérico para confirmar y (opcionalmente) editar el nombre de una nueva
 * entidad antes de agregarla. Muestra el nombre propuesto en un JTextField editable.
 *
 * Retorna el nombre confirmado (posiblemente editado) via {@link #obtenerResultado()},
 * o {@code null} si el usuario canceló.
 *
 * Usado como base de {@link AgregarAutocompletableDialog} y directamente en los flujos
 * de confirmación de catálogo donde no hay entidad tipada que persistir en el acto.
 */
public class NuevoElementoDialog extends JDialog {

    private final JTextField txtNombre;
    private String           resultado;

    public NuevoElementoDialog(Window parent, String nombreEntidad, String valorInicial) {
        super(parent,
              String.format(Constantes.Textos.DIALOG_TITULO_AGREGAR, nombreEntidad),
              ModalityType.APPLICATION_MODAL);
        resultado = null;
        txtNombre = new JTextField(valorInicial, 30);
        construirUI(nombreEntidad);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
    }

    /** Nombre confirmado (posiblemente editado por el usuario), o {@code null} si canceló. */
    public String obtenerResultado() { return resultado; }

    private void construirUI(String nombreEntidad) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        String mensaje = String.format(Constantes.Textos.DIALOG_MENSAJE_AGREGAR, nombreEntidad.toLowerCase());
        panel.add(new JLabel(mensaje), gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel(Constantes.Textos.LABEL_NOMBRE), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(txtNombre, gbc);

        JButton btnAgregar  = new JButton(Constantes.Botones.AGREGAR_TEXTO);
        JButton btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnAgregar.addActionListener(e  -> confirmar());
        btnCancelar.addActionListener(e -> cancelar());
        txtNombre.addActionListener(e   -> confirmar());

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(btnAgregar, gbc);
        gbc.gridx = 1;
        panel.add(btnCancelar, gbc);

        add(panel);
    }

    private void confirmar() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, Constantes.Mensajes.NOMBRE_VACIO,
                Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        resultado = nombre;
        dispose();
    }

    private void cancelar() {
        resultado = null;
        dispose();
    }
}
