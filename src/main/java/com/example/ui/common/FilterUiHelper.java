package com.example.ui.common;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class FilterUiHelper {

    private FilterUiHelper() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

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
}
