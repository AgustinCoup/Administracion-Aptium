package com.example.view.helpers;

import javax.swing.*;
import java.awt.*;

import com.example.constants.Constantes;

/**
 * Componente reutilizable que encapsula el header estándar de las pantallas.
 * Incluye un botón de volver (alineado a la izquierda) y un título centrado.
 * 
 * Este componente promueve la consistencia visual y facilita el mantenimiento,
 * permitiendo cambios globales de estilo desde un único lugar.
 */
public class PanelHeader extends JPanel {
    
    private JButton btnVolver;
    private JLabel lblTitulo;
    
    /**
     * Constructor que crea un header completo con navegación.
     * 
     * @param titulo El texto que se mostrará como título de la pantalla
     * @param navegador El CardLayout que maneja la navegación entre pantallas
     * @param contenedor El JPanel contenedor que usa el CardLayout
     * @param pantallaDestino El nombre de la pantalla a la que vuelve el botón (ej: "MENU_PRINCIPAL")
     */
    public PanelHeader(String titulo, CardLayout navegador, JPanel contenedor, String pantallaDestino) {
        // Configuración del panel principal
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        // Panel del botón volver (alineado a la izquierda)
        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        btnVolver = new JButton(Constantes.Botones.VOLVER);
        panelBotonVolver.add(btnVolver);
        
        // Panel del título (centrado)
        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(Estilos.Fuentes.TITULO);
        panelTitulo.add(lblTitulo);
        
        // Agregar componentes al panel principal
        add(panelBotonVolver);
        add(panelTitulo);
        
        // Configurar acción del botón volver
        btnVolver.addActionListener(e -> navegador.show(contenedor, pantallaDestino));
    }
    
    /**
     * Constructor alternativo sin navegación automática.
     * Útil cuando se necesita personalizar la acción del botón volver.
     * 
     * @param titulo El texto que se mostrará como título de la pantalla
     */
    public PanelHeader(String titulo) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        btnVolver = new JButton(Constantes.Botones.VOLVER);
        panelBotonVolver.add(btnVolver);
        
        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(new Font(Constantes.Defaults.FUENTE_PRINCIPAL, Font.BOLD, Constantes.Defaults.FUENTE_TAMANO_TITULO));
        panelTitulo.add(lblTitulo);
        
        add(panelBotonVolver);
        add(panelTitulo);
    }
    
    /**
     * Obtiene el botón de volver para personalizar su comportamiento si es necesario.
     * @return El botón de volver
     */
    public JButton getBtnVolver() {
        return btnVolver;
    }
    
    /**
     * Obtiene el JLabel del título para personalizaciones avanzadas.
     * @return El label del título
     */
    public JLabel getLblTitulo() {
        return lblTitulo;
    }
    
    /**
     * Cambia el texto del título dinámicamente.
     * @param nuevoTitulo El nuevo texto del título
     */
    public void setTitulo(String nuevoTitulo) {
        lblTitulo.setText(nuevoTitulo);
    }
}
