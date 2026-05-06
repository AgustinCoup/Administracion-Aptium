package com.example.features.equipos.view.helpers;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.ui.common.Estilos;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;

public class DetalleOrtopediaDialog extends JDialog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public DetalleOrtopediaDialog(Window parent, Equipo equipo) {
        super(parent, "Detalle Equipo #" + equipo.getId(), ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        add(crearPanelDatos(equipo),     BorderLayout.NORTH);
        add(crearPanelMateriales(equipo), BorderLayout.CENTER);
        add(crearPanelBoton(),           BorderLayout.SOUTH);

        setSize(650, 500);
        setLocationRelativeTo(parent);
        setResizable(true);
    }

    private JPanel crearPanelDatos(Equipo equipo) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Datos del equipo"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 6, 4, 6);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;

        int row = 0;
        agregarFila(panel, gbc, row++, "Cliente:",      val(equipo.getClienteNombre()));
        agregarFila(panel, gbc, row++, "Profesional:",  val(equipo.getProfesionalNombre()));
        agregarFila(panel, gbc, row++, "Paciente:",     val(equipo.getPacienteNombre()));
        agregarFila(panel, gbc, row++, "Institución:",  val(equipo.getInstitucionNombre()));
        agregarFila(panel, gbc, row++, "Estado:",       equipo.getEstado().getNombre());
        agregarFila(panel, gbc, row++, "Fecha ingreso:",
                equipo.getFechaIngreso() != null ? equipo.getFechaIngreso().format(FMT) : "—");
        agregarFila(panel, gbc, row,   "Lavado / Empaque:",
                (equipo.isRequiereLavado() ? "Lavado " : "") +
                (equipo.isRequiereEmpaque() ? "Empaque" : ""));
        return panel;
    }

    private JPanel crearPanelMateriales(Equipo equipo) {
        String[] cols = {"Código", "Descripción", "Cantidad", "Estado"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Material m : equipo.getMateriales()) {
            model.addRow(new Object[]{
                m.getCodigo(), val(m.getDescripcion()), m.getCantidad(), m.getEstado().getNombre()
            });
        }
        JTable tabla = new JTable(model);
        TableStyler.applyStandard(tabla);
        TableStyler.centerColumns(tabla, 0, 2, 3);
        tabla.getColumnModel().getColumn(3).setCellRenderer(TableStyler.createEstadoRenderer());

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
