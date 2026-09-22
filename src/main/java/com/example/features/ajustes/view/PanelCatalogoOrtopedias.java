package com.example.features.ajustes.view;

import com.example.features.catalogo.model.ItemCatalogo;
import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ABM de baja lógica del catálogo de Ortopedias ({@code catalogo_descripciones}, columna
 * {@code vigente} desde V16).
 *
 * <p><b>Sin botón Agregar, a propósito.</b> El alta ({@code CatalogoDAO.guardarDescripcion}) es
 * la entrada de código muerto que {@code hallazgos-arquitectura-pendientes.md} #10 marca como la
 * única que necesitaría guarda el día que deje de estarlo — este plan sólo prende y apaga
 * {@code vigente}, no cablea ese alta.</p>
 *
 * <p>Columnas extra respecto de {@link PanelCatalogoSimple}: lleva Código además de Nombre y
 * Estado, así que no reusa ese genérico — meterle una columna opcional ahí lo hubiera llenado de
 * flags para un solo llamador.</p>
 */
public class PanelCatalogoOrtopedias extends JPanel {

    private static final int COLUMNA_FILTRO = 1; // Descripción

    private final OrtopediasTableModel              tableModel = new OrtopediasTableModel();
    private final TableRowSorter<OrtopediasTableModel> sorter  = new TableRowSorter<>(tableModel);
    private final JTable                            tabla      = new JTable(tableModel);
    private final JTextField                        txtBuscar  = new JTextField(20);

    private Runnable onDarDeBaja;
    private Runnable onReactivar;

    public PanelCatalogoOrtopedias() {
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

        add(panelBuscar,            BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(crearBarraBotones(),    BorderLayout.SOUTH);
    }

    private void aplicarFiltro() {
        String texto = txtBuscar.getText().trim();
        if (texto.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto), COLUMNA_FILTRO));
        }
    }

    private JPanel crearBarraBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton btnDarDeBaja = new JButton("Dar de baja");
        JButton btnReactivar = new JButton("Reactivar");
        btnDarDeBaja.addActionListener(e -> { if (onDarDeBaja != null) onDarDeBaja.run(); });
        btnReactivar.addActionListener(e -> { if (onReactivar != null) onReactivar.run(); });
        panel.add(btnDarDeBaja);
        panel.add(btnReactivar);
        return panel;
    }

    public void setDatos(List<ItemCatalogo> datos) { tableModel.setDatos(datos); }

    public ItemCatalogo getSeleccionado() {
        int row = tabla.getSelectedRow();
        if (row < 0) return null;
        return tableModel.getItemAt(tabla.convertRowIndexToModel(row));
    }

    public void limpiarBusqueda() { txtBuscar.setText(""); }

    public void setOnDarDeBaja(Runnable r) { onDarDeBaja = r; }
    public void setOnReactivar(Runnable r) { onReactivar = r; }

    // ── TableModel interno ────────────────────────────────────────────────────

    private static class OrtopediasTableModel extends AbstractTableModel {

        private static final String[] COLUMNAS = { "Código", "Descripción", "Estado" };

        private List<ItemCatalogo> datos = new ArrayList<>();

        void setDatos(List<ItemCatalogo> lista) {
            this.datos = new ArrayList<>(lista);
            fireTableDataChanged();
        }

        ItemCatalogo getItemAt(int row) { return datos.get(row); }

        @Override public int getRowCount()    { return datos.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int col) { return COLUMNAS[col]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            ItemCatalogo item = datos.get(row);
            return switch (col) {
                case 0 -> item.codigo();
                case 1 -> item.descripcion();
                case 2 -> item.vigente() ? "Activo" : "De baja";
                default -> null;
            };
        }
    }
}
