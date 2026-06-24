package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.ElementoCicloItem;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

public class ElementoCicloTransferable implements Transferable {

    private final ElementoCicloItem item;
    private final DataFlavor flavor;

    public ElementoCicloTransferable(ElementoCicloItem item, DataFlavor flavor) {
        this.item = item;
        this.flavor = flavor;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{flavor}; }

    @Override
    public boolean isDataFlavorSupported(DataFlavor f) { return flavor != null && flavor.equals(f); }

    @Override
    public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
        return item;
    }
}
