package com.example.ui.common.dnd;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Utilidades para trabajar con la selección (posiblemente múltiple) de una
 * {@link JTable} de forma independiente del modelo concreto.
 */
public final class TableSelectionSupport {

    private TableSelectionSupport() {
        throw new UnsupportedOperationException("Clase de utilidades no instanciable");
    }

    /** Habilita selección múltiple por intervalos (Ctrl/Shift) en la tabla. */
    public static void enableMultiSelection(JTable table) {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    /**
     * Devuelve los ítems del modelo correspondientes a las filas seleccionadas,
     * en el orden en que aparecen en la vista. Convierte índices de vista a modelo
     * (robusto ante un {@code RowSorter}) y descarta los nulos que devuelva el mapper.
     *
     * @param table          tabla con la selección
     * @param itemAtModelRow función que mapea un índice de fila del modelo a su ítem
     * @param <T>            tipo del ítem
     * @return lista de ítems seleccionados (vacía si no hay selección)
     */
    public static <T> List<T> selectedItems(JTable table, IntFunction<T> itemAtModelRow) {
        List<T> seleccionados = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            T item = itemAtModelRow.apply(modelRow);
            if (item != null) {
                seleccionados.add(item);
            }
        }
        return seleccionados;
    }
}
