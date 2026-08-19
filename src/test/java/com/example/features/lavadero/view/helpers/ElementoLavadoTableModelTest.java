package com.example.features.lavadero.view.helpers;

import com.example.features.lavadero.model.ElementoLavadoPendiente;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElementoLavadoTableModelTest {

    private final ElementoLavadoTableModel model = new ElementoLavadoTableModel();

    @Test
    void sinItems_noTieneFilas() {
        assertEquals(0, model.getRowCount());
    }

    @Test
    void setItemsNull_dejaLaTablaVacia() {
        model.setItems(List.of(item()));
        model.setItems(null);
        assertEquals(0, model.getRowCount());
    }

    @Test
    void getValueAt_devuelveCadaColumna() {
        model.setItems(List.of(item()));

        assertEquals("Batas", model.getValueAt(0, 0));
        assertEquals(6, model.getValueAt(0, 1));
        assertEquals("Hosp. A", model.getValueAt(0, 2));
        assertEquals(4, model.getValueAt(0, 3));
        assertEquals("12/08/2026 14:30", model.getValueAt(0, 4));
    }

    @Test
    void getColumnClass_columnasNumericasSonInteger() {
        assertEquals(Integer.class, model.getColumnClass(1));
        assertEquals(Integer.class, model.getColumnClass(3));
        assertEquals(String.class, model.getColumnClass(0));
    }

    @Test
    void isCellEditable_siempreFalse() {
        model.setItems(List.of(item()));
        assertFalse(model.isCellEditable(0, 0));
    }

    @Test
    void getItemAt_fueraDeRango_devuelveNull() {
        model.setItems(List.of(item()));
        assertNull(model.getItemAt(-1));
        assertNull(model.getItemAt(1));
    }

    private ElementoLavadoPendiente item() {
        return new ElementoLavadoPendiente(1, 10, 4, 100, 5, "Hosp. A", "Batas",
            10, 4, LocalDateTime.of(2026, 8, 12, 14, 30));
    }
}
