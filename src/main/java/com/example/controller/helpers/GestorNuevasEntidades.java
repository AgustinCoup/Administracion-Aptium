package com.example.controller.helpers;

import com.example.model.Autocompletable;
import com.example.constants.Constantes;
import com.example.view.dialogs.AgregarAutocompletableDialog;
import com.example.view.helpers.AutocompleteListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import java.awt.Frame;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Gestor genérico para la creación de nuevas autocompletables desde el autocompletado.
 * 
 * Encapsula la lógica común de:
 * - Mostrar diálogo
 * - Guardar en BD
 * - Actualizar campo
 * - Refrescar autocompletado
 * 
 * Evita duplicación de código entre múltiples manejadores específicos.
 * 
 * @param <T> Tipo de autocompletable (debe implementar Autocompletable)
 */
public class GestorNuevasEntidades<T extends Autocompletable> {
    
    private static final Logger log = LoggerFactory.getLogger(GestorNuevasEntidades.class);
    
    private final Frame ventanaParente;
    private final String nombreEntidad;
    private final Consumer<String> actualizarCampoVista;
    private final Consumer<Integer> actualizarSeleccion;
    private final AutocompleteListener<T> autocompletado;
    private final Function<T, Boolean> guardarEnBD;
    private final Supplier<T> creador;
    
    /**
     * Constructor del gestor de nuevas entidades.
     * 
     * @param ventanaParente Frame de la ventana para mostrar diálogos modales
     * @param nombreEntidad Nombre de la entidad (ej: "Cliente", "Profesional")
     * @param actualizarCampoVista Callback para actualizar el campo de texto de la vista
     * @param actualizarSeleccion Callback para actualizar el ID seleccionado
     * @param autocompletado Listener del autocompletado para refrescar
     * @param guardarEnBD Función que guarda la entidad en BD
     * @param creador Función que crea una nueva instancia de T
     */
    public GestorNuevasEntidades(
            Frame ventanaParente,
            String nombreEntidad,
            Consumer<String> actualizarCampoVista,
            Consumer<Integer> actualizarSeleccion,
            AutocompleteListener<T> autocompletado,
            Function<T, Boolean> guardarEnBD,
            Supplier<T> creador) {
        
        this.ventanaParente = ventanaParente;
        this.nombreEntidad = nombreEntidad;
        this.actualizarCampoVista = actualizarCampoVista;
        this.actualizarSeleccion = actualizarSeleccion;
        this.autocompletado = autocompletado;
        this.guardarEnBD = guardarEnBD;
        this.creador = creador;
    }
    
    /**
     * Maneja la situación cuando el usuario ingresa una entidad que no existe en la BD.
     * Ofrece un diálogo para crearla, la guarda, y actualiza la UI.
     * 
     * @param nombreNoExistente Nombre ingresado por el usuario que no coincide con nada
     */
    public void manejarEntidadNoExistente(String nombreNoExistente) {
        // Mostrar diálogo con el nombre propuesto editable
        AgregarAutocompletableDialog<T> dialogo = new AgregarAutocompletableDialog<>(
            ventanaParente,
            nombreNoExistente,
            nombreEntidad,
            creador
        );
        dialogo.setVisible(true);
        
        // Obtener la entidad creada (o null si canceló)
        T nuevaEntidad = dialogo.obtenerEntidad();
        if (nuevaEntidad == null) {
            // Usuario canceló, no hacer nada
            return;
        }
        
        // Guardar la entidad en la BD
        try {
            boolean guardoExitoso = guardarEnBD.apply(nuevaEntidad);
            if (guardoExitoso) {
                // Éxito: actualizar la vista
                actualizarCampoVista.accept(nuevaEntidad.getNombre());
                actualizarSeleccion.accept(nuevaEntidad.getId());
                
                // Refrescar el autocompletado para que aparezca inmediatamente
                autocompletado.refrescarBusqueda();
                
                log.info("{} agregado/a: {}", nombreEntidad, nuevaEntidad.getNombre());
            } else {
                JOptionPane.showMessageDialog(ventanaParente,
                    String.format(Constantes.Mensajes.ERROR_GUARDAR_ENTIDAD, nombreEntidad.toLowerCase()),
                    Constantes.Mensajes.TITULO_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(ventanaParente,
                String.format(Constantes.Mensajes.ERROR_GUARDAR_ENTIDAD_DETALLE, nombreEntidad.toLowerCase(), e.getMessage()),
                Constantes.Mensajes.TITULO_ERROR,
                JOptionPane.ERROR_MESSAGE);
            log.error("Error al guardar {}: {}", nombreEntidad, nombreNoExistente, e);
        }
    }
}
