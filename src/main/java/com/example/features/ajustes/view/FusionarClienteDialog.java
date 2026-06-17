package com.example.features.ajustes.view;

import com.example.features.clientes.model.Cliente;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class FusionarClienteDialog extends JDialog {

    private final DefaultListModel<Cliente> listModel = new DefaultListModel<>();
    private final JList<Cliente>            lista     = new JList<>(listModel);
    private Cliente resultado;

    public FusionarClienteDialog(Window parent, Cliente origen, List<Cliente> candidatos) {
        super(parent, "Fusionar cliente", ModalityType.APPLICATION_MODAL);
        resultado = null;
        construirUI(origen, candidatos);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(420, 380);
        setLocationRelativeTo(parent);
    }

    public Cliente obtenerClienteDestino() { return resultado; }

    private void construirUI(Cliente origen, List<Cliente> candidatos) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel lblInfo = new JLabel(
            "<html>Se eliminará: <b>" + origen.getNombre() + "</b><br>"
            + "Sus equipos e ingresos pasarán al cliente destino seleccionado.</html>");
        panel.add(lblInfo, BorderLayout.NORTH);

        candidatos.forEach(listModel::addElement);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v,
                    int idx, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, v, idx, sel, focus);
                if (v instanceof Cliente) setText(((Cliente) v).getNombre());
                return this;
            }
        });
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(lista), BorderLayout.CENTER);

        JButton btnFusionar = new JButton("Fusionar");
        JButton btnCancelar = new JButton("Cancelar");
        btnFusionar.addActionListener(e -> confirmar());
        btnCancelar.addActionListener(e -> dispose());

        JPanel barraInferior = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        barraInferior.add(btnFusionar);
        barraInferior.add(btnCancelar);
        panel.add(barraInferior, BorderLayout.SOUTH);

        add(panel);
    }

    private void confirmar() {
        Cliente seleccionado = lista.getSelectedValue();
        if (seleccionado == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un cliente destino.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        resultado = seleccionado;
        dispose();
    }
}
