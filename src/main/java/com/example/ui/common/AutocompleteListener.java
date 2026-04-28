package com.example.ui.common;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Componente visual de autocompletado genérico para campos de texto de búsqueda.
 *
 * Implementa DocumentListener para capturar cambios de texto en tiempo real.
 * Características:
 * - Muestra popup con sugerencias mientras el usuario escribe
 * - Requiere mínimo 3 caracteres para activar la búsqueda
 * - Navegación con teclado: flechas arriba/abajo, Enter para seleccionar, Escape para cerrar
 * - Navegación con mouse: click simple para seleccionar
 * - Lista scrollable vertical con altura dinámica
 * - Máximo 10 filas visibles antes de activar scroll
 *
 * Patrón: Respeta arquitectura MVC. La Vista notifica cambios,
 * el Controller consulta el Modelo y actualiza la Vista.
 *
 * @param <T> Tipo de entidad manejada por el autocompletado (Cliente, Profesional, etc.)
 */
public class AutocompleteListener<T> implements DocumentListener {

    private final JTextField textField;
    private final Function<String, List<T>> searchFunction;
    private final Consumer<T>               onItemSelected;
    private final Consumer<String>          onNoMatch;
    private final int                       minChars;

    private final PopupManager popup;

    private T       selectedItem;
    private boolean mouseSelecting;

    public AutocompleteListener(
            JTextField textField,
            Function<String, List<T>> searchFunction,
            Consumer<T> onItemSelected,
            Consumer<String> onNoMatch,
            int minChars) {

        this.textField      = textField;
        this.searchFunction = searchFunction;
        this.onItemSelected = onItemSelected;
        this.onNoMatch      = onNoMatch;
        this.minChars       = minChars;
        this.popup          = new PopupManager();

        configurarListeners();
    }

    public AutocompleteListener(
            JTextField textField,
            Function<String, List<T>> searchFunction,
            Consumer<T> onItemSelected,
            Consumer<String> onNoMatch) {
        this(textField, searchFunction, onItemSelected, onNoMatch, 3);
    }

    /** Constructor sin callback onNoMatch (compatibilidad hacia atrás). */
    public AutocompleteListener(
            JTextField textField,
            Function<String, List<T>> searchFunction,
            Consumer<T> onItemSelected) {
        this(textField, searchFunction, onItemSelected, null, 3);
    }

    // ── Configuración de listeners ────────────────────────────────────────────

    private void configurarListeners() {

        popup.suggestionList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { mouseSelecting = true; }
            @Override public void mouseReleased(MouseEvent e) { mouseSelecting = false; }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) seleccionarItemActual();
                mouseSelecting = false;
            }
        });

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (mouseSelecting) return;
                String texto = textField.getText().trim();
                if (texto.isEmpty()) return;
                if (selectedItem != null && selectedItem.toString().equals(texto)) return;
                if (onNoMatch != null && texto.length() >= minChars) {
                    popup.hide();
                    onNoMatch.accept(texto);
                }
            }
        });

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popup.isVisible()) return;
                int selected = popup.getSelectedIndex();
                int size     = popup.getSize();
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        if (size > 0) popup.setSelectedIndex((selected + 1) % size);
                        e.consume(); break;
                    case KeyEvent.VK_UP:
                        if (size > 0) popup.setSelectedIndex((selected - 1 + size) % size);
                        e.consume(); break;
                    case KeyEvent.VK_ENTER:
                        if (selected >= 0) { seleccionarItemActual(); e.consume(); }
                        break;
                    case KeyEvent.VK_ESCAPE:
                        popup.hide(); e.consume(); break;
                }
            }
        });
    }

    private void seleccionarItemActual() {
        T item = popup.getSelectedValue();
        if (item != null) {
            selectedItem = item;
            textField.setText(item.toString());
            popup.hide();
            if (onItemSelected != null) onItemSelected.accept(item);
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public T getSelectedItem()  { return selectedItem; }
    public void resetSelection() { selectedItem = null; }

    /** Refresca las sugerencias con el texto actual del campo. */
    public void refrescarBusqueda() { popup.actualizar(textField.getText().trim()); }

    // ── DocumentListener ──────────────────────────────────────────────────────

    @Override public void insertUpdate(DocumentEvent e)  { popup.actualizar(textField.getText().trim()); }
    @Override public void removeUpdate(DocumentEvent e)  { popup.actualizar(textField.getText().trim()); }
    @Override public void changedUpdate(DocumentEvent e) { /* no aplica a JTextField simple */ }

    // ── PopupManager (inner class) ────────────────────────────────────────────

    /**
     * Gestiona el ciclo de vida del JPopupMenu de sugerencias:
     * creación, actualización de contenido, dimensionado dinámico y visibilidad.
     *
     * Separado de DocumentListener para mantener cada responsabilidad cohesiva.
     */
    private class PopupManager {

        private static final int MAX_VISIBLE_ROWS = 10;

        final JList<T>            suggestionList;
        final DefaultListModel<T> listModel;
        private final JPopupMenu  popupMenu;
        private JScrollPane       scrollPane;

        PopupManager() {
            listModel      = new DefaultListModel<>();
            suggestionList = new JList<>(listModel);
            suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            popupMenu  = new JPopupMenu();
            scrollPane = new JScrollPane(suggestionList);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setPreferredSize(new Dimension(textField.getPreferredSize().width, 0));
            popupMenu.add(scrollPane);
            popupMenu.setFocusable(false);
        }

        void actualizar(String texto) {
            if (texto.length() < minChars) { popupMenu.setVisible(false); return; }

            List<T> resultados = searchFunction.apply(texto);
            listModel.clear();

            if (resultados != null && !resultados.isEmpty()) {
                for (T item : resultados) listModel.addElement(item);
                suggestionList.setSelectedIndex(0);

                int visibleRows = Math.min(listModel.getSize(), MAX_VISIBLE_ROWS);
                scrollPane.setPreferredSize(new Dimension(
                        textField.getPreferredSize().width,
                        visibleRows * cellHeight() + 2));
                scrollPane.revalidate();

                if (!popupMenu.isVisible()) popupMenu.show(textField, 0, textField.getHeight());
                else                        popupMenu.pack();
            } else {
                popupMenu.setVisible(false);
            }
        }

        void    hide()             { popupMenu.setVisible(false); }
        boolean isVisible()        { return popupMenu.isVisible(); }
        T       getSelectedValue() { return suggestionList.getSelectedValue(); }
        int     getSelectedIndex() { return suggestionList.getSelectedIndex(); }
        int     getSize()          { return listModel.getSize(); }

        void setSelectedIndex(int i) {
            suggestionList.setSelectedIndex(i);
            suggestionList.ensureIndexIsVisible(i);
        }

        private int cellHeight() {
            if (listModel.getSize() > 0) {
                Component r = suggestionList.getCellRenderer()
                        .getListCellRendererComponent(suggestionList, listModel.get(0), 0, false, false);
                int h = r.getPreferredSize().height;
                if (h > 0) return h;
            }
            return 22;
        }
    }
}
