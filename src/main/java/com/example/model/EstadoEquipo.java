package com.example.model;

import java.awt.Color;

/**
 * Enum que define todos los estados posibles de un equipo en el sistema.
 * Encapsula los valores válidos y evita errores de tipeo con strings.
 * 
 * Cada estado incluye:
 * - Nombre del estado
 * - Color asociado para visualización en la UI
 * - Orden de progresión en el flujo de trabajo
 */
public enum EstadoEquipo {
    
    NUEVO("Nuevo", new Color(211, 211, 211), 1),              // LIGHT_GRAY
    LAVANDO("Lavando", Color.CYAN, 2),
    LAVADO("Lavado", new Color(135, 206, 250), 3),            // SKY_BLUE
    EMPAQUETADO("Empaquetado", Color.ORANGE, 4),
    ESTERILIZANDO("Esterilizando", Color.PINK, 5),
    ESTERILIZADO("Esterilizado", Color.GREEN, 6),
    ENTREGADO("Entregado", Color.GRAY, 7);               // Estado final
    
    private final String nombre;
    private final Color color;
    private final int orden;  // Orden en el flujo de trabajo
    
    /**
     * Constructor del enum.
     * 
     * @param nombre Nombre legible del estado
     * @param color Color asociado para UI
     * @param orden Posición en el flujo de trabajo
     */
    EstadoEquipo(String nombre, Color color, int orden) {
        this.nombre = nombre;
        this.color = color;
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
     * Obtiene el color asociado para visualización.
     * @return Color del estado
     */
    public Color getColor() {
        return color;
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
     * @return true si es ESTERILIZADO
     */
    public boolean esFinal() {
        return this == ESTERILIZADO;
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
