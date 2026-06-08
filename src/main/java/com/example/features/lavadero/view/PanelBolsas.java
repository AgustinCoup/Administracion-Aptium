package com.example.features.lavadero.view;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PanelBolsas extends JPanel {

    private final List<BolsaRow> filas = new ArrayList<>();
    private JLabel  lblTotal;
    private JPanel  listPanel;
    private final Font inputFont;
    private final int  inputHeight;

    public PanelBolsas(Font inputFont, int inputHeight) {
        this.inputFont   = inputFont;
        this.inputHeight = inputHeight;

        setLayout(new BorderLayout(5, 5));

        listPanel = new JPanel(new GridBagLayout());
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        lblTotal = new JLabel("Total: 0.00 kg");
        lblTotal.setFont(Estilos.Fuentes.LABEL);
        JPanel panelTotal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        panelTotal.add(lblTotal);

        JButton btnAgregar  = new JButton(Constantes.Botones.AGREGAR);
        JButton btnEliminar = new JButton(Constantes.Botones.ELIMINAR);
        btnAgregar.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnEliminar.setFont(Estilos.Fuentes.BOTON_PEQUENO);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelBotones.add(btnEliminar);
        panelBotones.add(btnAgregar);

        JPanel panelSur = new JPanel(new BorderLayout());
        panelSur.add(panelTotal,   BorderLayout.WEST);
        panelSur.add(panelBotones, BorderLayout.EAST);
        add(panelSur, BorderLayout.SOUTH);

        agregarFila();

        btnAgregar.addActionListener(e -> {
            agregarFila();
            listPanel.revalidate();
            listPanel.repaint();
        });
        btnEliminar.addActionListener(e -> {
            eliminarUltimaFila();
            listPanel.revalidate();
            listPanel.repaint();
        });
    }

    public List<BolsaRow> getFilas() {
        return filas;
    }

    public void limpiar() {
        while (!filas.isEmpty()) eliminarFila(filas.get(filas.size() - 1));
        agregarFila();
        listPanel.revalidate();
        listPanel.repaint();
        actualizarTotal();
    }

    private void agregarFila() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 10);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        int rowIdx = filas.size();

        JLabel lblNum = new JLabel("Bolsa " + (rowIdx + 1) + ":");
        lblNum.setFont(inputFont);

        JTextField txtPeso = new JTextField();
        txtPeso.setFont(inputFont);
        txtPeso.setColumns(10);
        txtPeso.setMargin(Estilos.Espaciados.INSETS_INPUT);
        txtPeso.setPreferredSize(new Dimension(100, inputHeight));
        txtPeso.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { actualizarTotal(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { actualizarTotal(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { actualizarTotal(); }
        });

        JLabel lblKg = new JLabel("kg");
        lblKg.setFont(inputFont);

        JButton btnDel = new JButton(Constantes.Botones.ELIMINAR_FILA);
        btnDel.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnDel.setPreferredSize(new Dimension(45, inputHeight));
        btnDel.setMargin(new Insets(0, 0, 0, 0));
        btnDel.setToolTipText(Constantes.Textos.TOOLTIP_ELIMINAR_FILA);

        BolsaRow fila = new BolsaRow(lblNum, txtPeso, lblKg, btnDel);
        btnDel.addActionListener(e -> eliminarFila(fila));

        gbc.gridx = 0; gbc.gridy = rowIdx; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        listPanel.add(lblNum, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        listPanel.add(txtPeso, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(5, 5, 5, 5);
        listPanel.add(lblKg, gbc);

        gbc.gridx = 3;
        gbc.insets = new Insets(5, 0, 5, 0);
        listPanel.add(btnDel, gbc);

        filas.add(fila);
        actualizarTotal();
    }

    private void eliminarUltimaFila() {
        if (!filas.isEmpty()) eliminarFila(filas.get(filas.size() - 1));
    }

    private void eliminarFila(BolsaRow fila) {
        if (filas.remove(fila)) {
            listPanel.remove(fila.lblNumero);
            listPanel.remove(fila.txtPeso);
            listPanel.remove(fila.lblKg);
            listPanel.remove(fila.btnEliminar);
            listPanel.revalidate();
            listPanel.repaint();
            actualizarTotal();
        }
    }

    private void actualizarTotal() {
        BigDecimal total = filas.stream()
            .map(f -> {
                try {
                    return new BigDecimal(f.txtPeso.getText().trim().replace(",", "."));
                } catch (NumberFormatException e) {
                    return BigDecimal.ZERO;
                }
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotal.setText(String.format("Total: %s kg", total.toPlainString()));
    }

    public static class BolsaRow {
        public final JLabel     lblNumero;
        public final JTextField txtPeso;
        public final JLabel     lblKg;
        public final JButton    btnEliminar;

        public BolsaRow(JLabel lblNumero, JTextField txtPeso, JLabel lblKg, JButton btnEliminar) {
            this.lblNumero   = lblNumero;
            this.txtPeso     = txtPeso;
            this.lblKg       = lblKg;
            this.btnEliminar = btnEliminar;
        }
    }
}
