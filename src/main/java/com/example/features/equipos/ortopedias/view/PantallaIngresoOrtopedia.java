package com.example.features.equipos.ortopedias.view;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.*;

import com.example.common.constants.Constantes;
import com.example.common.util.Validador;
import com.example.features.equipos.common.view.PantallaIngresoBase;
import com.example.features.equipos.ortopedias.view.helpers.PanelMateriales;
import com.example.ui.common.Estilos;
import com.example.ui.common.PanelHeader;

/**
 * Pantalla para ingreso de ortopedia.
 * Responsabilidad: Gestionar el formulario de datos básicos (cliente, profesional, paciente).
 * 
 * Delegación: PanelMateriales gestiona la tabla dinámica de materiales.
 * Esta pantalla fue refactorizada para respetar el SRP (Single Responsibility Principle).
 */
public class PantallaIngresoOrtopedia extends JPanel implements PantallaIngresoBase {
    
    // UI Components
    private JTextField txtCliente;
    private JTextField txtProfesional;
    private JTextField txtPaciente;
    private JTextField txtInstitucion;
    private JCheckBox chkRequiereLavado;
    private JCheckBox chkRequiereEmpaque;
    
    private JButton btnGuardar;
    private JButton btnCancelar;
    
    // Panel especializado en materiales
    private PanelMateriales panelMateriales;
    
    // Referencias para navegación
    private CardLayout navegador;
    private JPanel contenedor;
    
    /**
     * Identificador del cliente seleccionado desde el autocompletado.
     * Valor -1 indica que no hay cliente seleccionado.
     */
    private int selectedClienteId = -1;
    
    /**
     * Identificador del profesional seleccionado desde el autocompletado.
     * Valor -1 indica que no hay profesional seleccionado.
     */
    private int selectedProfesionalId = -1;
    
    /**
     * Identificador de la institución seleccionada desde el autocompletado.
     * Valor -1 indica que no hay institución seleccionada.
     */
    private int selectedInstitucionId = -1;

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
        configurarVolverConLimpieza(header);
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
        gbc.insets = new Insets(8, 5, 8, 10); // Reducir padding horizontal
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Fila 0: Cliente
        agregarFila(formulario, gbc, labelFont, 0, Constantes.Textos.LABEL_CLIENTE, txtCliente, null);

        // Fila 1: Profesional con hint
        agregarFila(formulario, gbc, labelFont, 1, Constantes.Textos.LABEL_PROFESIONAL, txtProfesional, Constantes.Textos.AYUDA_FORMATO_APELLIDO_NOMBRE);

        // Fila 2: Paciente con hint
        agregarFila(formulario, gbc, labelFont, 2, Constantes.Textos.LABEL_PACIENTE, txtPaciente, Constantes.Textos.AYUDA_FORMATO_APELLIDO_NOMBRE);

        // Fila 3: Institución
        agregarFila(formulario, gbc, labelFont, 3, Constantes.Textos.LABEL_INSTITUCION, txtInstitucion, null);

        // Fila 4: Opciones de proceso
        JPanel panelOpciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chkRequiereLavado = new JCheckBox(Constantes.Textos.CHECK_REQUIERE_LAVADO, true);
        chkRequiereLavado.setFont(Estilos.Fuentes.INPUT);
        chkRequiereEmpaque = new JCheckBox(Constantes.Textos.CHECK_REQUIERE_EMPAQUE, true);
        chkRequiereEmpaque.setFont(Estilos.Fuentes.INPUT);
        chkRequiereEmpaque.setEnabled(false);
        panelOpciones.add(chkRequiereLavado);
        panelOpciones.add(chkRequiereEmpaque);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        gbc.gridwidth = 2; // Ocupar ambas columnas
        gbc.anchor = GridBagConstraints.WEST;
        formulario.add(panelOpciones, gbc);
        gbc.gridwidth = 1; // Resetear para las siguientes filas

        // Fila 5: Panel de Materiales
        JLabel lblDescripcion = new JLabel(Constantes.Textos.LABEL_MATERIAL);
        lblDescripcion.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        gbc.insets = new Insets(2, 5, 2, 10); // Reducir espacio vertical y horizontal
        gbc.anchor = GridBagConstraints.WEST; // Alineado por línea base del texto
        formulario.add(lblDescripcion, gbc);

        panelMateriales = new PanelMateriales(inputFont, inputHeight);
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.insets = new Insets(2, 5, 5, 5); // Reducir padding en todos lados
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
        gbc.fill = GridBagConstraints.NONE; // Labels solo ocupan lo necesario
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
        gbc.fill = GridBagConstraints.HORIZONTAL; // Campos se expanden
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

    private void configurarVolverConLimpieza(PanelHeader header) {
        for (ActionListener listener : header.getBtnVolver().getActionListeners()) {
            header.getBtnVolver().removeActionListener(listener);
        }

        header.getBtnVolver().addActionListener(e -> {
            limpiarFormulario();
            navegador.show(contenedor, Constantes.Pantallas.ESTERILIZACION);
        });
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

    public void setOnRequiereLavadoChanged(ActionListener listener) {
        chkRequiereLavado.addActionListener(listener);
    }

    public void setRequiereEmpaqueSelected(boolean selected) {
        chkRequiereEmpaque.setSelected(selected);
    }

    public void setRequiereEmpaqueEnabled(boolean enabled) {
        chkRequiereEmpaque.setEnabled(enabled);
    }

    public boolean isRequiereLavado() {
        return chkRequiereLavado.isSelected();
    }

    public boolean isRequiereEmpaque() {
        return chkRequiereEmpaque.isSelected();
    }
    
    public JButton getBtnGuardar() {
        return btnGuardar;
    }
    
    public JButton getBtnCancelar() {
        return btnCancelar;
    }

    // ==================== GESTIÓN DE CLIENTE SELECCIONADO ====================

    /**
     * Retorna el identificador del cliente seleccionado.
     * 
     * Este valor es establecido por el Controller cuando el usuario
     * selecciona un cliente desde el autocompletado.
     * 
     * @return ID del cliente, o -1 si no hay selección
     */
    public int getSelectedClienteId() {
        return selectedClienteId;
    }

    /**
     * Establece el identificador del cliente seleccionado.
     * 
     * Responsabilidad del Controller llamar a este método cuando
     * el usuario selecciona un cliente desde el autocompletado.
     * 
     * @param clienteId Identificador del cliente seleccionado
     */
    public void setSelectedClienteId(int clienteId) {
        this.selectedClienteId = clienteId;
    }
    
    /**
     * Obtiene el identificador del profesional seleccionado.
     * 
     * Este valor es establecido por el Controller cuando el usuario
     * selecciona un profesional desde el autocompletado.
     * 
     * @return ID del profesional, o -1 si no hay selección
     */
    public int getSelectedProfesionalId() {
        return selectedProfesionalId;
    }
    
    /**
     * Establece el identificador del profesional seleccionado.
     * 
     * Responsabilidad del Controller llamar a este método cuando
     * el usuario selecciona un profesional desde el autocompletado.
     * 
     * @param profesionalId Identificador del profesional seleccionado
     */
    public void setSelectedProfesionalId(int profesionalId) {
        this.selectedProfesionalId = profesionalId;
    }
    
    /**
     * Obtiene el identificador de la institución seleccionada.
     * 
     * Este valor es establecido por el Controller cuando el usuario
     * selecciona una institución desde el autocompletado.
     * 
     * @return ID de la institución, o -1 si no hay selección
     */
    public int getSelectedInstitucionId() {
        return selectedInstitucionId;
    }
    
    /**
     * Establece el identificador de la institución seleccionada.
     * 
     * Responsabilidad del Controller llamar a este método cuando
     * el usuario selecciona una institución desde el autocompletado.
     * 
     * @param institucionId Identificador de la institución seleccionada
     */
    public void setSelectedInstitucionId(int institucionId) {
        this.selectedInstitucionId = institucionId;
    }

    // ==================== OPERACIONES DE FORMULARIO ====================

    /**
     * Limpia todos los campos del formulario al estado inicial.
     * 
     * Responsabilidad del Controller llamar a este método después
     * de guardar exitosamente un equipo.
     */
    public void limpiarFormulario() {
        txtCliente.setText("");
        txtProfesional.setText("");
        txtPaciente.setText("");
        txtInstitucion.setText("");
        panelMateriales.limpiar();
        chkRequiereLavado.setSelected(true);
        chkRequiereEmpaque.setSelected(true);
        chkRequiereEmpaque.setEnabled(false);
        selectedClienteId = -1;
        selectedProfesionalId = -1;
        selectedInstitucionId = -1;
    }

    @Override
    public void mostrarAdvertencia(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, Constantes.Mensajes.TITULO_EXITO, JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, Constantes.Mensajes.TITULO_ERROR_GUARDAR, JOptionPane.ERROR_MESSAGE);
    }
}


