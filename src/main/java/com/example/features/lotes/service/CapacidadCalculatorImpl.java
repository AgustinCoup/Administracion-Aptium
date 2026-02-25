package com.example.features.lotes.service;

import com.example.features.lotes.model.LoteMaterialInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Implementación de cálculos de capacidad.
 * Centraliza lógica matemática que antes estaba en múltiples lugares.
 * 
 * SRP: Solo realiza cálculos de volumen/capacidad.
 * Testeable: Sin dependencias externas, lógica pura.
 */
public class CapacidadCalculatorImpl implements ICapacidadCalculator {
    
    private static final Logger log = LoggerFactory.getLogger(CapacidadCalculatorImpl.class);
    
    // Umbral en porcentaje para considerar "lleno" y mostrar advertencia
    private static final int UMBRAL_ADVERTENCIA = 80;
    
    @Override
    public int calcularVolumenTotal(List<LoteMaterialInfo> materiales) {
        if (materiales == null || materiales.isEmpty()) {
            return 0;
        }
        
        int total = 0;
        for (LoteMaterialInfo material : materiales) {
            total += material.getVolumenTotal();
        }
        
        return total;
    }
    
    @Override
    public int calcularPorcentajeUso(int capacidadUsada, int capacidadTotal) {
        if (capacidadTotal <= 0) {
            return 0;
        }
        
        return (capacidadUsada * 100) / capacidadTotal;
    }
    
    @Override
    public boolean cabeLaCapacidad(int capacidadUsada, int volumenAgregar, int capacidadTotal) {
        if (capacidadTotal <= 0) {
            return false;
        }
        
        int totalNuevo = capacidadUsada + volumenAgregar;
        return totalNuevo <= capacidadTotal;
    }
    
    @Override
    public boolean requiereAdvertencia(int capacidadUsada, int capacidadTotal) {
        if (capacidadTotal <= 0) {
            return false;
        }
        
        int porcentaje = calcularPorcentajeUso(capacidadUsada, capacidadTotal);
        return porcentaje >= UMBRAL_ADVERTENCIA;
    }
    
    @Override
    public int calcularEspacioDisponible(int capacidadUsada, int capacidadTotal) {
        if (capacidadTotal < 0 || capacidadUsada < 0) {
            return 0;
        }
        
        int disponible = capacidadTotal - capacidadUsada;
        return Math.max(0, disponible);  // Nunca retorna negativo
    }
}


