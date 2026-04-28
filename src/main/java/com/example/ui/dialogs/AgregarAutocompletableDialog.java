package com.example.ui.dialogs;

import com.example.common.model.Autocompletable;

import java.awt.Window;
import java.util.function.Supplier;

/**
 * Diálogo para agregar una nueva autocompletable (Cliente, Profesional, Institución).
 * Extiende {@link NuevoElementoDialog} y añade la creación de la entidad tipada.
 *
 * @param <T> Tipo de autocompletable a crear (debe implementar {@link Autocompletable})
 */
public class AgregarAutocompletableDialog<T extends Autocompletable> extends NuevoElementoDialog {

    private final Supplier<T> creador;

    public AgregarAutocompletableDialog(Window parent, String nombrePropuesto,
                                        String nombreEntidad, Supplier<T> creador) {
        super(parent, nombreEntidad, nombrePropuesto);
        this.creador = creador;
    }

    /**
     * Retorna la entidad creada con el nombre confirmado, o {@code null} si el usuario canceló.
     * Llamar DESPUÉS de que el diálogo se haya cerrado (tras {@code setVisible(true)}).
     */
    public T obtenerEntidad() {
        String nombre = obtenerResultado();
        if (nombre == null) return null;
        T entidad = creador.get();
        entidad.setId(0);
        entidad.setNombre(nombre);
        return entidad;
    }
}
