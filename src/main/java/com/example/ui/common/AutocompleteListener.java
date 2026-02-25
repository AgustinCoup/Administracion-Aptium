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
    private final JPopupMenu popupMenu;
    private final JList<T> suggestionList;
    private final DefaultListModel<T> listModel;
    private JScrollPane scrollPane;
    
    /**
     * Cantidad máxima de filas antes de mostrar scroll vertical.
     */
    private static final int MAX_VISIBLE_ROWS = 10;
    
    /**
     * Función de búsqueda proporcionada por el Controller.
     * Responsable de consultar el Modelo y retornar resultados.
     */
    private final Function<String, List<T>> searchFunction;
    
    /**
     * Callback ejecutado cuando el usuario selecciona una entidad.
     * Notifica al Controller para que actualice la Vista.
     */
    private final Consumer<T> onItemSelected;
    
    /**
     * Callback opcional ejecutado cuando el usuario sale del campo sin seleccionar
     * una entidad existente (texto no coincide con ningún resultado).
     * Permite al Controller ofrecer la opción de crear una nueva entidad.
     */
    private final Consumer<String> onNoMatch;
    
    /**
     * Entidad actualmente seleccionada por el usuario.
     * Nula si no hay selección activa.
     */
    private T selectedItem;
    private boolean mouseSelecting;

    /**
     * Construye un componente de autocompletado.
     * 
     * @param textField Campo de texto donde el usuario escribe
     * @param searchFunction Función que consulta el Modelo para obtener resultados
     * @param onItemSelected Callback que notifica al Controller sobre la selección
     * @param onNoMatch Callback opcional para cuando no hay coincidencia (puede ser null)
     */
    public AutocompleteListener(
            JTextField textField, 
            Function<String, List<T>> searchFunction,
            Consumer<T> onItemSelected,
            Consumer<String> onNoMatch) {
        
        this.textField = textField;
        this.searchFunction = searchFunction;
        this.onItemSelected = onItemSelected;
        this.onNoMatch = onNoMatch;
        
        // Crear modelo de lista y JList
        this.listModel = new DefaultListModel<>();
        this.suggestionList = new JList<>(listModel);
        this.suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Crear popup menu con scroll vertical
        this.popupMenu = new JPopupMenu();
        this.scrollPane = new JScrollPane(suggestionList);
        
        // Configurar scroll para ser vertical solamente
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        // La altura se ajustará dinámicamente en actualizarSugerencias()
        scrollPane.setPreferredSize(new Dimension(textField.getPreferredSize().width, 0));
        
        popupMenu.add(scrollPane);
        popupMenu.setFocusable(false);
        
        // Configurar listeners de selección
        configurarListeners();
    }
    
    /**
     * Constructor alternativo sin callback para onNoMatch (para compatibilidad hacia atrás).
     */
    public AutocompleteListener(
            JTextField textField, 
            Function<String, List<T>> searchFunction,
            Consumer<T> onItemSelected) {
        this(textField, searchFunction, onItemSelected, null);
    }

    /**
     * Configura los listeners para interacción con teclado y mouse.
     */
    private void configurarListeners() {
        // Click del mouse en la lista
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mouseSelecting = true;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    seleccionarItemActual();
                }
                mouseSelecting = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mouseSelecting = false;
            }
        });
        
        // FocusListener: Detecta cuando el usuario sale del campo sin seleccionar
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String texto = textField.getText().trim();

                if (mouseSelecting) {
                    return;
                }
                
                // Si no hay texto, no hacer nada
                if (texto.isEmpty()) {
                    return;
                }
                
                // Si se seleccionó un item, ya se procesó en seleccionarItemActual()
                if (selectedItem != null && selectedItem.toString().equals(texto)) {
                    return;
                }
                
                // Si hay texto pero no coincide con ninguna entidad, notificar al Controller
                if (onNoMatch != null && texto.length() >= 3) {
                    popupMenu.setVisible(false);
                    onNoMatch.accept(texto);
                }
            }
        });
        
        // Navegación con teclado en el campo de texto
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popupMenu.isVisible()) {
                    return;
                }
                
                int selectedIndex = suggestionList.getSelectedIndex();
                int listSize = listModel.getSize();
                
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        // Navegar hacia el siguiente elemento en la lista
                        if (listSize > 0) {
                            int nextIndex = (selectedIndex + 1) % listSize;
                            suggestionList.setSelectedIndex(nextIndex);
                            suggestionList.ensureIndexIsVisible(nextIndex);
                        }
                        e.consume();
                        break;
                        
                    case KeyEvent.VK_UP:
                        // Navegar hacia el elemento anterior en la lista
                        if (listSize > 0) {
                            int prevIndex = (selectedIndex - 1 + listSize) % listSize;
                            suggestionList.setSelectedIndex(prevIndex);
                            suggestionList.ensureIndexIsVisible(prevIndex);
                        }
                        e.consume();
                        break;
                        
                    case KeyEvent.VK_ENTER:
                        // Confirmar selección del elemento actualmente destacado
                        if (selectedIndex >= 0) {
                            seleccionarItemActual();
                            e.consume();
                        }
                        break;
                        
                    case KeyEvent.VK_ESCAPE:
                        // Cancelar y cerrar el popup sin realizar selección
                        popupMenu.setVisible(false);
                        e.consume();
                        break;
                }
            }
        });
    }

    /**
     * Selecciona la entidad actualmente destacada en la lista.
     */
    private void seleccionarItemActual() {
        T item = suggestionList.getSelectedValue();
        if (item != null) {
            selectedItem = item;
            textField.setText(item.toString());
            popupMenu.setVisible(false);
            
            // Notificar al Controller que se seleccionó una entidad
            if (onItemSelected != null) {
                onItemSelected.accept(item);
            }
        }
    }

    /**
     * Actualiza las sugerencias en el dropdown basándose en el texto actual.
     * 
     * Calcula dinámicamente la altura del panel según la cantidad de elementos:
     * - Si hay 10 o menos elementos: altura = cantidad * altura por fila
     * - Si hay más de 10 elementos: altura = 10 * altura por fila + scroll vertical
     */
    private void actualizarSugerencias() {
        String texto = textField.getText().trim();
        
        // Requiere mínimo 3 caracteres
        if (texto.length() < 3) {
            popupMenu.setVisible(false);
            return;
        }
        
        // Buscar entidades usando la función proporcionada (Controller → Model)
        List<T> resultados = searchFunction.apply(texto);
        
        // Actualizar lista de sugerencias
        listModel.clear();
        if (resultados != null && !resultados.isEmpty()) {
            for (T item : resultados) {
                listModel.addElement(item);
            }
            
            // Seleccionar el primer item por defecto
            suggestionList.setSelectedIndex(0);
            
            // Calcular dinámicamente la altura del dropdown usando la altura REAL del renderer
            int itemCount = listModel.getSize();
            int visibleRows = Math.min(itemCount, MAX_VISIBLE_ROWS);
            
            // Obtener la altura real de una celda usando el renderer
            int cellHeight = getActualCellHeight();
            int dropdownHeight = visibleRows * cellHeight;
            
            // Agregar altura de los bordes del JPopupMenu (típicamente 2px)
            int totalHeight = dropdownHeight + 2;
            
            // Actualizar tamaño del scroll pane
            scrollPane.setPreferredSize(new Dimension(
                    textField.getPreferredSize().width,
                    totalHeight
            ));
            
            // Revalidar el scroll pane para aplicar los nuevos tamaños
            scrollPane.revalidate();
            
            // Mostrar popup debajo del campo de texto
            if (!popupMenu.isVisible()) {
                popupMenu.show(textField, 0, textField.getHeight());
            } else {
                // Si el popup ya está visible, recalcular su tamaño
                popupMenu.pack();
            }
        } else {
            popupMenu.setVisible(false);
        }
    }

    /**
     * Obtiene la altura real de una celda basándose en el renderer de la JList.
     * Esto es más preciso que usar una constante estimada.
     * 
     * @return Altura en píxeles de una celda de la lista
     */
    private int getActualCellHeight() {
        // Si hay elementos en la lista, obtener la altura real del renderer
        if (listModel.getSize() > 0) {
            Component renderer = suggestionList.getCellRenderer()
                    .getListCellRendererComponent(
                            suggestionList,
                            listModel.get(0),
                            0,
                            false,
                            false
                    );
            int height = renderer.getPreferredSize().height;
            if (height > 0) {
                return height;
            }
        }
        // Fallback: usar altura predeterminada si no se puede calcular
        return 22;
    }

    /**
     * Obtiene la entidad seleccionada actualmente.
     * 
     * @return Entidad seleccionada, o null si no hay selección
     */
    public T getSelectedItem() {
        return selectedItem;
    }

    /**
     * Resetea la selección actual.
     */
    public void resetSelection() {
        this.selectedItem = null;
    }

    /**
     * Refuerza la búsqueda basado en el texto actual del campo.
     * Útil después de agregar un nuevo item para que aparezca inmediatamente
     * en las sugerencias.
     */
    public void refrescarBusqueda() {
        actualizarSugerencias();
    }

    // ==================== IMPLEMENTACIÓN DE DocumentListener ====================

    @Override
    public void insertUpdate(DocumentEvent e) {
        // Se ejecuta cuando se inserta texto
        actualizarSugerencias();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        // Se ejecuta cuando se elimina texto
        actualizarSugerencias();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // Se ejecuta cuando cambian atributos del documento
        // No necesario para JTextField simple
    }
}


