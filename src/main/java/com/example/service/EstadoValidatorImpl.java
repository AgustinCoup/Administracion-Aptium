package com.example.service;

import com.example.model.EstadoEquipo;
import com.example.model.Material;
import com.example.model.Equipo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementación de validaciones de estado.
 * Centraliza la lógica de flujo de estado para evitar duplicación.
 * 
 * SRP: Solo valida estados según reglas de negocio.
 */
public class EstadoValidatorImpl implements IEstadoValidator {
    
    private static final Logger log = LoggerFactory.getLogger(EstadoValidatorImpl.class);
    
    @Override
    public boolean puedeAvanzar(Material material, Equipo equipo) {
        if (material == null || equipo == null) {
            return false;
        }
        EstadoEquipo siguiente = obtenerProximoEstado(material, equipo);
        return siguiente != null;
    }
    
    @Override
    public EstadoEquipo obtenerProximoEstado(Material material, Equipo equipo) {
        if (material == null || equipo == null) {
            return null;
        }
        EstadoEquipo estadoActual = material.getEstado();
        return equipo.getSiguienteEstado(estadoActual);
    }
    
    @Override
    public boolean esEntregable(EstadoEquipo estado) {
        if (estado == null) {
            return false;
        }
        return estado.getOrden() >= EstadoEquipo.ESTERILIZADO.getOrden();
    }
    
    @Override
    public boolean esFinal(EstadoEquipo estado) {
        if (estado == null) {
            return false;
        }
        return estado == EstadoEquipo.ENTREGADO;
    }
    
    @Override
    public boolean necesitaEsterilizado(Material material, Equipo equipo) {
        if (material == null || equipo == null) {
            return false;
        }
        EstadoEquipo siguiente = obtenerProximoEstado(material, equipo);
        return siguiente == EstadoEquipo.ESTERILIZANDO;
    }
}
