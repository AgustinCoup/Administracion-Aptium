package com.example.features.lavadero.view.helpers;

import com.example.features.lavadero.model.ElementoCicloItem;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ElementoCargadoTableModel extends AbstractTableModel {

    private final String[] columnas = {"Elemento", "Cantidad", "Ingreso #", "Cliente"};
    private List<ElementoCicloItem> filas = new ArrayList<>();

    @Override public int getRowCount()    { return filas.size(); }
    @Override public int getColumnCount() { return columnas.length; }
    @Override public String getColumnName(int column) { return columnas[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ElementoCicloItem item = filas.get(rowIndex);
        switch (columnIndex) {
            case 0: return item.getElementoNombre();
            case 1: return item.getCantidadEnCiclo();
            case 2: return item.getIngresoId();
            case 3: return item.getClienteNombre();
            default: return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return (columnIndex == 1 || columnIndex == 2) ? Integer.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

    public void setItems(List<ElementoCicloItem> items) {
        filas = items != null ? new ArrayList<>(items) : new ArrayList<>();
        fireTableDataChanged();
    }

    public ElementoCicloItem getItemAt(int row) {
        return (row >= 0 && row < filas.size()) ? filas.get(row) : null;
    }

    public void clear() {
        filas.clear();
        fireTableDataChanged();
    }
}
