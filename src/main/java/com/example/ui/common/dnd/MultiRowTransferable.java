package com.example.ui.common.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.List;

/**
 * Transferable genérico que transporta varias filas seleccionadas de una tabla
 * dentro de la misma JVM. Reutilizable por cualquier feature con drag-and-drop
 * multi-fila; no depende de ningún modelo concreto.
 *
 * @param <T> tipo de los ítems transportados
 */
public final class MultiRowTransferable<T> implements Transferable {

    private final List<T> items;
    private final DataFlavor flavor;

    public MultiRowTransferable(List<T> items, DataFlavor flavor) {
        this.items = List.copyOf(items);
        this.flavor = flavor;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{flavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return this.flavor != null && this.flavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return items;
    }
}
