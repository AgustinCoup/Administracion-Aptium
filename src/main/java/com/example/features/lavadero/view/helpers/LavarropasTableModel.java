package com.example.features.lavadero.view.helpers;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class LavarropasTableModel extends AbstractTableModel {

    private final String[] columnas = {"N°", "Estado", "Capacidad (L)"};
    private List<LavarropasItem> items = new ArrayList<>();

    @Override public int getRowCount()    { return items.size(); }
    @Override public int getColumnCount() { return columnas.length; }
    @Override public String getColumnName(int column) { return columnas[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LavarropasItem item = items.get(rowIndex);
        switch (columnIndex) {
            case 0: return item.getNumero();
            case 1: return item.isOcupado() ? "Ocupado" : "Libre";
            case 2: return item.getCapacidadLitros();
            default: return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex != 1 ? Integer.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

    public void setItems(List<LavarropasItem> items) {
        this.items = new ArrayList<>(items);
        fireTableDataChanged();
    }

    public LavarropasItem getItemAt(int row) {
        return (row >= 0 && row < items.size()) ? items.get(row) : null;
    }
}
