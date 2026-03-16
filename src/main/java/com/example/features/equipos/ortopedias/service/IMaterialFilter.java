package com.example.features.equipos.ortopedias.service;

import java.util.List;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;

/**
 * Contrato para filtrar colecciones de materiales según criterios.
 * Centraliza lógica de filtrado para evitar duplicación en controllers.
 * 
 * Principios: SRP (solo filtra), DIP (interfaz para abstracción)
 */
public interface IMaterialFilter {
    
    /**
     * Obtiene materiales que necesitan ir a esterilización.
     * La siguiente etapa del material en su equipo es ESTERILIZANDO.
     * 
     * @param equipo Equipo a analizar
     * @return Lista de materiales que necesitan esterilización
     */
    List<Material> obtenerQueNecesitanEsterilizado(Equipo equipo);
    
    /**
     * Obtiene todos los materiales que necesitan esterilización de múltiples equipos.
     * 
     * @param equipos Lista de equipos
     * @return Lista plana de todos los materiales esterilizables
     */
    List<Material> obtenerQueNecesitanEsterilizadoMultiple(List<Equipo> equipos);
    
    /**
     * Filtra materiales de un equipo por estado específico.
     * 
     * @param equipo Equipo contenedor
     * @param estado Estado deseado
     * @return Materiales en ese estado
     */
    List<Material> obtenerPorEstado(Equipo equipo, EstadoEquipo estado);
    
    /**
     * Obtiene materiales entregables (estado >= ESTERILIZADO).
     * 
     * @param equipo Equipo a analizar
     * @return Materiales en estado entregable
     */
    List<Material> obtenerEntregables(Equipo equipo);
    
    /**
     * Obtiene todos los materiales entregables de múltiples equipos.
     * 
     * @param equipos Lista de equipos
     * @return Materiales entregables de todos
     */
    List<Material> obtenerEntregablesMultiple(List<Equipo> equipos);
}


