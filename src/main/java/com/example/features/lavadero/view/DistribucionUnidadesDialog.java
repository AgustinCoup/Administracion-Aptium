package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;

import javax.swing.*;
import java.awt.*;

/**
 * "¿Cuántas de estas N?" — spinner acotado al máximo disponible, con un checkbox
 * "Todas (N)" para saltar el caso más frecuente (repartir todo) sin tipear.
 * Molde: {@link EquipoSubdivisionDialog}.
 *
 * <p>Lo usan dos pantallas que hacen la misma pregunta sobre cosas distintas: Ciclos, al
 * repartir un elemento disponible entre lavarropas, y Salidas, al marcar Listo una tanda
 * ya lavada. Sólo cambian el título y la línea que identifica el ítem, así que las dos
 * variantes son fábricas y no clases: un solo widget que no puede divergir.</p>
 *
 * <p>Resultado: la cantidad elegida, o 0 si se canceló.
 */
public class DistribucionUnidadesDialog extends JDialog {

    private final JSpinner  spinner;
    private final JCheckBox chkTodas;
    private final int max;
    private int cantidad = 0;
    private int ultimoValorManual = 1;

    /** Reparto de un elemento disponible entre lavarropas (Ciclos). */
    public static DistribucionUnidadesDialog paraCiclos(Frame parent, String elementoNombre,
                                                        String clienteNombre, int max) {
        return new DistribucionUnidadesDialog(parent, Constantes.Titulos.DISTRIBUCION_UNIDADES,
            String.format(Constantes.Textos.FORMATO_ITEM_DISPONIBLE, elementoNombre, clienteNombre, max),
            max);
    }

    /**
     * Marcado parcial de una tanda lavada (Salidas). Muestra además el lavarropas: el
     * nombre del elemento no distingue dos tandas del mismo cliente lavadas por separado.
     */
    public static DistribucionUnidadesDialog paraSalidas(Frame parent, String elementoNombre,
                                                          String clienteNombre, int lavarropasNumero,
                                                          int max) {
        return new DistribucionUnidadesDialog(parent, Constantes.Titulos.CANTIDAD_A_MARCAR_LISTO,
            String.format(Constantes.Textos.FORMATO_ITEM_LAVADO,
                          elementoNombre, clienteNombre, lavarropasNumero, max),
            max);
    }

    private DistribucionUnidadesDialog(Frame parent, String titulo, String encabezado, int max) {
        super(parent, titulo, true);
        this.max = max;
        setLayout(new BorderLayout(5, 5));

        add(new JLabel(encabezado, SwingConstants.CENTER), BorderLayout.NORTH);

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
