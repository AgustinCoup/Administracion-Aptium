package com.example.ui.common.dnd;

import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del Transferable genérico multi-fila (Paso 1 del plan
 * {@code plans/lotes-multiseleccion-dnd-materiales.md}). Sin display de Swing.
 */
class MultiRowTransferableTest {

    private static final DataFlavor FLAVOR = crearFlavor(String.class);
    private static final DataFlavor OTRO_FLAVOR = crearFlavor(Integer.class);

    private static DataFlavor crearFlavor(Class<?> clazz) {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + clazz.getName() + "\"");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void soportaSuPropioFlavor() {
        MultiRowTransferable<String> t = new MultiRowTransferable<>(List.of("a"), FLAVOR);

        assertTrue(t.isDataFlavorSupported(FLAVOR));
        assertArrayEquals(new DataFlavor[]{FLAVOR}, t.getTransferDataFlavors());
    }

    @Test
    void rechazaOtroFlavor() {
        MultiRowTransferable<String> t = new MultiRowTransferable<>(List.of("a"), FLAVOR);

        assertFalse(t.isDataFlavorSupported(OTRO_FLAVOR));
        assertThrows(UnsupportedFlavorException.class, () -> t.getTransferData(OTRO_FLAVOR));
    }

    @Test
    void getTransferData_devuelveExactamenteLosItems() throws Exception {
        List<String> items = List.of("x", "y", "z");
        MultiRowTransferable<String> t = new MultiRowTransferable<>(items, FLAVOR);

        assertEquals(items, t.getTransferData(FLAVOR));
    }

    @Test
    @SuppressWarnings("unchecked")
    void copiaDefensiva_mutarElOrigenNoAfectaElContenido() throws Exception {
        List<String> origen = new ArrayList<>(List.of("a", "b"));
        MultiRowTransferable<String> t = new MultiRowTransferable<>(origen, FLAVOR);

        origen.add("c");

        List<String> data = (List<String>) t.getTransferData(FLAVOR);
        assertEquals(List.of("a", "b"), data);
    }
}
