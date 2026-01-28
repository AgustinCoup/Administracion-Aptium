package com.example.view;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.example.constants.Constantes;
import com.example.util.Validador;

import java.util.function.BiConsumer;

/**
 * Panel especializado para gestionar materiales de forma dinámica.
 * Responsabilidad única: UI de tabla de materiales (agregar, eliminar filas, calcular totales).
 * 
 * Separado de PantallaIngresoOrtopedia para evitar que esa clase sea demasiado grande.
 */
public class PanelMateriales extends JPanel {
    
    private List<MaterialRow> materialRows = new ArrayList<>();
    private JLabel lblTotalElementos;
    private Font inputFont;
    private int inputHeight;
    private JPanel listaMaterialesPanel;
    
    // Callback para cuando se cambia el número de material
    // BiConsumer<Integer, JTextField>: recibe el código y el textfield donde actualizar la descripción
    private BiConsumer<Integer, JTextField> onNumeroChangedListener;

    public PanelMateriales(Font inputFont, int inputHeight) {
        this.inputFont = inputFont;
        this.inputHeight = inputHeight;
        
        setLayout(new BorderLayout(5, 5));

        // Botones para agregar y eliminar filas de material
        JButton btnAgregarMaterial = new JButton(Constantes.Botones.AGREGAR);
        btnAgregarMaterial.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        JButton btnEliminarMaterial = new JButton(Constantes.Botones.ELIMINAR);
        btnEliminarMaterial.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        
        JPanel panelAgregar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelAgregar.add(btnEliminarMaterial);
        panelAgregar.add(btnAgregarMaterial);
        add(panelAgregar, BorderLayout.NORTH);

        // Lista de materiales (cada fila: número + descripción + Elementos)
        listaMaterialesPanel = new JPanel(new GridBagLayout());
        
        // Envolver en JScrollPane para permitir scroll vertical ilimitado
        JScrollPane scrollPane = new JScrollPane(listaMaterialesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null); // Eliminar el marco gris
        add(scrollPane, BorderLayout.CENTER);

        // Total de Elementos
        lblTotalElementos = new JLabel("Total Elementos: 0");
        lblTotalElementos.setFont(Estilos.Fuentes.LABEL);
        JPanel panelTotal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        panelTotal.add(lblTotalElementos);
        add(panelTotal, BorderLayout.SOUTH);

        // Agregar primera fila por defecto
        agregarFilaMaterial();

        // Acciones de botones
        btnAgregarMaterial.addActionListener(e -> {
            agregarFilaMaterial();
            listaMaterialesPanel.revalidate();
            listaMaterialesPanel.repaint();
        });

        btnEliminarMaterial.addActionListener(e -> {
            eliminarUltimaFilaMaterial();
            listaMaterialesPanel.revalidate();
            listaMaterialesPanel.repaint();
        });
    }

    /**
     * Permite al Controller registrar un listener para cuando cambie el número de material.
     * Respeta MVC: la Vista notifica eventos, el Controller maneja la lógica.
     * 
     * @param listener BiConsumer que recibe (código, campoDescripción)
     */
    public void setOnNumeroChangedListener(BiConsumer<Integer, JTextField> listener) {
        this.onNumeroChangedListener = listener;
    }

    /**
     * Crea y agrega una nueva fila de material (número + descripción + Elementos + botón eliminar)
     */
    private void agregarFilaMaterial() {
        GridBagConstraints gbcRow = new GridBagConstraints();
        gbcRow.insets = new Insets(5, 0, 5, 10);
        gbcRow.fill = GridBagConstraints.HORIZONTAL;

        int rowIndex = materialRows.size();

        // Campo de número (identificador del material)
        JTextField txtNumero = new JTextField();
        Validador.aplicarSoloNumeros(txtNumero);
        txtNumero.setFont(this.inputFont);
        txtNumero.setColumns(5);
        txtNumero.setMargin(Estilos.Espaciados.INSETS_INPUT);
        int numeroWidth = Estilos.Dimensiones.calcularAnchoNumero(5);
        txtNumero.setPreferredSize(new Dimension(numeroWidth, this.inputHeight));
        txtNumero.setMinimumSize(new Dimension(numeroWidth, this.inputHeight));

        // Campo de descripción (no editable)
        JTextField txtDesc = new JTextField();
        txtDesc.setFont(this.inputFont);
        txtDesc.setColumns(40);
        txtDesc.setMargin(Estilos.Espaciados.INSETS_INPUT);
        txtDesc.setPreferredSize(new Dimension(200, this.inputHeight));
        txtDesc.setMinimumSize(new Dimension(100, this.inputHeight));
        txtDesc.setEditable(false);
        txtDesc.setBackground(Color.LIGHT_GRAY); // Indica visualmente que no es editable

        // Listener para notificar cambios al Controller (respeta MVC)
        txtNumero.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notificarCambioNumero(txtNumero, txtDesc);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notificarCambioNumero(txtNumero, txtDesc);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notificarCambioNumero(txtNumero, txtDesc);
            }
        });

        // Campo de Elementos con JSpinner
        SpinnerNumberModel model = new SpinnerNumberModel(1.0, 1.0, null, 1.0);
        JSpinner spElementos = new JSpinner(model);
        spElementos.setFont(this.inputFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spElementos, "#0.##");
        spElementos.setEditor(editor);
        editor.getTextField().setColumns(4);

        // Escuchar cambios para actualizar el total
        spElementos.addChangeListener(e -> actualizarTotalElementos());

        // Botón eliminar para esta fila específica
        JButton btnEliminarFila = new JButton("X");
        btnEliminarFila.setFont(Estilos.Fuentes.BOTON_PEQUENO);
        btnEliminarFila.setPreferredSize(new Dimension(45, this.inputHeight));
        btnEliminarFila.setMargin(new Insets(0, 0, 0, 0));
        btnEliminarFila.setToolTipText("Eliminar esta fila");
        
        MaterialRow materialRow = new MaterialRow(txtNumero, txtDesc, spElementos, btnEliminarFila);
        
        btnEliminarFila.addActionListener(e -> {
            eliminarFilaMaterial(materialRow);
        });

        // Agregar componentes a la fila
        gbcRow.gridx = 0; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE;
        gbcRow.anchor = GridBagConstraints.WEST;
        listaMaterialesPanel.add(txtNumero, gbcRow);

        gbcRow.gridx = 1; gbcRow.gridy = rowIndex; gbcRow.weightx = 1;
        gbcRow.fill = GridBagConstraints.HORIZONTAL;
        gbcRow.anchor = GridBagConstraints.WEST;
        listaMaterialesPanel.add(txtDesc, gbcRow);

        gbcRow.gridx = 2; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE;
        gbcRow.anchor = GridBagConstraints.EAST;
        listaMaterialesPanel.add(spElementos, gbcRow);

        gbcRow.gridx = 3; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE;
        gbcRow.anchor = GridBagConstraints.EAST;
        gbcRow.insets = new Insets(5, 5, 5, 0);
        listaMaterialesPanel.add(btnEliminarFila, gbcRow);

        materialRows.add(materialRow);
        actualizarTotalElementos();
    }

    /**
     * Suma los Elementos de todas las filas y actualiza el label
     */
    private void actualizarTotalElementos() {
        double total = 0.0;
        for (MaterialRow row : materialRows) {
            Object v = row.Elementos.getValue();
            if (v instanceof Number) {
                total += ((Number) v).doubleValue();
            }
        }
        lblTotalElementos.setText(String.format("Total Elementos: %d", (int) total));
    }

    /**
     * Elimina la última fila de material (si existe)
     */
    private void eliminarUltimaFilaMaterial() {
        if (materialRows.isEmpty()) {
            return;
        }
        MaterialRow last = materialRows.get(materialRows.size() - 1);
        eliminarFilaMaterial(last);
    }

    /**
     * Elimina una fila de material específica
     */
    private void eliminarFilaMaterial(MaterialRow row) {
        if (materialRows.remove(row)) {
            listaMaterialesPanel.remove(row.numero);
            listaMaterialesPanel.remove(row.descripcion);
            listaMaterialesPanel.remove(row.Elementos);
            listaMaterialesPanel.remove(row.btnEliminar);
            listaMaterialesPanel.revalidate();
            listaMaterialesPanel.repaint();
            actualizarTotalElementos();
        }
    }

    /**
     * Notifica al Controller cuando cambia el número de material.
     * La Vista solo valida y notifica, no accede a la BD (respeta MVC).
     */
    private void notificarCambioNumero(JTextField txtNumero, JTextField txtDesc) {
        String numeroStr = txtNumero.getText().trim();
        
        // Si el campo está vacío, limpiar descripción
        if (numeroStr.isEmpty()) {
            txtDesc.setText("");
            return;
        }
        
        // Verificar que sea un número válido
        if (!Validador.soloNumeros(numeroStr)) {
            txtDesc.setText("Código inválido");
            return;
        }
        
        // Notificar al Controller si hay listener registrado
        if (onNumeroChangedListener != null) {
            try {
                int codigo = Integer.parseInt(numeroStr);
                onNumeroChangedListener.accept(codigo, txtDesc);
            } catch (NumberFormatException e) {
                txtDesc.setText("Código inválido");
            }
        }
    }

    /**
     * Limpia todas las filas y agrega una vacía por defecto
     */
    public void limpiar() {
        while (!materialRows.isEmpty()) {
            MaterialRow last = materialRows.remove(materialRows.size() - 1);
            listaMaterialesPanel.remove(last.numero);
            listaMaterialesPanel.remove(last.descripcion);
            listaMaterialesPanel.remove(last.Elementos);
            listaMaterialesPanel.remove(last.btnEliminar);
        }
        agregarFilaMaterial();
        listaMaterialesPanel.revalidate();
        listaMaterialesPanel.repaint();
        actualizarTotalElementos();
    }

    // ==================== GETTERS ====================

    public List<MaterialRow> getMaterialRows() {
        return materialRows;
    }

    // Clase pública para acceder a las filas desde el controlador
    public static class MaterialRow {
        public final JTextField numero;
        public final JTextField descripcion;
        public final JSpinner Elementos;
        public final JButton btnEliminar;

        public MaterialRow(JTextField numero, JTextField descripcion, JSpinner Elementos, JButton btnEliminar) {
            this.numero = numero;
            this.descripcion = descripcion;
            this.Elementos = Elementos;
            this.btnEliminar = btnEliminar;
        }
    }
}
