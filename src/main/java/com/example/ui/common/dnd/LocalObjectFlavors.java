package com.example.ui.common.dnd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.datatransfer.DataFlavor;
import java.util.List;

/**
 * Factory de {@link DataFlavor} para objetos que sólo viajan dentro de la misma
 * JVM (drag-and-drop entre componentes de la app, nunca hacia afuera).
 */
public final class LocalObjectFlavors {

    private static final Logger log = LoggerFactory.getLogger(LocalObjectFlavors.class);

    private LocalObjectFlavors() {
        throw new UnsupportedOperationException("Clase de utilidades no instanciable");
    }

    /**
     * Flavor para transportar una {@link List} local, el que espera
     * {@link MultiRowTransferable}.
     *
     * <p><b>Nota:</b> {@code DataFlavor.equals} compara la representation class, no
     * el nombre presentable, así que todos los flavors creados acá son iguales entre
     * sí: una tabla que acepte este flavor aceptaría el arrastre de cualquier otra
     * pantalla que también lo use. No es un problema mientras las pantallas
     * involucradas vivan en tarjetas distintas del {@code CardLayout} y nunca estén
     * visibles a la vez (caso de Lotes y Ciclos): un arrastre no puede empezar en una
     * y terminar en la otra.
     *
     * @return el flavor, o {@code null} si el runtime no pudo resolver la clase
     *         (imposible en la práctica; se loguea y el llamador desactiva el DnD)
     */
    public static DataFlavor forList() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                + ";class=\"" + List.class.getName() + "\"");
        } catch (ClassNotFoundException e) {
            log.error("No se pudo registrar el DataFlavor de lista local para drag-and-drop", e);
            return null;
        }
    }
}
