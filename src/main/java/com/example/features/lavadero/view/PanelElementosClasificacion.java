package com.example.features.lavadero.view;

import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.ui.common.DuplicadoHighlighter;
import com.example.ui.common.Estilos;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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

    /** Fondo original de los combos, capturado del primero que se crea. */
    private Color colorNormal = null;

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

        if (colorNormal == null) colorNormal = cmb.getBackground();
        cmb.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) tieneDuplicados();
        });

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
        // El combo nuevo arranca en el índice 0 sin que el usuario lo toque: el
        // ItemListener no dispara, así que el resaltado se refresca acá a mano.
        tieneDuplicados();
    }

    private void eliminarFila(ElementoFila fila) {
        filas.remove(fila);
        reconstruirPanel();
        tieneDuplicados();
    }

    /**
     * Marca en rojo (+ tooltip) los combos que tienen elegido el mismo elemento
     * del catálogo que otra fila, y restaura el fondo de los que no.
     *
     * @return true si hay al menos un elemento repetido (debe bloquearse el guardado)
     */
    public boolean tieneDuplicados() {
        List<JComboBox<ElementoCatalogo>> combos = new ArrayList<>();
        for (ElementoFila f : filas) combos.add(f.cmbElemento);

        return DuplicadoHighlighter.marcar(
            combos,
            cmb -> {
                Object sel = cmb.getSelectedItem();
                return sel instanceof ElementoCatalogo e ? String.valueOf(e.getId()) : "";
            },
            Function.identity(),
            colorNormal,
            "Elemento duplicado: unifique estas filas antes de guardar.");
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
