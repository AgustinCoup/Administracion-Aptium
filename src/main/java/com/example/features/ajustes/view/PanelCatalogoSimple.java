package com.example.features.ajustes.view;

import com.example.ui.common.FilterUiHelper;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * ABM genérico de catálogo simple: tabla {@code {Nombre, Estado}} + buscador + Agregar
 * (opcional) / Dar de baja / Reactivar. Calcado de {@link PanelGestionClientes}.
 *
 * <p>Sirve a los catálogos que son sólo nombre + baja lógica (elementos de Lavadero, jabones,
 * insumos, "Otros"). Ortopedias necesita una columna de código extra y no tiene alta desde
 * Ajustes, así que usa {@link PanelCatalogoOrtopedias} en vez de forzar flags acá — es la
 * variante en la que la repetición hubiera sido especulativa, no real.</p>
 *
 * @param <T> tipo del ítem de catálogo (p. ej. {@code ElementoCatalogo}, {@code JabonCatalogo})
 */
public class PanelCatalogoSimple<T> extends JPanel {

    private final CatalogoTableModel<T>              tableModel;
    private final TableRowSorter<CatalogoTableModel<T>> sorter;
    private final JTable                             tabla;
    private final JTextField                         txtBuscar = new JTextField(20);

    private Runnable onAgregar;
    private Runnable onDarDeBaja;
    private Runnable onReactivar;

    public PanelCatalogoSimple(Function<T, String> nombreFn, Function<T, Boolean> activoFn, boolean permiteAgregar) {
        this.tableModel = new CatalogoTableModel<>(nombreFn, activoFn);
        this.sorter     = new TableRowSorter<>(tableModel);
        this.tabla      = new JTable(tableModel);

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
        add(crearBarraBotones(permiteAgregar), BorderLayout.SOUTH);
    }

    private void aplicarFiltro() {
        String texto = txtBuscar.getText().trim();
        if (texto.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 0));
        }
    }

    private JPanel crearBarraBotones(boolean permiteAgregar) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        if (permiteAgregar) {
            JButton btnAgregar = new JButton("Agregar");
            btnAgregar.addActionListener(e -> { if (onAgregar != null) onAgregar.run(); });
            panel.add(btnAgregar);
        }

        JButton btnDarDeBaja = new JButton("Dar de baja");
        JButton btnReactivar = new JButton("Reactivar");
        btnDarDeBaja.addActionListener(e -> { if (onDarDeBaja != null) onDarDeBaja.run(); });
        btnReactivar.addActionListener(e -> { if (onReactivar != null) onReactivar.run(); });
        panel.add(btnDarDeBaja);
        panel.add(btnReactivar);
        return panel;
    }

    public void setDatos(List<T> datos) { tableModel.setDatos(datos); }

    public T getSeleccionado() {
        int row = tabla.getSelectedRow();
        if (row < 0) return null;
        return tableModel.getItemAt(tabla.convertRowIndexToModel(row));
    }

    public void limpiarBusqueda() { txtBuscar.setText(""); }

    public void setOnAgregar(Runnable r)   { onAgregar   = r; }
    public void setOnDarDeBaja(Runnable r) { onDarDeBaja = r; }
    public void setOnReactivar(Runnable r) { onReactivar = r; }

    // ── TableModel interno ────────────────────────────────────────────────────

    private static class CatalogoTableModel<T> extends AbstractTableModel {

        private static final String[] COLUMNAS = { "Nombre", "Estado" };

        private final Function<T, String>  nombreFn;
        private final Function<T, Boolean> activoFn;
        private       List<T>              datos = new ArrayList<>();

        CatalogoTableModel(Function<T, String> nombreFn, Function<T, Boolean> activoFn) {
            this.nombreFn = nombreFn;
            this.activoFn = activoFn;
        }

        void setDatos(List<T> lista) {
            this.datos = new ArrayList<>(lista);
            fireTableDataChanged();
        }

        T getItemAt(int row) { return datos.get(row); }

        @Override public int getRowCount()    { return datos.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int col) { return COLUMNAS[col]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            T item = datos.get(row);
            return switch (col) {
                case 0 -> nombreFn.apply(item);
                case 1 -> Boolean.TRUE.equals(activoFn.apply(item)) ? "Activo" : "De baja";
                default -> null;
            };
        }
    }
}
