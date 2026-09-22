package com.example.features.ajustes.view;

import com.example.features.lavadero.model.Lavarropas;
import com.example.ui.common.TableStyler;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ABM de lavarropas: alta con el número que elige el operador, baja y reactivación lógicas.
 * Sin capacidad en litros — la columna se dropeó en la V27 porque ninguna pantalla la mostraba.
 */
public class PanelGestionLavarropas extends JPanel {

    private final LavarropasTableModel tableModel = new LavarropasTableModel();
    private final JTable               tabla      = new JTable(tableModel);

    private Runnable onAgregar;
    private Runnable onDarDeBaja;
    private Runnable onReactivar;

    public PanelGestionLavarropas() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(24);
        TableStyler.applyStandard(tabla);

        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(crearBarraBotones(),    BorderLayout.SOUTH);
    }

    private JPanel crearBarraBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton btnAgregar   = new JButton("Agregar");
        JButton btnDarDeBaja = new JButton("Dar de baja");
        JButton btnReactivar = new JButton("Reactivar");
        btnAgregar.addActionListener(e   -> { if (onAgregar   != null) onAgregar.run();   });
        btnDarDeBaja.addActionListener(e -> { if (onDarDeBaja != null) onDarDeBaja.run(); });
        btnReactivar.addActionListener(e -> { if (onReactivar != null) onReactivar.run(); });
        panel.add(btnAgregar);
        panel.add(btnDarDeBaja);
        panel.add(btnReactivar);
        return panel;
    }

    public void setDatos(List<Lavarropas> datos) { tableModel.setDatos(datos); }

    public Lavarropas getSeleccionado() {
        int row = tabla.getSelectedRow();
        if (row < 0) return null;
        return tableModel.getItemAt(row);
    }

    public void setOnAgregar(Runnable r)   { onAgregar   = r; }
    public void setOnDarDeBaja(Runnable r) { onDarDeBaja = r; }
    public void setOnReactivar(Runnable r) { onReactivar = r; }

    // ── TableModel interno ────────────────────────────────────────────────────

    private static class LavarropasTableModel extends AbstractTableModel {

        private static final String[] COLUMNAS = { "N°", "Estado" };

        private List<Lavarropas> datos = new ArrayList<>();

        void setDatos(List<Lavarropas> lista) {
            this.datos = new ArrayList<>(lista);
            fireTableDataChanged();
        }

        Lavarropas getItemAt(int row) { return datos.get(row); }

        @Override public int getRowCount()    { return datos.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int col) { return COLUMNAS[col]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            Lavarropas item = datos.get(row);
            return switch (col) {
                case 0 -> item.getNumero();
                case 1 -> item.isActivo() ? "Activo" : "De baja";
                default -> null;
            };
        }
    }
}
