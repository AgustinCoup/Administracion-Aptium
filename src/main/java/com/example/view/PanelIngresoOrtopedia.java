package com.example.view;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

public class PanelIngresoOrtopedia extends JPanel {
    // Campos para manejar materiales dinámicos y estilos
    private List<MaterialRow> materialRows = new ArrayList<>();
    private JLabel lblTotalLitros;
    private Font inputFont;
    private int inputHeight;
    private JPanel listaMaterialesPanel;
    
    // Campos del formulario
    private JTextField txtCliente;
    private JTextField txtProfesional;
    private JTextField txtPaciente;
    private JTextField txtInstitucion;
    
    // Botones
    private JButton btnGuardar;
    private JButton btnCancelar;
    
    // Referencias para navegación
    private CardLayout navegador;
    private JPanel contenedor;
    
    public PanelIngresoOrtopedia(CardLayout navegador, JPanel contenedor) {
        this.navegador = navegador;
        this.contenedor = contenedor;
        //INICIALIZACIÓN DE LA VENTANA
        setLayout(new BorderLayout());

        JPanel panelNorte = new JPanel();
        panelNorte.setLayout(new BoxLayout(panelNorte, BoxLayout.Y_AXIS));;

        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        JLabel titulo = new JLabel("INGRESO ORTOPEDIA");
        titulo.setFont(new Font("Arial", Font.BOLD, 26));
        panelTitulo.add(titulo);

        JButton btnVolver = new JButton("<- Volver");
        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panelBotonVolver.add(btnVolver);
        
        panelNorte.add(panelBotonVolver);
        panelNorte.add(panelTitulo);

        add(panelNorte, BorderLayout.NORTH);

        // Formulario con tamaños naturales y fuentes más grandes
        Font labelFont = new Font("Arial", Font.BOLD, 18);
        this.inputFont = new Font("Arial", Font.PLAIN, 18);

        //ACÁ ARRANCA EL FORMULARIO HASTA EL FINAL
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));

        //Donde se mete el texto
        txtCliente = new JTextField();
        txtProfesional = new JTextField();
        txtPaciente = new JTextField();
        txtInstitucion = new JTextField();

        // Configuración común para inputs: fuente, margen y altura preferida
        this.inputHeight = formulario.getFontMetrics(this.inputFont).getHeight() + 6;
        for (JTextField tf : new JTextField[]{txtCliente, txtProfesional, txtPaciente, txtInstitucion}) {
            tf.setFont(this.inputFont);
            tf.setColumns(20);
            tf.setMargin(new Insets(2, 6, 2, 6));
            tf.setPreferredSize(new Dimension(0, this.inputHeight));
        }

        // Panel de botones Guardar / Cancelar
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnGuardar = new JButton("Guardar");
        btnGuardar.setFont(new Font("Arial", Font.PLAIN, 18));
        btnCancelar = new JButton("Cancelar");
        btnCancelar.setFont(new Font("Arial", Font.PLAIN, 18));

        botones.add(btnGuardar);
        botones.add(btnCancelar);

        add(botones, BorderLayout.SOUTH);

        // Solo el botón cancelar tiene acción por defecto, el guardar será manejado por el controlador
        btnCancelar.addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, "MENU_PRINCIPAL");
        });

        // Construcción del formulario con GridBagLayout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        //CLIENTE
        JLabel lblCliente = new JLabel("Cliente / Empresa:");
        lblCliente.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        formulario.add(lblCliente, gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1;
        formulario.add(txtCliente, gbc);

        //PROFESIONAL
        JLabel lblProfesional = new JLabel("Profesional a cargo:");
        lblProfesional.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        formulario.add(lblProfesional, gbc);
        
        JPanel panelProfesional = new JPanel();
        panelProfesional.setLayout(new BoxLayout(panelProfesional, BoxLayout.Y_AXIS));
        panelProfesional.add(txtProfesional);
        
        JLabel lblAyudaProf = new JLabel("Formato: Apellido Nombre");
        lblAyudaProf.setFont(new Font("Arial", Font.ITALIC, 10));
        lblAyudaProf.setForeground(Color.GRAY);
        lblAyudaProf.setAlignmentX(Component.LEFT_ALIGNMENT);
        panelProfesional.add(lblAyudaProf);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1;
        formulario.add(panelProfesional, gbc);

        //PACIENTE
        JLabel lblPaciente = new JLabel("Nombre del Paciente:");
        lblPaciente.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        formulario.add(lblPaciente, gbc);
        
        JPanel panelPaciente = new JPanel();
        panelPaciente.setLayout(new BoxLayout(panelPaciente, BoxLayout.Y_AXIS));
        panelPaciente.add(txtPaciente);
        
        JLabel lblAyudaPaciente = new JLabel("Formato: Apellido Nombre");
        lblAyudaPaciente.setFont(new Font("Arial", Font.ITALIC, 10));
        lblAyudaPaciente.setForeground(Color.GRAY);
        lblAyudaPaciente.setAlignmentX(Component.LEFT_ALIGNMENT);
        panelPaciente.add(lblAyudaPaciente);
        
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1;
        formulario.add(panelPaciente, gbc);

        //INSTITUCIÓN
        JLabel lblInstitucion = new JLabel("Institución:");
        lblInstitucion.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        formulario.add(lblInstitucion, gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1;
        formulario.add(txtInstitucion, gbc);

        JLabel lblDescripcion = new JLabel("Descripción del Material:");
        lblDescripcion.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        formulario.add(lblDescripcion, gbc);

        // Panel contenedor de materiales
        JPanel panelDescripcion = new JPanel(new BorderLayout(5, 5));

        // Botones para agregar y eliminar filas de material
        JButton btnAgregarMaterial = new JButton("+");
        btnAgregarMaterial.setFont(new Font("Arial", Font.BOLD, 18));
        JButton btnEliminarMaterial = new JButton("-");
        btnEliminarMaterial.setFont(new Font("Arial", Font.BOLD, 18));
        JPanel panelAgregar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelAgregar.add(btnEliminarMaterial);
        panelAgregar.add(btnAgregarMaterial);
        panelDescripcion.add(panelAgregar, BorderLayout.NORTH);

        // Lista de materiales (cada fila: descripción + litros)
        listaMaterialesPanel = new JPanel(new GridBagLayout());
        panelDescripcion.add(listaMaterialesPanel, BorderLayout.CENTER);

        // Total de litros
        lblTotalLitros = new JLabel("Total litros: 0");
        lblTotalLitros.setFont(new Font("Arial", Font.BOLD, 16));
        JPanel panelTotal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        panelTotal.add(lblTotalLitros);
        panelDescripcion.add(panelTotal, BorderLayout.SOUTH);

        // Agregar primera fila por defecto
        agregarFilaMaterial(listaMaterialesPanel);

        // Acción para agregar nuevas filas
        btnAgregarMaterial.addActionListener(e -> {
            agregarFilaMaterial(listaMaterialesPanel);
            listaMaterialesPanel.revalidate();
            listaMaterialesPanel.repaint();
        });

        // Acción para eliminar la última fila
        btnEliminarMaterial.addActionListener(e -> {
            eliminarUltimaFilaMaterial(listaMaterialesPanel);
            listaMaterialesPanel.revalidate();
            listaMaterialesPanel.repaint();
        });

        // Ubicar el panel de descripción en la columna de inputs
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1;
        formulario.add(panelDescripcion, gbc);

        add(formulario, BorderLayout.CENTER);

        btnVolver.addActionListener(e -> navegador.show(contenedor, "ESTERILIZACION"));
    }

    // Crea y agrega una nueva fila de material (número + descripcion + litros)
    private void agregarFilaMaterial(JPanel listaMateriales) {
        GridBagConstraints gbcRow = new GridBagConstraints();
        gbcRow.insets = new Insets(5, 0, 5, 10);
        gbcRow.fill = GridBagConstraints.HORIZONTAL;

        int rowIndex = materialRows.size();

        // Campo de número (identificador del material)
        JTextField txtNumero = new JTextField();
        soloNumeros(txtNumero);
        txtNumero.setFont(this.inputFont);
        txtNumero.setColumns(5);
        txtNumero.setMargin(new Insets(2, 6, 2, 6));
        // Calcular ancho basado en caracteres + márgenes para asegurar visibilidad
        int numeroWidth = txtNumero.getFontMetrics(this.inputFont).charWidth('0') * 5 + 12;
        txtNumero.setPreferredSize(new Dimension(numeroWidth, this.inputHeight));
        txtNumero.setMinimumSize(new Dimension(numeroWidth, this.inputHeight));

        // Campo de descripción (no editable)
        JTextField txtDesc = new JTextField();
        txtDesc.setFont(this.inputFont);
        txtDesc.setColumns(20);
        txtDesc.setMargin(new Insets(2, 6, 2, 6));
        txtDesc.setPreferredSize(new Dimension(0, this.inputHeight));
        txtDesc.setEditable(false);

        // Campo de litros con JSpinner para evitar problemas de formato
        SpinnerNumberModel model = new SpinnerNumberModel(0.0, 0.0, null, 1.0);
        JSpinner spLitros = new JSpinner(model);
        spLitros.setFont(this.inputFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spLitros, "#0.##");
        spLitros.setEditor(editor);
        // Asegurar ancho visible: fija columnas del campo interno y tamaños mínimos/preferidos
        editor.getTextField().setColumns(4);

        // Escuchar cambios para actualizar el total
        spLitros.addChangeListener(e -> actualizarTotalLitros());

        // Agregar componentes a la fila en el panel de lista
        gbcRow.gridx = 0; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE;
        gbcRow.anchor = GridBagConstraints.WEST;
        listaMateriales.add(txtNumero, gbcRow);

        gbcRow.gridx = 1; gbcRow.gridy = rowIndex; gbcRow.weightx = 1;
        gbcRow.fill = GridBagConstraints.HORIZONTAL;
        listaMateriales.add(txtDesc, gbcRow);

        gbcRow.gridx = 2; gbcRow.gridy = rowIndex; gbcRow.weightx = 0;
        gbcRow.fill = GridBagConstraints.NONE;
        gbcRow.anchor = GridBagConstraints.WEST;
        listaMateriales.add(spLitros, gbcRow);

        // Guardar referencia de la fila
        materialRows.add(new MaterialRow(txtNumero, txtDesc, spLitros));

        // Recalcular total
        actualizarTotalLitros();
    }

    // Suma los litros de todas las filas y actualiza el label
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

    // Elimina la última fila de material (si existe) y actualiza el total
    private void eliminarUltimaFilaMaterial(JPanel listaMateriales) {
        if (materialRows.isEmpty()) {
            return;
        }
        MaterialRow last = materialRows.remove(materialRows.size() - 1);
        // Remover todos los componentes de la fila: número, descripción y litros
        listaMateriales.remove(last.numero);
        listaMateriales.remove(last.descripcion);
        listaMateriales.remove(last.litros);
        actualizarTotalLitros();
        // Asegurar que el panel se revalide y repinte tras la eliminación
    }

    // Clase interna para mantener referencias a los componentes de cada material
    private static class MaterialRow {
        final JTextField numero;
        final JTextField descripcion;
        final JSpinner litros;

        MaterialRow(JTextField numero, JTextField descripcion, JSpinner litros) {
            this.numero = numero;
            this.descripcion = descripcion;
            this.litros = litros;
        }
    }

    // Método para restringir un JTextField a solo números
    private void soloNumeros(JTextField campo) {
        campo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE) {
                    e.consume(); // Ignora la tecla si no es número
                }
            }
        });
    }

    // Getters para los campos del formulario
    public JTextField getTxtCliente() {
        return txtCliente;
    }

    public JTextField getTxtProfesional() {
        return txtProfesional;
    }

    public JTextField getTxtPaciente() {
        return txtPaciente;
    }

    public JTextField getTxtInstitucion() {
        return txtInstitucion;
    }

    public List<MaterialRow> getMaterialRows() {
        return materialRows;
    }
    
    public JButton getBtnGuardar() {
        return btnGuardar;
    }
    
    public JButton getBtnCancelar() {
        return btnCancelar;
    }
    
    // Método para limpiar todos los campos del formulario
    public void limpiarFormulario() {
        // Limpiar campos de texto
        txtCliente.setText("");
        txtProfesional.setText("");
        txtPaciente.setText("");
        txtInstitucion.setText("");
        
        // Limpiar materiales: eliminar todas las filas
        while (!materialRows.isEmpty()) {
            MaterialRow last = materialRows.remove(materialRows.size() - 1);
            listaMaterialesPanel.remove(last.numero);
            listaMaterialesPanel.remove(last.descripcion);
            listaMaterialesPanel.remove(last.litros);
        }
        
        // Agregar una fila vacía por defecto
        agregarFilaMaterial(listaMaterialesPanel);
        
        // Actualizar UI
        listaMaterialesPanel.revalidate();
        listaMaterialesPanel.repaint();
        actualizarTotalLitros();
    }
}
