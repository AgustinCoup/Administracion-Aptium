package com.example.features.equipos.ortopedias.model;

/**
 * Enum que define todos los estados posibles de un equipo en el sistema.
 * Encapsula los valores válidos y evita errores de tipeo con strings.
 *
 * Cada estado incluye:
 * - Nombre del estado
 * - Orden de progresión en el flujo de trabajo
 *
 * <p>El color con el que se pinta cada estado es una decisión de presentación
 * y vive en {@code TableStyler}, no acá: este enum es de dominio y no debe
 * depender de AWT/Swing.
 */
public enum EstadoEquipo {

    NUEVO("Nuevo", 1),
    LAVANDO("Lavando", 2),
    LAVADO("Lavado", 3),
    EMPAQUETADO("Empaquetado", 4),
    ESTERILIZANDO("Esterilizando", 5),
    ESTERILIZADO("Esterilizado", 6),
    ENTREGADO("Entregado", 7);               // Estado final

    private final String nombre;
    private final int orden;  // Orden en el flujo de trabajo

    /**
     * Constructor del enum.
     *
     * @param nombre Nombre legible del estado
     * @param orden Posición en el flujo de trabajo
     */
    EstadoEquipo(String nombre, int orden) {
        this.nombre = nombre;
        this.orden = orden;
    }
    
    /**
     * Obtiene el nombre legible del estado.
     * @return Nombre del estado
     */
    public String getNombre() {
        return nombre;
    }
    
    /**
     * Obtiene el orden en el flujo de trabajo.
     * Útil para determinar qué estado sigue.
     * @return Número de orden (1-6)
     */
    public int getOrden() {
        return orden;
    }
    
    /**
     * Obtiene el siguiente estado en el flujo de trabajo.
     * @return El siguiente estado, o null si ya es el final
     */
    public EstadoEquipo getSiguiente() {
        for (EstadoEquipo estado : EstadoEquipo.values()) {
            if (estado.orden == this.orden + 1) {
                return estado;
            }
        }
        return null; // Es el último estado
    }
    
    /**
     * Verifica si el estado es el final del proceso.
     * @return true si es ENTREGADO
     */
    public boolean esFinal() {
        return this == ENTREGADO;
    }
    
    /**
     * Verifica si el estado es el inicial.
     * @return true si es NUEVO
     */
    public boolean esInicial() {
        return this == NUEVO;
    }
    
    /**
     * Obtiene un estado por su nombre.
     * Útil para convertir valores de la base de datos.
     * 
     * @param nombre Nombre del estado a buscar
     * @return El estado correspondiente, o NUEVO si no existe
     */
    public static EstadoEquipo desdeBD(String nombre) {
        for (EstadoEquipo estado : EstadoEquipo.values()) {
            if (estado.nombre.equalsIgnoreCase(nombre)) {
                return estado;
            }
        }
        return NUEVO; // Valor por defecto
    }
    
    /**
     * Representación en string del estado.
     * @return El nombre del estado
     */
    @Override
    public String toString() {
        return nombre;
    }
}


