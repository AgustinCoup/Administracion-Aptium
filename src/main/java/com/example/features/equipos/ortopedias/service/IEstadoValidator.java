package com.example.features.equipos.ortopedias.service;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;

/**
 * Contrato para validar reglas de estados según lógica de negocio.
 * Interface principal: facilita testing e inyección de dependencias.
 * 
 * Principios: SRP (validación de estados), DIP (depender de abstracción)
 */
public interface IEstadoValidator {
    
    /**
     * Valida si un material puede avanzar al siguiente estado.
     * @param material Material a validar
     * @param equipo Equipo que contiene el material
     * @return true si puede avanzar
     */
    boolean puedeAvanzar(Material material, Equipo equipo);
    
    /**
     * Obtiene el próximo estado esperado para un material dentro de su equipo.
     * @param material Material a evaluar
     * @param equipo Equipo contenedor
     * @return Próximo estado, o null si ya está finalizado
     */
    EstadoEquipo obtenerProximoEstado(Material material, Equipo equipo);
    
    /**
     * Valida si un material/equipo está en estado entregable.
     * @param estado Estado a validar
     * @return true si está >= ESTERILIZADO
     */
    boolean esEntregable(EstadoEquipo estado);
    
    /**
     * Valida si un estado es el final del flujo.
     * @param estado Estado a validar
     * @return true si es ENTREGADO
     */
    boolean esFinal(EstadoEquipo estado);
    
    /**
     * Valida si un material necesita ser esterilizado.
     * @param material Material a verificar
     * @param equipo Equipo contenedor
     * @return true si el próximo estado es ESTERILIZANDO
     */
    boolean necesitaEsterilizado(Material material, Equipo equipo);

    /**
     * Indica si el avance de estado puede hacerse manualmente desde la pantalla
     * de registro. ESTERILIZANDO y ESTERILIZADO son gestionados por autoclave.
     *
     * @param estadoActual    Estado actual del material
     * @param estadoSiguiente Siguiente estado calculado (puede ser null)
     * @return true si la transición es ejecutable manualmente
     */
    boolean esAvanzableManualmente(EstadoEquipo estadoActual, EstadoEquipo estadoSiguiente);
}


