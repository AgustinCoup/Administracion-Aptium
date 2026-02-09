package com.example.view.dialogs;

import com.example.model.Autocompletable;
import com.example.constants.Constantes;
import javax.swing.*;
import java.awt.*;

/**
 * Diálogo genérico para agregar una nueva autocompletable (Cliente, Profesional, Institución).
 * 
 * Evita duplicación de código entre múltiples diálogos específicos.
 * 
 * @param <T> Tipo de autocompletable a crear (debe implementar Autocompletable)
 */
public class AgregarAutocompletableDialog<T extends Autocompletable> extends JDialog {
    
    private JTextField txtNombre;
    private JButton btnAgregar;
    private JButton btnCancelar;
    private T entidadResultado;
    private final String nombreEntidad;
    private final java.util.function.Supplier<T> creador;
    
    /**
     * Constructor del diálogo genérico.
     * 
     * @param parent Ventana parente
     * @param nombrePropuesto Nombre sugerido
     * @param nombreEntidad Nombre de la entidad (ej: "Cliente", "Profesional")
     * @param creador Función que crea una nueva instancia de T
     */
    public AgregarAutocompletableDialog(Frame parent, String nombrePropuesto, String nombreEntidad, 
                                 java.util.function.Supplier<T> creador) {
        super(parent, String.format(Constantes.Textos.DIALOG_TITULO_AGREGAR, nombreEntidad), true);
        this.nombreEntidad = nombreEntidad;
        this.creador = creador;
        this.entidadResultado = null;
        
        inicializarComponentes(nombrePropuesto);
        configurarLayout();
        configurarEventos();
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
    }
    
    private void inicializarComponentes(String nombrePropuesto) {
        txtNombre = new JTextField(nombrePropuesto, 30);
        btnAgregar = new JButton(Constantes.Botones.AGREGAR_TEXTO);
        btnCancelar = new JButton(Constantes.Botones.CANCELAR);
    }
    
    private void configurarLayout() {
        JPanel panelPrincipal = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // Etiqueta
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        String mensaje = String.format(Constantes.Textos.DIALOG_MENSAJE_AGREGAR, nombreEntidad.toLowerCase());
        JLabel lblMensaje = new JLabel(mensaje);
        panelPrincipal.add(lblMensaje, gbc);
        
        // Campo de nombre
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        JLabel lblNombre = new JLabel(Constantes.Textos.LABEL_NOMBRE);
        panelPrincipal.add(lblNombre, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panelPrincipal.add(txtNombre, gbc);
        
        // Botones
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panelPrincipal.add(btnAgregar, gbc);
        
        gbc.gridx = 1;
        panelPrincipal.add(btnCancelar, gbc);
        
        add(panelPrincipal);
    }
    
    private void configurarEventos() {
        btnAgregar.addActionListener(e -> confirmarAgregar());
        btnCancelar.addActionListener(e -> cancelar());
        
        // Enter en el campo de texto = agregar
        txtNombre.addActionListener(e -> confirmarAgregar());
    }
    
    private void confirmarAgregar() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, Constantes.Mensajes.NOMBRE_VACIO,
                Constantes.Mensajes.TITULO_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        entidadResultado = creador.get();
        entidadResultado.setId(0);
        entidadResultado.setNombre(nombre);
        dispose();
    }
    
    private void cancelar() {
        entidadResultado = null;
        dispose();
    }
    
    /**
     * Retorna la autocompletable creada, o null si el usuario canceló.
     */
    public T obtenerEntidad() {
        return entidadResultado;
    }
}
