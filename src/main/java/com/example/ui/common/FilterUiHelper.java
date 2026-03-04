package com.example.ui.common;

import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Utilidad para vincular componentes de filtro (JTextField, JDateChooser, JComboBox)
 * a acciones de cambio.
 */
public final class FilterUiHelper {

    private FilterUiHelper() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

    /**
     * Vincula cambios de texto en múltiples campos a una acción.
     */
    public static void bindOnTextChange(Runnable onChange, JTextField... fields) {
        if (onChange == null || fields == null) {
            return;
        }

        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.run();
            }
        };

        for (JTextField field : fields) {
            if (field != null) {
                field.getDocument().addDocumentListener(listener);
            }
        }
    }

    /**
     * Vincula cambios de fecha en múltiples JDateChooser a una acción.
     */
    public static void bindOnDateChange(Runnable onChange, JDateChooser... dateChoosers) {
        if (onChange == null || dateChoosers == null) {
            return;
        }

        PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("date".equals(evt.getPropertyName())) {
                    onChange.run();
                }
            }
        };

        for (JDateChooser chooser : dateChoosers) {
            if (chooser != null) {
                chooser.addPropertyChangeListener(listener);
            }
        }
    }

    /**
     * Vincula cambios de selección en múltiples JComboBox a una acción.
     */
    public static void bindOnComboChange(Runnable onChange, JComboBox<?>... combos) {
        if (onChange == null || combos == null) {
            return;
        }

        for (JComboBox<?> combo : combos) {
            if (combo != null) {
                combo.addActionListener(e -> onChange.run());
            }
        }
    }
}
