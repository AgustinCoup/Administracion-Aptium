package com.example.features.equipos.ortopedias.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de filtrado de materiales.
 * Centraliza lógica que antes estaba duplicada en controllers.
 * 
 * SRP: Solo filtra listas según criterios de negocio.
 * Depende de: IEstadoValidator (para validar qué estados aplican)
 */
public class MaterialFilterImpl implements IMaterialFilter {
    
    private static final Logger log = LoggerFactory.getLogger(MaterialFilterImpl.class);
    private final IEstadoValidator estadoValidator;
    
    public MaterialFilterImpl(IEstadoValidator estadoValidator) {
        this.estadoValidator = estadoValidator;
    }
    
    @Override
    public List<Material> obtenerQueNecesitanEsterilizado(Equipo equipo) {
        List<Material> resultado = new ArrayList<>();
        
        if (equipo == null || equipo.getMateriales() == null) {
            return resultado;
        }
        
        for (Material material : equipo.getMateriales()) {
            if (estadoValidator.necesitaEsterilizado(material, equipo)) {
                resultado.add(material);
            }
        }
        
        return resultado;
    }
    
    @Override
    public List<Material> obtenerQueNecesitanEsterilizadoMultiple(List<Equipo> equipos) {
        List<Material> resultado = new ArrayList<>();
        
        if (equipos == null) {
            return resultado;
        }
        
        for (Equipo equipo : equipos) {
            resultado.addAll(obtenerQueNecesitanEsterilizado(equipo));
        }
        
        return resultado;
    }
    
    @Override
    public List<Material> obtenerPorEstado(Equipo equipo, EstadoEquipo estado) {
        List<Material> resultado = new ArrayList<>();
        
        if (equipo == null || equipo.getMateriales() == null || estado == null) {
            return resultado;
        }
        
        for (Material material : equipo.getMateriales()) {
            if (material.getEstado() == estado) {
                resultado.add(material);
            }
        }
        
        return resultado;
    }
    
    @Override
    public List<Material> obtenerEntregables(Equipo equipo) {
        List<Material> resultado = new ArrayList<>();
        
        if (equipo == null || equipo.getMateriales() == null) {
            return resultado;
        }
        
        for (Material material : equipo.getMateriales()) {
            if (estadoValidator.esEntregable(material.getEstado())) {
                resultado.add(material);
            }
        }
        
        return resultado;
    }
    
    @Override
    public List<Material> obtenerEntregablesMultiple(List<Equipo> equipos) {
        List<Material> resultado = new ArrayList<>();
        
        if (equipos == null) {
            return resultado;
        }
        
        for (Equipo equipo : equipos) {
            resultado.addAll(obtenerEntregables(equipo));
        }
        
        return resultado;
    }
}


