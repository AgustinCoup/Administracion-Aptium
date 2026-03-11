package com.example.ui.common;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.*;
import java.util.List;

/**
 * JComboBox personalizado que permite seleccionar múltiples elementos con checkboxes.
 * El popup permanece abierto mientras se seleccionan opciones.
 */
public class CheckableComboBox<E> extends JComboBox<E> {
    private static final long serialVersionUID = 1L;

    private DefaultComboBoxModel<E> model;
    private final Set<Integer> selectedIndices;
    private String displayFormat = "%d / %d";
    private String displayText = "Todos";

    private Runnable onSelectionChange;
    private boolean keepOpen = false;

    public CheckableComboBox(E[] items) {
        this(new DefaultComboBoxModel<>(items));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setModel(ComboBoxModel<E> aModel) {
        super.setModel(aModel);
        this.model = (DefaultComboBoxModel<E>) aModel;
        if (selectedIndices != null) {
            selectedIndices.clear();
            updateDisplay();
        }
    }

    public CheckableComboBox(DefaultComboBoxModel<E> model) {
        super(model);
        this.model = model;
        this.selectedIndices = new LinkedHashSet<>();

        // Configurar renderer personalizado
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                if (index == -1) {
                    return super.getListCellRendererComponent(list, displayText, index, isSelected, cellHasFocus);
                }
                JCheckBox checkBox = new JCheckBox(value != null ? value.toString() : "");
                checkBox.setSelected(selectedIndices.contains(index));
                return checkBox;
            }
        });

        // Bloquear edición de texto
        setEditable(false);

        // UI personalizado para mantener el popup abierto
        setUI(new KeepOpenComboBoxUI());

        // Mostrar resumen en el botón
        updateDisplay();
    }

    /**
     * UI personalizado que mantiene el popup abierto al hacer clic en los checkboxes.
     */
    private class KeepOpenComboBoxUI extends BasicComboBoxUI {
        @Override
        protected ComboPopup createPopup() {
            return new BasicComboPopup(comboBox) {
                @Override
                protected MouseMotionListener createListMouseMotionListener() {
                    return new MouseMotionListener() {
                        @Override
                        public void mouseDragged(MouseEvent e) {}

                        @Override
                        public void mouseMoved(MouseEvent e) {}
                    };
                }

                @Override
                protected MouseListener createListMouseListener() {
                    return new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            if (e.getSource() == list) {
                                int index = list.locationToIndex(e.getPoint());
                                if (index != -1 && list.getCellBounds(index, index).contains(e.getPoint())) {
                                    keepOpen = true;
                                    setSelectedIndex(index);
                                    list.repaint();
                                    keepOpen = false;
                                }
                            }
                        }
                    };
                }
            };
        }

        @Override
        public void setPopupVisible(JComboBox<?> c, boolean v) {
            if (!keepOpen) {
                super.setPopupVisible(c, v);
            }
        }
    }

    /**
     * Adiciona un listener que se ejecuta cuando cambia la selección.
     */
    public void setOnSelectionChange(Runnable listener) {
        this.onSelectionChange = listener;
    }

    @Override
    public void setSelectedIndex(int index) {
        if (index == -1) {
            selectedIndices.clear();
        } else if (!selectedIndices.contains(index)) {
            selectedIndices.add(index);
        } else {
            selectedIndices.remove(index);
        }
        updateDisplay();
        if (onSelectionChange != null) {
            onSelectionChange.run();
        }
    }

    /**
     * Obtiene los índices seleccionados.
     */
    public Set<Integer> getSelectedIndices() {
        return new LinkedHashSet<>(selectedIndices);
    }

    /**
     * Obtiene los valores seleccionados.
     */
    public List<E> getSelectedItems() {
        List<E> selected = new ArrayList<>();
        for (int index : selectedIndices) {
            selected.add(model.getElementAt(index));
        }
        return selected;
    }

    /**
     * Establece los valores seleccionados.
     */
    public void setSelectedItems(Collection<E> items) {
        selectedIndices.clear();
        if (items != null) {
            for (E item : items) {
                for (int i = 0; i < model.getSize(); i++) {
                    if (model.getElementAt(i).equals(item)) {
                        selectedIndices.add(i);
                        break;
                    }
                }
            }
        }
        updateDisplay();
    }

    /**
     * Limpia la selección.
     */
    public void clearSelection() {
        selectedIndices.clear();
        updateDisplay();
    }

    /**
     * Establece el formato de visualización del botón. Por defecto es "%d / %d" (ej: 2 / 5).
     */
    public void setDisplayFormat(String format) {
        this.displayFormat = format;
        updateDisplay();
    }

    /**
     * Actualiza el texto mostrado en el botón.
     */
    private void updateDisplay() {
        int selected = selectedIndices.size();
        int total = model.getSize();

        if (selected == 0 || selected == total) {
            displayText = "Todos";
            setToolTipText("Todos");
        } else if (selected == 1) {
            int idx = selectedIndices.iterator().next();
            E item = model.getElementAt(idx);
            displayText = item != null ? item.toString() : "";
            setToolTipText(displayText);
        } else {
            displayText = "Varios";
            setToolTipText(selected + " de " + total + " seleccionados");
        }
        super.setSelectedIndex(total > 0 ? 0 : -1);
        repaint();
    }

    /**
     * Obtiene la cantidad de elementos seleccionados.
     */
    public int getSelectedCount() {
        return selectedIndices.size();
    }

    /**
     * Verifica si un índice está seleccionado.
     */
    public boolean isIndexSelected(int index) {
        return selectedIndices.contains(index);
    }

    /**
     * Selecciona todos los elementos.
     */
    public void selectAll() {
        selectedIndices.clear();
        for (int i = 0; i < model.getSize(); i++) {
            selectedIndices.add(i);
        }
        updateDisplay();
        if (onSelectionChange != null) {
            onSelectionChange.run();
        }
    }

    /**
     * Deselecciona todos los elementos.
     */
    public void deselectAll() {
        selectedIndices.clear();
        updateDisplay();
        if (onSelectionChange != null) {
            onSelectionChange.run();
        }
    }
}
