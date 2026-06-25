package com.example.features.lavadero.view;

import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.view.helpers.LavarropasItem;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EquipoSubdivisionDialog extends JDialog {

    private final List<Integer>   seleccionados = new ArrayList<>();
    private final List<JCheckBox> checkboxes    = new ArrayList<>();
    private final JLabel          lblFraccion   = new JLabel("Fracción: —");
    private final JButton         btnConfirmar  = new JButton("Confirmar");

    public EquipoSubdivisionDialog(Frame parent, ElementoCicloItem equipo,
                                   List<LavarropasItem> candidatos,
                                   Integer lavarropasPreSelected) {
        super(parent, "Subdividir: " + equipo.getElementoNombre(), true);
        setLayout(new BorderLayout(5, 5));

        add(new JLabel("<html><b>Subdividir:</b> " + equipo.getElementoNombre()
            + " — " + equipo.getClienteNombre() + "</html>",
            SwingConstants.CENTER), BorderLayout.NORTH);

        JPanel panelChk = new JPanel(new GridLayout(0, 2, 5, 3));
        for (LavarropasItem lv : candidatos) {
            JCheckBox chk = new JCheckBox("Lavarropas #" + lv.getNumero());
            if (lavarropasPreSelected != null && lv.getNumero() == lavarropasPreSelected) {
                chk.setSelected(true);
            }
            chk.addItemListener(e -> actualizarFraccion());
            checkboxes.add(chk);
            panelChk.add(chk);
        }
        add(new JScrollPane(panelChk), BorderLayout.CENTER);

        btnConfirmar.addActionListener(e -> confirmar());
        JButton btnCancelar = new JButton("Cancelar");
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
        int n = (int) checkboxes.stream().filter(JCheckBox::isSelected).count();
        lblFraccion.setText("Fracción: " + calcularFraccion(n));
        btnConfirmar.setEnabled(n > 0);
    }

    private void confirmar() {
        for (JCheckBox chk : checkboxes) {
            if (chk.isSelected()) {
                try {
                    seleccionados.add(Integer.parseInt(
                        chk.getText().replace("Lavarropas #", "").trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        dispose();
    }

    public List<Integer> getSeleccionados() {
        return Collections.unmodifiableList(seleccionados);
    }

    static String calcularFraccion(int n) {
        return n == 0 ? "—" : "1/" + n;
    }
}
