package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;

import javax.swing.*;
import java.awt.*;

/**
 * "¿Cuántas unidades distribuís ahora?" — spinner acotado al máximo disponible, con un
 * checkbox "Todas (N)" para saltar el caso más frecuente (repartir todo) sin tipear.
 * Molde: {@link EquipoSubdivisionDialog}.
 *
 * <p>Resultado: la cantidad elegida, o 0 si se canceló.
 */
public class DistribucionUnidadesDialog extends JDialog {

    private final JSpinner  spinner;
    private final JCheckBox chkTodas;
    private final int max;
    private int cantidad = 0;
    private int ultimoValorManual = 1;

    public DistribucionUnidadesDialog(Frame parent, String elementoNombre, String clienteNombre, int max) {
        super(parent, Constantes.Titulos.DISTRIBUCION_UNIDADES, true);
        this.max = max;
        setLayout(new BorderLayout(5, 5));

        add(new JLabel(String.format(Constantes.Textos.FORMATO_ITEM_DISPONIBLE,
                elementoNombre, clienteNombre, max), SwingConstants.CENTER), BorderLayout.NORTH);

        spinner = new JSpinner(new SpinnerNumberModel(1, 1, max, 1));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0"));
        spinner.addChangeListener(e -> {
            if (!chkTodasSeleccionado()) ultimoValorManual = (Integer) spinner.getValue();
        });

        chkTodas = new JCheckBox(String.format(Constantes.Textos.CHECK_TODAS_N, max));
        chkTodas.addItemListener(e -> onTodasToggled(chkTodas.isSelected()));

        JPanel panelCentro = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.gridy = 0;
        panelCentro.add(new JLabel(Constantes.Textos.LABEL_UNIDADES), gbc);
        gbc.gridx = 1;
        panelCentro.add(spinner, gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        panelCentro.add(chkTodas, gbc);
        add(panelCentro, BorderLayout.CENTER);

        JButton btnConfirmar = new JButton(Constantes.Botones.CONFIRMAR);
        btnConfirmar.addActionListener(e -> { cantidad = (Integer) spinner.getValue(); dispose(); });
        JButton btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnCancelar.addActionListener(e -> dispose());

        JPanel panelSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        panelSouth.add(btnCancelar);
        panelSouth.add(btnConfirmar);
        add(panelSouth, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(280, 160));
        setLocationRelativeTo(parent);
    }

    private boolean chkTodasSeleccionado() {
        return chkTodas != null && chkTodas.isSelected();
    }

    private void onTodasToggled(boolean tildada) {
        if (tildada) ultimoValorManual = (Integer) spinner.getValue();
        spinner.setValue(aplicarTodas(tildada, max, ultimoValorManual));
        spinner.setEnabled(!tildada);
    }

    public int getCantidad() { return cantidad; }

    /**
     * Qué valor debe tomar el spinner al tildar/destildar "Todas". Extraído para poder
     * testearlo sin construir el {@link JDialog}: en headless (así corren los tests del
     * repo) construir una {@code Window} lanza {@code HeadlessException} — mismo motivo
     * por el que {@link EquipoSubdivisionDialog} sólo testea su lógica estática.
     */
    static int aplicarTodas(boolean tildada, int max, int ultimoValorManual) {
        return tildada ? max : ultimoValorManual;
    }
}
