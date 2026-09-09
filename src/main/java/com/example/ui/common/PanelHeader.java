package com.example.ui.common;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private JButton btnVolver;
    private JLabel lblTitulo;

    // Botón "Actualizar" opt-in: oculto hasta que una pantalla llame a setAccionRefrescar.
    private JButton btnRefrescar;
    private JLabel lblActualizado;
    private GuardaRefresco guardaRefresco;

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
        this.navegador = navegador;
        this.contenedor = contenedor;
        this.pantallaDestino = pantallaDestino;
        init(titulo);
        btnVolver.addActionListener(e -> navegador.show(contenedor, pantallaDestino));
    }

    /**
     * Constructor alternativo sin navegación automática.
     * El llamador debe agregar un ActionListener a {@link #getBtnVolver()} o
     * llamar a {@link #setGuardNavegacion} para que ESC y el botón funcionen.
     *
     * @param titulo El texto que se mostrará como título de la pantalla
     */
    public PanelHeader(String titulo) {
        init(titulo);
    }

    private void init(String titulo) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel panelBotonVolver = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        btnVolver = new JButton(Constantes.Botones.VOLVER);
        panelBotonVolver.add(btnVolver);

        btnRefrescar = new JButton(Constantes.Botones.ACTUALIZAR);
        btnRefrescar.setVisible(false);
        panelBotonVolver.add(btnRefrescar);

        lblActualizado = new JLabel();
        lblActualizado.setVisible(false);
        panelBotonVolver.add(lblActualizado);

        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(Estilos.Fuentes.TITULO);
        panelTitulo.add(lblTitulo);

        aplicarEstiloCorporativo(panelBotonVolver, panelTitulo);

        add(panelBotonVolver);
        add(panelTitulo);
        Hotkeys.registrarVolver(btnVolver);
    }

    private void aplicarEstiloCorporativo(JPanel panelBoton, JPanel panelTitulo) {
        setBackground(Estilos.Colores.PRIMARIO);
        panelBoton.setBackground(Estilos.Colores.PRIMARIO);
        panelTitulo.setBackground(Estilos.Colores.PRIMARIO);
        lblTitulo.setForeground(Color.WHITE);
        btnVolver.setContentAreaFilled(false);
        btnVolver.setBorderPainted(false);
        btnVolver.setOpaque(false);
        btnVolver.setForeground(Color.WHITE);
        btnVolver.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefrescar.setContentAreaFilled(false);
        btnRefrescar.setBorderPainted(false);
        btnRefrescar.setOpaque(false);
        btnRefrescar.setForeground(Color.WHITE);
        btnRefrescar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblActualizado.setForeground(Color.WHITE);
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
        setGuardNavegacion(hayPendientes, mensajeBloqueo, null);
    }

    /**
     * Igual que {@link #setGuardNavegacion(Supplier, String)} pero permite ejecutar
     * una acción adicional cuando el usuario confirma salir con cambios pendientes.
     *
     * @param hayPendientes  Supplier que retorna true cuando hay cambios sin confirmar
     * @param mensajeBloqueo Mensaje que verá el usuario en el diálogo de confirmación
     * @param onDescartarConfirmado Acción opcional a ejecutar al confirmar descarte
     */
    public void setGuardNavegacion(Supplier<Boolean> hayPendientes, String mensajeBloqueo,
                                   Runnable onDescartarConfirmado) {
        if (navegador == null || contenedor == null || pantallaDestino == null) {
            return;
        }

        // Remover todos los listeners actuales del botón
        for (ActionListener al : btnVolver.getActionListeners()) {
            btnVolver.removeActionListener(al);
        }

        // Agregar listener con guard
        btnVolver.addActionListener(e -> {
            boolean habiaPendientes = hayPendientes.get();
            if (habiaPendientes) {
                int respuesta = JOptionPane.showConfirmDialog(
                    this,
                    mensajeBloqueo,
                    Constantes.Mensajes.TITULO_CAMBIOS_SIN_CONFIRMAR,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (respuesta != JOptionPane.YES_OPTION) {
                    return;  // El usuario eligió quedarse
                }

                if (onDescartarConfirmado != null) {
                    onDescartarConfirmado.run();
                }
            }
            navegador.show(contenedor, pantallaDestino);
        });
    }

    /**
     * Cablea el botón "Actualizar" (y F5) a una acción de relectura y lo hace visible.
     *
     * <p>La {@code accion} debe ser la <em>misma</em> función de carga que la pantalla
     * ya usa al entrar (un {@code Runnable} de {@code Disparador}, o su método de
     * carga): el botón no agrega un camino de lectura nuevo. Si hay una
     * {@link #setGuardRefresco guarda} configurada, se consulta antes de correr la acción.
     *
     * <p>A diferencia de {@link #setGuardNavegacion}, acá <b>no</b> hay early-return si
     * falta el navegador: el refresco no tiene nada que ver con el {@code CardLayout}.
     *
     * @param accion relectura a disparar; nunca {@code null}
     */
    public void setAccionRefrescar(Runnable accion) {
        for (ActionListener al : btnRefrescar.getActionListeners()) {
            btnRefrescar.removeActionListener(al);
        }
        btnRefrescar.addActionListener(e -> {
            if (guardaRefresco == null || guardaRefresco.debeRefrescar()) {
                accion.run();
            }
        });
        btnRefrescar.setVisible(true);
        Hotkeys.registrarRefrescar(btnRefrescar);
    }

    /**
     * Configura la guarda que se consulta antes de refrescar cuando la pantalla
     * tiene trabajo sin guardar. El listener del botón consulta esta guarda; no se
     * reconstruye ni se tocan otros listeners.
     *
     * @param hayPendientes retorna {@code true} cuando hay trabajo sin guardar
     * @param mensaje       cartel de confirmación (debe decir la verdad de la pantalla:
     *                      si lo pendiente se conserva o se descarta)
     * @param onDescartar   acción al confirmar el descarte; {@code null} donde lo
     *                      pendiente se conserva
     */
    public void setGuardRefresco(Supplier<Boolean> hayPendientes, String mensaje,
                                 Runnable onDescartar) {
        this.guardaRefresco = new GuardaRefresco(hayPendientes, mensaje, onDescartar,
                this::confirmarDescarteRefresco);
    }

    private boolean confirmarDescarteRefresco(String mensaje) {
        int respuesta = JOptionPane.showConfirmDialog(
            this,
            mensaje,
            Constantes.Mensajes.TITULO_CAMBIOS_SIN_CONFIRMAR,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        return respuesta == JOptionPane.YES_OPTION;
    }

    /**
     * Muestra "Actualizado HH:mm" con la hora actual. Oculto hasta la primera
     * llamada. Debe llamarse dentro del {@code pintar()} de cada controller, donde
     * el repintado efectivamente ocurrió (no en el click: hay debounce y la lectura
     * puede fallar).
     */
    public void marcarActualizado() {
        lblActualizado.setText(Constantes.Mensajes.REFRESCO_TIMESTAMP_PREFIJO
                + LocalTime.now().format(FORMATO_HORA));
        lblActualizado.setVisible(true);
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