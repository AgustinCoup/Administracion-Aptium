package com.example.view;

import java.awt.*;
import java.awt.event.*;
import java.util.List;

import javax.swing.*;

import com.example.constants.Constantes;
import com.example.util.Validador;

/**
 * Pantalla para ingreso de ortopedia.
 * Responsabilidad: Gestionar el formulario de datos básicos (cliente, profesional, paciente).
 * 
 * Delegación: PanelMateriales gestiona la tabla dinámica de materiales.
 * Esta pantalla fue refactorizada para respetar el SRP (Single Responsibility Principle).
 */
public class PantallaIngresoOrtopedia extends JPanel {
    
    // UI Components
    private JTextField txtCliente;
    private JTextField txtProfesional;
    private JTextField txtPaciente;
    private JTextField txtInstitucion;
    
    private JButton btnGuardar;
    private JButton btnCancelar;
    
    // Panel especializado en materiales
    private PanelMateriales panelMateriales;
    
    // Referencias para navegación
    private CardLayout navegador;
    private JPanel contenedor;

    public PantallaIngresoOrtopedia(CardLayout navegador, JPanel contenedor) {
        this.navegador = navegador;
        this.contenedor = contenedor;
        
        setLayout(new BorderLayout());

        // Header reutilizable
        PanelHeader header = new PanelHeader(
            Constantes.Titulos.INGRESO_ORTOPEDIA, 
            navegador, 
            contenedor, 
            Constantes.Pantallas.ESTERILIZACION
        );
        add(header, BorderLayout.NORTH);

        // Formulario principal
        JPanel formulario = construirFormulario();
        add(formulario, BorderLayout.CENTER);

        // Botones de acción
        add(construirPanelBotones(), BorderLayout.SOUTH);
    }

    /**
     * Construye el formulario con datos básicos del ingreso.
     */
    private JPanel construirFormulario() {
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(Estilos.Espaciados.BORDE_FORMULARIO);

        Font labelFont = Estilos.Fuentes.LABEL;
        Font inputFont = Estilos.Fuentes.INPUT;
        int inputHeight = Estilos.Dimensiones.calcularAlturaInput();

        // Crear campos de texto
        txtCliente = crearTextField(inputFont, inputHeight);
        txtProfesional = crearTextField(inputFont, inputHeight);
        Validador.aplicarSoloLetrasYEspacios(txtProfesional);
        
        txtPaciente = crearTextField(inputFont, inputHeight);
        Validador.aplicarSoloLetrasYEspacios(txtPaciente);
        
        txtInstitucion = crearTextField(inputFont, inputHeight);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Fila 0: Cliente
        agregarFila(formulario, gbc, labelFont, 0, "Cliente / Empresa:", txtCliente, null);

        // Fila 1: Profesional con hint
        agregarFila(formulario, gbc, labelFont, 1, "Profesional a cargo:", txtProfesional, "Formato: Apellido Nombre");

        // Fila 2: Paciente con hint
        agregarFila(formulario, gbc, labelFont, 2, "Nombre del Paciente:", txtPaciente, "Formato: Apellido Nombre");

        // Fila 3: Institución
        agregarFila(formulario, gbc, labelFont, 3, "Institución:", txtInstitucion, null);

        // Fila 4: Panel de Materiales
        JLabel lblDescripcion = new JLabel("Material:");
        lblDescripcion.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        formulario.add(lblDescripcion, gbc);

        panelMateriales = new PanelMateriales(inputFont, inputHeight);
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        formulario.add(panelMateriales, gbc);

        return formulario;
    }

    /**
     * Agrega una fila al formulario con etiqueta, campo de texto y opcionalmente texto de ayuda.
     */
    private void agregarFila(JPanel panel, GridBagConstraints gbc, Font labelFont, int row, 
                            String labelText, JTextField textField, String ayuda) {
        JLabel label = new JLabel(labelText);
        label.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(label, gbc);

        JPanel panelCampo = new JPanel();
        panelCampo.setLayout(new BoxLayout(panelCampo, BoxLayout.Y_AXIS));
        panelCampo.add(textField);
        
        if (ayuda != null) {
            JLabel lblAyuda = new JLabel(ayuda);
            lblAyuda.setFont(Estilos.Fuentes.TEXTO_AYUDA);
            lblAyuda.setForeground(Estilos.Colores.TEXTO_AYUDA);
            lblAyuda.setAlignmentX(Component.LEFT_ALIGNMENT);
            panelCampo.add(lblAyuda);
        }

        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1;
        panel.add(panelCampo, gbc);
    }

    /**
     * Crea un JTextField con configuración estándar.
     */
    private JTextField crearTextField(Font font, int height) {
        JTextField tf = new JTextField();
        tf.setFont(font);
        tf.setColumns(20);
        tf.setMargin(Estilos.Espaciados.INSETS_INPUT);
        tf.setPreferredSize(new Dimension(0, height));
        return tf;
    }

    /**
     * Construye el panel inferior con botones de acción.
     */
    private JPanel construirPanelBotones() {
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        btnGuardar = new JButton(Constantes.Botones.GUARDAR);
        btnGuardar.setFont(Estilos.Fuentes.INPUT);
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
        btnCancelar.setFont(Estilos.Fuentes.INPUT);

        botones.add(btnGuardar);
        botones.add(btnCancelar);

        // Acción cancelar por defecto
        btnCancelar.addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.MENU_PRINCIPAL);
        });

        return botones;
    }

    // ==================== GETTERS ====================

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

    public List<PanelMateriales.MaterialRow> getMaterialRows() {
        return panelMateriales.getMaterialRows();
    }
    
    public PanelMateriales getPanelMateriales() {
        return panelMateriales;
    }
    
    public JButton getBtnGuardar() {
        return btnGuardar;
    }
    
    public JButton getBtnCancelar() {
        return btnCancelar;
    }

    // ==================== LIMPIAR FORMULARIO ====================

    public void limpiarFormulario() {
        txtCliente.setText("");
        txtProfesional.setText("");
        txtPaciente.setText("");
        txtInstitucion.setText("");
        panelMateriales.limpiar();
    }
}
