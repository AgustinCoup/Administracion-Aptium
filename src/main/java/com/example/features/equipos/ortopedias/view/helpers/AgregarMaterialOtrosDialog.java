package com.example.features.equipos.ortopedias.view.helpers;

import com.example.common.constants.Constantes;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.common.Estilos;
import com.example.ui.dialogs.NuevoElementoDialog;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

/**
 * Panel de formulario para el diálogo "Agregar Material" en equipos de tipo Otros.
 * A diferencia de {@link AgregarMaterialDialog}, la descripción es texto libre con
 * autocompletado contra catalogo_otros. Al perder el foco con una descripción que no
 * existe en el catálogo se abre {@link NuevoElementoDialog} (misma estética que el
 * diálogo de nuevo cliente/profesional/institución); si el usuario cancela, el campo
 * se vacía.
 */
public class AgregarMaterialOtrosDialog {

    public final JPanel panel;
    private final JTextField txtDesc;
    private final JSpinner   spinCant;
    private final JTextArea  txtMot;

    public AgregarMaterialOtrosDialog(
            Function<String, List<String>> buscar,
            Function<String, Boolean> verificar) {
        panel    = new JPanel(new GridBagLayout());
        txtDesc  = new JTextField();
        spinCant = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        txtMot   = new JTextArea(3, 25);
        construirPanel();
        configurarAutocomplete(buscar, verificar);
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

    private void configurarAutocomplete(
            Function<String, List<String>> buscar,
            Function<String, Boolean> verificar) {
        // confirmedText evita re-preguntar si el usuario mueve el foco sin cambiar el texto
        String[] confirmedText = {null};

        new AutocompleteListener<>(
            txtDesc,
            buscar,
            s -> confirmedText[0] = s, // seleccionado del popup → ya confirmado
            text -> {
                if (text.equals(confirmedText[0])) return;
                if (verificar.apply(text)) { confirmedText[0] = text; return; }
                Window w = SwingUtilities.getWindowAncestor(panel);
                NuevoElementoDialog d = new NuevoElementoDialog(
                    w, Constantes.Textos.ENTIDAD_CATALOGO_OTROS, text);
                d.setVisible(true);
                String nombre = d.obtenerResultado();
                if (nombre != null) {
                    confirmedText[0] = nombre;
                    txtDesc.setText(nombre);
                } else {
                    confirmedText[0] = null;
                    txtDesc.setText("");
                }
            },
            1 /* minChars */);
    }
}
