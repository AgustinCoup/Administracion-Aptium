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
    private JLabel lblTotalLitros;
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

        // Lista de materiales (cada fila: número + descripción + litros)
        listaMaterialesPanel = new JPanel(new GridBagLayout());
        add(listaMaterialesPanel, BorderLayout.CENTER);

        // Total de litros
        lblTotalLitros = new JLabel("Total litros: 0");
        lblTotalLitros.setFont(new Font("Arial", Font.BOLD, 16));
        JPanel panelTotal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        panelTotal.add(lblTotalLitros);
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
     * Crea y agrega una nueva fila de material (número + descripción + litros)
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
        txtDesc.setColumns(20);
        txtDesc.setMargin(Estilos.Espaciados.INSETS_INPUT);
        txtDesc.setPreferredSize(new Dimension(0, this.inputHeight));
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

        // Campo de litros con JSpinner
        SpinnerNumberModel model = new SpinnerNumberModel(0.0, 0.0, null, 1.0);
        JSpinner spLitros = new JSpinner(model);
        spLitros.setFont(this.inputFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spLitros, "#0.##");
        spLitros.setEditor(editor);
        editor.getTextField().setColumns(4);

        // Escuchar cambios para actualizar el total
        spLitros.addChangeListener(e -> actualizarTotalLitros());

        // Agregar componentes a la fila
        gbcRow.gridx = 0; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE;
        gbcRow.anchor = GridBagConstraints.WEST;
        listaMaterialesPanel.add(txtNumero, gbcRow);

        gbcRow.gridx = 1; gbcRow.gridy = rowIndex; gbcRow.weightx = 1;
        gbcRow.fill = GridBagConstraints.HORIZONTAL;
        listaMaterialesPanel.add(txtDesc, gbcRow);

        gbcRow.gridx = 2; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE;
        gbcRow.anchor = GridBagConstraints.WEST;
        listaMaterialesPanel.add(spLitros, gbcRow);

        materialRows.add(new MaterialRow(txtNumero, txtDesc, spLitros));
        actualizarTotalLitros();
    }

    /**
     * Suma los litros de todas las filas y actualiza el label
     */
    private void actualizarTotalLitros() {
        double total = 0.0;
        for (MaterialRow row : materialRows) {
            Object v = row.litros.getValue();
            if (v instanceof Number) {
                total += ((Number) v).doubleValue();
            }
        }
        lblTotalLitros.setText(String.format("Total litros: %.2f", total));
    }

    /**
     * Elimina la última fila de material (si existe)
     */
    private void eliminarUltimaFilaMaterial() {
        if (materialRows.isEmpty()) {
            return;
        }
        MaterialRow last = materialRows.remove(materialRows.size() - 1);
        listaMaterialesPanel.remove(last.numero);
        listaMaterialesPanel.remove(last.descripcion);
        listaMaterialesPanel.remove(last.litros);
        actualizarTotalLitros();
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
            listaMaterialesPanel.remove(last.litros);
        }
        agregarFilaMaterial();
        listaMaterialesPanel.revalidate();
        listaMaterialesPanel.repaint();
        actualizarTotalLitros();
    }

    // ==================== GETTERS ====================

    public List<MaterialRow> getMaterialRows() {
        return materialRows;
    }

    // Clase pública para acceder a las filas desde el controlador
    public static class MaterialRow {
        public final JTextField numero;
        public final JTextField descripcion;
        public final JSpinner litros;

        public MaterialRow(JTextField numero, JTextField descripcion, JSpinner litros) {
            this.numero = numero;
            this.descripcion = descripcion;
            this.litros = litros;
        }
    }
}
