package com.example.features.lavadero.view.helpers;

import com.example.features.lavadero.model.ElementoCicloItem;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LavarropasCardTableModel extends AbstractTableModel {

    private static final String[] COLUMNAS = {"Elemento", "Cant.", "Fracción", "Ingreso #", "Cliente"};

    private List<ElementoCicloItem> items     = new ArrayList<>();
    private Map<Integer, Integer>   fracciones = new HashMap<>();

    public void setItems(List<ElementoCicloItem> items, Map<Integer, Integer> fracciones) {
        this.items      = new ArrayList<>(items);
        this.fracciones = new HashMap<>(fracciones);
        fireTableDataChanged();
    }

    @Override public int getRowCount()    { return items.size(); }
    @Override public int getColumnCount() { return COLUMNAS.length; }
    @Override public String getColumnName(int col) { return COLUMNAS[col]; }

    @Override
    public Object getValueAt(int row, int col) {
        ElementoCicloItem item = items.get(row);
        switch (col) {
            case 0: return item.getElementoNombre();
            case 1: return item.getCantidadEnCiclo();
            case 2: return item.isEquipo()
                        ? "1/" + fracciones.getOrDefault(item.getElementoClasificacionId(), 1)
                        : "—";
            case 3: return item.getIngresoId();
            case 4: return item.getClienteNombre();
            default: return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return (col == 1 || col == 3) ? Integer.class : String.class;
    }

    public ElementoCicloItem getItemAt(int row) {
        return (row >= 0 && row < items.size()) ? items.get(row) : null;
    }

    public List<ElementoCicloItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
