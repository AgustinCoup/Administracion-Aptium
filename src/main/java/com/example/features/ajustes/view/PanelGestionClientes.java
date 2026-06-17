package com.example.features.ajustes.view;

import com.example.features.clientes.model.Cliente;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PanelGestionClientes extends JPanel {

    private final ClienteTableModel              tableModel = new ClienteTableModel();
    private final TableRowSorter<ClienteTableModel> sorter  = new TableRowSorter<>(tableModel);
    private final JTable                         tabla      = new JTable(tableModel);
    private final JTextField                     txtBuscar  = new JTextField(20);

    private Runnable onAgregar;
    private Runnable onEliminar;
    private Runnable onFusionar;

    public PanelGestionClientes() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tabla.setRowSorter(sorter);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(24);
        TableStyler.applyStandard(tabla);

        FilterUiHelper.bindOnTextChange(this::aplicarFiltro, txtBuscar);

        JPanel panelBuscar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelBuscar.add(new JLabel("Buscar: "));
        panelBuscar.add(txtBuscar);

        add(panelBuscar,           BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(crearBarraBotones(),   BorderLayout.SOUTH);
    }

    private void aplicarFiltro() {
        String texto = txtBuscar.getText().trim();
        if (texto.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 0));
        }
    }

    private JPanel crearBarraBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        JButton btnAgregar  = new JButton("Nuevo cliente");
        JButton btnEliminar = new JButton("Eliminar");
        JButton btnFusionar = new JButton("Fusionar");

        btnAgregar.addActionListener(e  -> { if (onAgregar  != null) onAgregar.run();  });
        btnEliminar.addActionListener(e -> { if (onEliminar != null) onEliminar.run(); });
        btnFusionar.addActionListener(e -> { if (onFusionar != null) onFusionar.run(); });

        panel.add(btnAgregar);
        panel.add(btnEliminar);
        panel.add(btnFusionar);
        return panel;
    }

    public void setDatos(List<Cliente> clientes) {
        tableModel.setDatos(clientes);
    }

    public Cliente getClienteSeleccionado() {
        int row = tabla.getSelectedRow();
        if (row < 0) return null;
        return tableModel.getClienteAt(tabla.convertRowIndexToModel(row));
    }

    public JTable getTabla() { return tabla; }

    public void setOnAgregar(Runnable r)  { onAgregar  = r; }
    public void setOnEliminar(Runnable r) { onEliminar = r; }
    public void setOnFusionar(Runnable r) { onFusionar = r; }

    // ── TableModel interno ────────────────────────────────────────────────────

    private static class ClienteTableModel extends AbstractTableModel {

        private static final String[] COLUMNAS = { "Nombre" };
        private List<Cliente> datos = new ArrayList<>();

        void setDatos(List<Cliente> lista) {
            this.datos = new ArrayList<>(lista);
            fireTableDataChanged();
        }

        Cliente getClienteAt(int row) { return datos.get(row); }

        @Override public int getRowCount()    { return datos.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int col) { return COLUMNAS[col]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            return datos.get(row).getNombre();
        }
    }
}
