package com.example.ui.dialogs;

import javax.swing.*;
import java.util.function.BiConsumer;

import com.example.common.constants.Constantes;
import com.example.ui.common.Estilos;

/**
 * Helper para mostrar diálogo de cantidad con opciones avanzadas.
 * Encapsula la lógica de UI para pedir una cantidad parcial al usuario.
 * 
 * Uso:
 * Integer cantidad = CantidadDialogHelper.pedirCantidad(parent, "Material A", 10);
 */
public class CantidadDialogHelper {

    /**
     * Abre un diálogo para que el usuario ingrese una cantidad.
     * 
     * @param parent Componente padre para centrar el diálogo
     * @param descripcion Nombre/descripción del material
     * @param cantidadDisponible Máximo permitido a ingresar
     * @return Cantidad seleccionada, o null si canceló
     */
    public static Integer pedirCantidad(JPanel parent, String descripcion, int cantidadDisponible) {
        return pedirCantidad(parent, descripcion, cantidadDisponible, null);
    }

    /**
     * Abre un diálogo para que el usuario ingrese una cantidad con opción de "Todos".
     * 
     * @param parent Componente padre para centrar el diálogo
     * @param descripcion Nombre/descripción del material
     * @param cantidadDisponible Máximo permitido a ingresar
     * @param configurarTodos Callback para personalizar el checkbox de "todos" (opcional)
     * @return Cantidad seleccionada, o null si canceló o error
     */
    public static Integer pedirCantidad(JPanel parent, String descripcion, int cantidadDisponible,
                                        BiConsumer<JCheckBox, JSpinner> configurarTodos) {
        
        // Si solo hay 1, retornar directamente
        if (cantidadDisponible <= 1) {
            return cantidadDisponible;
        }

        // Crear panel con layout
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.anchor = java.awt.GridBagConstraints.WEST;

        // Label con instrucción
        JLabel lbl = new JLabel(String.format(
            Constantes.Mensajes.CANTIDAD_AVANZAR_PROMPT, descripcion, cantidadDisponible));
        lbl.setFont(Estilos.Fuentes.INPUT);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(lbl, gbc);

        // Spinner para ingresar cantidad
        SpinnerNumberModel model = new SpinnerNumberModel(1, 1, cantidadDisponible, 1);
        JSpinner spinner = new JSpinner(model);
        spinner.setFont(Estilos.Fuentes.INPUT);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        spinner.setEditor(editor);
        editor.getTextField().setFont(Estilos.Fuentes.INPUT);
        editor.getTextField().setColumns(4);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(spinner, gbc);

        // Checkbox "Todos" (opcional)
        JCheckBox chkTodos = new JCheckBox(Constantes.Mensajes.CANTIDAD_AVANZAR_TODOS);
        chkTodos.setFont(Estilos.Fuentes.INPUT);
        if (configurarTodos != null) {
            configurarTodos.accept(chkTodos, spinner);
        }

        gbc.gridx = 1;
        panel.add(chkTodos, gbc);

        // Mostrar diálogo
        int opcion = JOptionPane.showConfirmDialog(
            parent,
            panel,
            Constantes.Mensajes.TITULO_AVANZAR_SUBCANTIDAD,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        // Si canceló, retornar null
        if (opcion != JOptionPane.OK_OPTION) {
            return null;
        }

        // Obtener cantidad y validar
        int cantidad = (Integer) spinner.getValue();
        if (cantidad <= 0 || cantidad > cantidadDisponible) {
            String msg = String.format(Constantes.Mensajes.CANTIDAD_AVANZAR_RANGO, cantidadDisponible);
            JOptionPane.showMessageDialog(parent, msg,
                Constantes.Mensajes.TITULO_ADVERTENCIA, JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return cantidad;
    }
}


