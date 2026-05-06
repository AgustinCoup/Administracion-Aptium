package com.example.features.equipos.view.helpers;

import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.ui.common.Estilos;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;

public class DetalleOtrosDialog extends JDialog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public DetalleOtrosDialog(Window parent, EquipoOtros equipo) {
        super(parent, "Detalle Equipo Otros #" + equipo.getId(), ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        add(crearPanelDatos(equipo), BorderLayout.NORTH);
        if (equipo.getTipoIngreso() == TipoIngresoOtros.DETALLES && !equipo.getMateriales().isEmpty()) {
            add(crearPanelMateriales(equipo), BorderLayout.CENTER);
        }
        add(crearPanelBoton(), BorderLayout.SOUTH);

        setSize(580, 480);
        setLocationRelativeTo(parent);
        setResizable(true);
    }

    private JPanel crearPanelDatos(EquipoOtros equipo) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Datos del equipo"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        int row = 0;
        agregarFila(panel, gbc, row++, "Cliente:",      val(equipo.getClienteNombre()));
        agregarFila(panel, gbc, row++, "Tipo ingreso:", equipo.getTipoIngreso().name());
        agregarFila(panel, gbc, row++, "Estado:",       equipo.getEstado().getNombre());
        agregarFila(panel, gbc, row++, "Fecha ingreso:",
                equipo.getFechaIngreso() != null ? equipo.getFechaIngreso().format(FMT) : "—");

        if (equipo.getTipoIngreso() == TipoIngresoOtros.REMITO) {
            agregarFila(panel, gbc, row++, "ID Remito:",    val(equipo.getRemitoId()));
            agregarFila(panel, gbc, row++, "Cantidad:",
                    equipo.getRemitoCantidad() != null ? String.valueOf(equipo.getRemitoCantidad()) : "—");
            agregarFila(panel, gbc, row++, "Observaciones:", val(equipo.getRemitoObservaciones()));
        }

        agregarFila(panel, gbc, row, "Lavado / Empaque:",
                (equipo.isRequiereLavado() ? "Lavado " : "") +
                (equipo.isRequiereEmpaque() ? "Empaque" : ""));
        return panel;
    }

    private JPanel crearPanelMateriales(EquipoOtros equipo) {
        String[] cols = {"Descripción", "Cantidad", "Estado"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (MaterialOtros m : equipo.getMateriales()) {
            model.addRow(new Object[]{val(m.getDescripcion()), m.getCantidad(), m.getEstado().getNombre()});
        }
        JTable tabla = new JTable(model);
        TableStyler.applyStandard(tabla);
        TableStyler.centerColumns(tabla, 1, 2);
        tabla.getColumnModel().getColumn(2).setCellRenderer(TableStyler.createEstadoRenderer());

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Materiales (" + equipo.getMateriales().size() + ")"));
        panel.add(new JScrollPane(tabla), BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelBoton() {
        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnCerrar.addActionListener(e -> dispose());
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(btnCerrar);
        return panel;
    }

    private void agregarFila(JPanel panel, GridBagConstraints gbc, int row,
                              String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setFont(Estilos.Fuentes.LABEL);
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        JLabel val = new JLabel(value);
        val.setFont(Estilos.Fuentes.INPUT);
        panel.add(val, gbc);
    }

    private String val(String s) { return s != null && !s.isBlank() ? s : "—"; }
}
