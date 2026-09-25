package com.example.ui.common.seleccion;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Reglas de la selección acumulativa de una tabla, sin nada de Swing: qué selección
 * resulta de Ctrl+↓ / Ctrl+↑, y si una pérdida de foco la conserva.
 *
 * <p>La instalación sobre una {@code JTable} concreta vive en
 * {@link SeleccionAcumulativaTabla}; esta clase es la parte que decide, y por eso la
 * que se testea en aislamiento. <b>No importa nada de {@code javax.swing} ni de
 * {@code java.awt}</b>, a propósito.
 *
 * <p>La regla central: Ctrl+flecha <b>suma</b> la fila vecina del ancla y <b>nunca
 * quita</b> filas. El binding de Swing que reemplaza ({@code selectNextRowChangeLead})
 * mueve el recuadro de foco sin seleccionar, y el operador cree que sumó una fila.
 */
public final class ReglasSeleccionAcumulativa {

    /** Valor de {@link Seleccion#ancla()} cuando no hay fila tocada. */
    public static final int SIN_ANCLA = -1;

    private ReglasSeleccionAcumulativa() {
        throw new UnsupportedOperationException("Clase de utilidades no instanciable");
    }

    /**
     * Selección de filas (índices de vista) y la última fila tocada.
     *
     * @param filas filas seleccionadas; se copian y quedan inmodificables
     * @param ancla la última fila tocada (el <em>lead</em> del modelo de selección), o
     *              {@link #SIN_ANCLA}. Puede no estar en {@code filas}: tras un Ctrl+click
     *              que deseleccionó una fila, el ancla es esa fila.
     */
    public record Seleccion(SortedSet<Integer> filas, int ancla) {
        public Seleccion {
            filas = Collections.unmodifiableSortedSet(new TreeSet<>(filas));
        }
    }

    /** Ctrl+↓: suma la fila de abajo del ancla, que pasa a ser el ancla nueva. */
    public static Seleccion sumarSiguiente(Seleccion actual, int cantidadFilas) {
        return sumar(actual, actual.ancla() + 1, cantidadFilas);
    }

    /** Ctrl+↑: suma la fila de arriba del ancla, que pasa a ser el ancla nueva. */
    public static Seleccion sumarAnterior(Seleccion actual, int cantidadFilas) {
        return sumar(actual, actual.ancla() - 1, cantidadFilas);
    }

    /**
     * Sin selección, o sin ancla, devuelve la misma: Ctrl+flecha no inventa un punto de
     * partida. Con el destino fuera de la tabla (ancla en el borde), también. Si no,
     * agrega el destino —si ya estaba, no pasa nada— y lo hace ancla. Nunca saca filas.
     */
    private static Seleccion sumar(Seleccion actual, int destino, int cantidadFilas) {
        boolean sinPuntoDePartida = actual.filas().isEmpty() || actual.ancla() == SIN_ANCLA;
        boolean fueraDeLaTabla = destino < 0 || destino >= cantidadFilas;
        if (sinPuntoDePartida || fueraDeLaTabla) {
            return actual;
        }
        SortedSet<Integer> filas = new TreeSet<>(actual.filas());
        filas.add(destino);
        return new Seleccion(filas, destino);
    }

    /**
     * Si una pérdida de foco conserva la selección. La conservan las pérdidas
     * <b>temporales</b> (diálogo modal, Alt+Tab, otra ventana) y las que van a otro
     * componente de la zona (la tabla o un exento). Cualquier otra la vacía.
     *
     * <p>Es trivial, pero es <em>la regla</em>: vive acá con nombre y test para que nadie
     * la reescriba adentro de un listener.
     */
    public static boolean conservarAlPerderFoco(boolean perdidaTemporal, boolean destinoEnLaZona) {
        return perdidaTemporal || destinoEnLaZona;
    }
}
