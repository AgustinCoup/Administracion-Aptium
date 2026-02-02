package com.example.controller.listeners;

/**
 * Interfaz callback para notificar cuando un equipo ha sido guardado.
 * Respeta el patrón MVC manteniendo desacoplados los controladores.
 */
public interface OnEquipoGuardadoListener {
    /**
     * Se ejecuta cuando un equipo fue guardado exitosamente en la BD.
     */
    void onEquipoGuardado();
}
