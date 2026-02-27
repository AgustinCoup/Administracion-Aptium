package com.example.ui.common;


import com.example.ui.common.Estilos;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

import com.example.common.constants.Constantes;

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

    // Guardados para poder reconstruir la acción del botón con un guard
    private CardLayout navegador;
    private JPanel contenedor;
    private String pantallaDestino;
    
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

        this.navegador = navegador;
        this.contenedor = contenedor;
        this.pantallaDestino = pantallaDestino;
        
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
     * Reemplaza la acción del botón Volver con una versión que verifica si hay
     * cambios pendientes antes de navegar.
     *
     * Si {@code hayPendientes.get()} retorna {@code true}, muestra un diálogo de
     * confirmación con {@code mensajeBloqueo}. El usuario puede elegir:
     *   - Sí  → navega de todas formas (abandona los cambios)
     *   - No  → cancela la navegación y permanece en la pantalla actual
     *
     * Si no hay pendientes, navega directamente sin mostrar diálogo.
     *
     * Solo debe llamarse en headers construidos con el constructor de 4 parámetros
     * (los que tienen navegador). Si el header no tiene navegador configurado,
     * el método no hace nada.
     *
     * @param hayPendientes  Supplier que retorna true cuando hay cambios sin confirmar
     * @param mensajeBloqueo Mensaje que verá el usuario en el diálogo de confirmación
     */
    public void setGuardNavegacion(Supplier<Boolean> hayPendientes, String mensajeBloqueo) {
        if (navegador == null || contenedor == null || pantallaDestino == null) {
            return;
        }

        // Remover todos los listeners actuales del botón
        for (ActionListener al : btnVolver.getActionListeners()) {
            btnVolver.removeActionListener(al);
        }

        // Agregar listener con guard
        btnVolver.addActionListener(e -> {
            if (hayPendientes.get()) {
                int respuesta = JOptionPane.showConfirmDialog(
                    this,
                    mensajeBloqueo,
                    "Cambios sin confirmar",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (respuesta != JOptionPane.YES_OPTION) {
                    return;  // El usuario eligió quedarse
                }
            }
            navegador.show(contenedor, pantallaDestino);
        });
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