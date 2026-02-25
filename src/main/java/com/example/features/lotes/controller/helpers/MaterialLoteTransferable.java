package com.example.features.lotes.controller.helpers;

import com.example.features.lotes.view.helpers.MaterialLoteItem;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class MaterialLoteTransferable implements Transferable {
    private final MaterialLoteItem item;
    private final DataFlavor materialFlavor;

    public MaterialLoteTransferable(MaterialLoteItem item, DataFlavor materialFlavor) {
        this.item = item;
        this.materialFlavor = materialFlavor;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{materialFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return materialFlavor != null && materialFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return item;
    }
}


