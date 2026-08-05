package com.example.ui.common.dnd;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la extracción de selección de una JTable (Paso 1 del plan
 * {@code plans/lotes-multiseleccion-dnd-materiales.md}). Las tablas no se muestran:
 * Swing permite instanciarlas y manipular su selección sin display.
 */
class TableSelectionSupportTest {

    private JTable tablaConFilas(String... valores) {
        DefaultTableModel modelo = new DefaultTableModel(0, 1);
        for (String v : valores) {
            modelo.addRow(new Object[]{v});
        }
        JTable tabla = new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        return tabla;
    }

    private String valorEnFila(JTable tabla, int fila) {
        return (String) tabla.getModel().getValueAt(fila, 0);
    }

    @Test
    void enableMultiSelection_poneElModoMultiple() {
        JTable tabla = new JTable(new DefaultTableModel(0, 1));

        TableSelectionSupport.enableMultiSelection(tabla);

        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                tabla.getSelectionModel().getSelectionMode());
    }

    @Test
    void selectedItems_devuelveLosItemsSeleccionadosEnOrden() {
        JTable tabla = tablaConFilas("a", "b", "c", "d");
        tabla.setRowSelectionInterval(0, 0);
        tabla.addRowSelectionInterval(2, 2);

        List<String> res = TableSelectionSupport.selectedItems(tabla, fila -> valorEnFila(tabla, fila));

        assertEquals(List.of("a", "c"), res);
    }

    @Test
    void selectedItems_sinSeleccion_listaVacia() {
        JTable tabla = tablaConFilas("a", "b");

        assertTrue(TableSelectionSupport.selectedItems(tabla, fila -> valorEnFila(tabla, fila)).isEmpty());
    }

    @Test
    void selectedItems_descartaNulosDelMapper() {
        JTable tabla = tablaConFilas("a", "b", "c");
        tabla.setRowSelectionInterval(0, 2);

        List<String> res = TableSelectionSupport.selectedItems(
                tabla, fila -> fila == 1 ? null : valorEnFila(tabla, fila));

        assertEquals(List.of("a", "c"), res);
    }
}
