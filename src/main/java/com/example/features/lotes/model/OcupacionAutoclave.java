package com.example.features.lotes.model;

/**
 * Ocupación de un autoclave: el par (volumen usado, capacidad total) y las
 * reglas que se derivan de él. Inmutable y sin dependencias, así que lo
 * construyen por igual el controller, los diálogos y los helpers de Swing a
 * partir de los dos enteros que ya tienen — sin inyectar ningún service.
 *
 * <p>Es el único dueño del umbral de advertencia y de la aritmética de
 * porcentaje, que antes estaba duplicada (y calculada de tres formas distintas)
 * entre la barra de capacidad, el diálogo de lanzamiento y la confirmación de
 * ortopedias.
 */
public final class OcupacionAutoclave {

    /** Por debajo de este porcentaje se avisa que el autoclave va poco cargado. */
    public static final int UMBRAL_ADVERTENCIA = 80;

    private final int usada;
    private final int total;

    public OcupacionAutoclave(int usada, int total) {
        this.usada = usada;
        this.total = total;
    }

    public int getUsada() {
        return usada;
    }

    public int getTotal() {
        return total;
    }

    /**
     * Porcentaje de capacidad ocupada. Devuelve 0 si no hay capacidad conocida,
     * y puede superar 100 cuando el volumen se pasa del tope.
     */
    public int porcentaje() {
        if (total <= 0) {
            return 0;
        }
        return (usada * 100) / total;
    }

    /** El volumen cargado se pasa de la capacidad del autoclave. */
    public boolean estaSobrecargado() {
        return usada > total;
    }

    /**
     * El autoclave va con menos carga que el umbral: se lanzaría desaprovechado.
     * Sin capacidad conocida se considera poco cargado (porcentaje 0).
     */
    public boolean estaPocoCargado() {
        return porcentaje() < UMBRAL_ADVERTENCIA;
    }
}
