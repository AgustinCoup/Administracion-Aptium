package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.view.helpers.LavarropasItem;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EquipoSubdivisionDialog extends JDialog {

    private final List<Integer>          seleccionados     = new ArrayList<>();
    private final Map<JCheckBox, Integer> numeroDeCheckbox = new LinkedHashMap<>();
    private final JLabel  lblFraccion  = new JLabel(String.format(
        Constantes.Textos.FORMATO_FRACCION, calcularFraccion(0)));
    private final JButton btnConfirmar = new JButton(Constantes.Botones.CONFIRMAR);

    public EquipoSubdivisionDialog(Frame parent, ElementoCicloItem equipo,
                                   List<LavarropasItem> candidatos,
                                   Integer lavarropasPreSelected,
                                   int unidad, int totalUnidades) {
        super(parent, totalUnidades == 1
            ? String.format(Constantes.Textos.FORMATO_TITULO_SUBDIVIDIR, equipo.getElementoNombre())
            : String.format(Constantes.Textos.FORMATO_TITULO_UNIDAD_DE,
                             unidad, totalUnidades, equipo.getElementoNombre()),
            true);
        setLayout(new BorderLayout(5, 5));

        add(new JLabel(String.format(Constantes.Textos.FORMATO_LABEL_SUBDIVIDIR,
                equipo.getElementoNombre(), equipo.getClienteNombre()),
            SwingConstants.CENTER), BorderLayout.NORTH);

        JPanel panelChk = new JPanel(new GridLayout(0, 2, 5, 3));
        for (LavarropasItem lv : candidatos) {
            JCheckBox chk = new JCheckBox(String.format(
                Constantes.Textos.FORMATO_LAVARROPAS_NUM, lv.getNumero()));
            if (lavarropasPreSelected != null && lv.getNumero() == lavarropasPreSelected) {
                chk.setSelected(true);
            }
            chk.addItemListener(e -> actualizarFraccion());
            numeroDeCheckbox.put(chk, lv.getNumero());
            panelChk.add(chk);
        }
        add(new JScrollPane(panelChk), BorderLayout.CENTER);

        btnConfirmar.addActionListener(e -> confirmar());
        JButton btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnCancelar.addActionListener(e -> dispose());

        JPanel panelSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        panelSouth.add(lblFraccion);
        panelSouth.add(btnCancelar);
        panelSouth.add(btnConfirmar);
        add(panelSouth, BorderLayout.SOUTH);

        actualizarFraccion();
        pack();
        setMinimumSize(new Dimension(280, 220));
        setLocationRelativeTo(parent);
    }

    private void actualizarFraccion() {
        int n = (int) numeroDeCheckbox.keySet().stream().filter(JCheckBox::isSelected).count();
        lblFraccion.setText(String.format(Constantes.Textos.FORMATO_FRACCION, calcularFraccion(n)));
        btnConfirmar.setEnabled(n > 0);
    }

    private void confirmar() {
        numeroDeCheckbox.forEach((chk, numero) -> {
            if (chk.isSelected()) seleccionados.add(numero);
        });
        dispose();
    }

    public List<Integer> getSeleccionados() {
        return Collections.unmodifiableList(seleccionados);
    }

    static String calcularFraccion(int n) {
        return n == 0 ? "—" : "1/" + n;
    }
}
