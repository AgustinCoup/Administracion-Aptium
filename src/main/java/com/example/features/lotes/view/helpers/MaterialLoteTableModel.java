package com.example.features.lotes.view.helpers;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class MaterialLoteTableModel extends AbstractTableModel {

    private final String[] columnas = {
        "Material", "Cantidad", "Volumen", "Volumen Total", "Equipo"
    };

    private final List<MaterialLoteItem> filas = new ArrayList<>();

    @Override
    public int getRowCount() {
        return filas.size();
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
        MaterialLoteItem item = filas.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return item.getDescripcion();
            case 1:
                return item.getCantidad();
            case 2:
                return item.getVolumen();
            case 3:
                return item.getVolumenTotal();
            case 4:
                return item.getEquipoId();
            default:
                return "";
        }
    }

    public void setItems(List<MaterialLoteItem> items) {
        filas.clear();
        if (items != null) {
            filas.addAll(items);
        }
        fireTableDataChanged();
    }

    public MaterialLoteItem getItemAt(int row) {
        if (row < 0 || row >= filas.size()) {
            return null;
        }
        return filas.get(row);
    }

    public void removeAt(int row) {
        if (row < 0 || row >= filas.size()) {
            return;
        }
        filas.remove(row);
        fireTableDataChanged();
    }

    public void updateAt(int row, MaterialLoteItem item) {
        if (row < 0 || row >= filas.size()) {
            return;
        }
        filas.set(row, item);
        fireTableRowsUpdated(row, row);
    }

    public List<MaterialLoteItem> getItems() {
        return new ArrayList<>(filas);
    }
}


