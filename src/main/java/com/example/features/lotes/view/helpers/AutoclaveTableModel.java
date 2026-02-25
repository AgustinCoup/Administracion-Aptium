package com.example.features.lotes.view.helpers;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * TableModel para mostrar autoclaves con nombre y capacidad.
 */
public class AutoclaveTableModel extends AbstractTableModel {
    
    private final String[] columnas = {"Nombre", "Estado", "Capacidad"};
    private List<AutoclaveItem> autoclaves = new ArrayList<>();

    @Override
    public int getRowCount() {
        return autoclaves.size();
    }

    @Override
    public int getColumnCount() {
        return columnas.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnas[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AutoclaveItem item = autoclaves.get(rowIndex);
        switch (columnIndex) {
            case 0: return item.getNombre();
            case 1: return item.isOcupado() ? "Ocupado" : "Libre";
            case 2: return item.getCapacidad();
            default: return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 2) {
            return Integer.class;
        }
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public void setAutoclaves(List<AutoclaveItem> autoclaves) {
        this.autoclaves = new ArrayList<>(autoclaves);
        fireTableDataChanged();
    }

    public AutoclaveItem getAutoclaveAt(int row) {
        if (row < 0 || row >= autoclaves.size()) {
            return null;
        }
        return autoclaves.get(row);
    }

    public void clear() {
        autoclaves.clear();
        fireTableDataChanged();
    }
}


