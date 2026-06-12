package com.example.features.lavadero.view;

import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.ui.common.Estilos;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PanelElementosClasificacion extends JPanel {

    public static class ElementoFila {
        public final JComboBox<ElementoCatalogo> cmbElemento;
        public final JSpinner                    spnCantidad;
        public final JButton                     btnEliminar;

        ElementoFila(JComboBox<ElementoCatalogo> cmb, JSpinner spn, JButton btn) {
            this.cmbElemento = cmb;
            this.spnCantidad = spn;
            this.btnEliminar = btn;
        }
    }

    private final List<ElementoFila>     filas = new ArrayList<>();
    private final JPanel                 panelFilas;
    private final List<ElementoCatalogo> catalogo;

    public PanelElementosClasificacion(List<ElementoCatalogo> catalogo) {
        this.catalogo = catalogo;
        setLayout(new BorderLayout(0, 5));

        panelFilas = new JPanel(new GridBagLayout());
        JScrollPane scroll = new JScrollPane(panelFilas);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton btnAgregar = new JButton("+ Agregar elemento");
        btnAgregar.setFont(Estilos.Fuentes.LABEL);
        btnAgregar.addActionListener(e -> agregarFila());
        panelBotones.add(btnAgregar);
        add(panelBotones, BorderLayout.SOUTH);
    }

    private void agregarFila() {
        JComboBox<ElementoCatalogo> cmb = new JComboBox<>();
        for (ElementoCatalogo e : catalogo) cmb.addItem(e);
        cmb.setFont(Estilos.Fuentes.LABEL);

        SpinnerNumberModel spinModel = new SpinnerNumberModel(1, 1, 9999, 1);
        JSpinner spn = new JSpinner(spinModel);
        spn.setFont(Estilos.Fuentes.LABEL);
        ((JSpinner.DefaultEditor) spn.getEditor()).getTextField().setColumns(4);

        JButton btnX = new JButton("X");
        btnX.setFont(Estilos.Fuentes.LABEL);

        ElementoFila fila = new ElementoFila(cmb, spn, btnX);
        filas.add(fila);
        btnX.addActionListener(e -> eliminarFila(fila));

        reconstruirPanel();
    }

    private void eliminarFila(ElementoFila fila) {
        filas.remove(fila);
        reconstruirPanel();
    }

    private void reconstruirPanel() {
        panelFilas.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        for (int i = 0; i < filas.size(); i++) {
            ElementoFila f = filas.get(i);
            gbc.gridy = i;

            gbc.gridx = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            panelFilas.add(f.cmbElemento, gbc);

            gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panelFilas.add(f.spnCantidad, gbc);

            gbc.gridx = 2;
            panelFilas.add(f.btnEliminar, gbc);
        }

        gbc.gridy = filas.size(); gbc.weighty = 1.0; gbc.fill = GridBagConstraints.VERTICAL;
        panelFilas.add(Box.createVerticalGlue(), gbc);

        panelFilas.revalidate();
        panelFilas.repaint();
    }

    public List<ElementoFila> getFilas() {
        return Collections.unmodifiableList(filas);
    }

    public void limpiar() {
        filas.clear();
        reconstruirPanel();
    }
}
