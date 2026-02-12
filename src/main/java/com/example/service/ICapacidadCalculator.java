package com.example.service;

import com.example.model.LoteMaterialInfo;
import java.util.List;

/**
 * Contrato para cálculos de capacidad y volumen.
 * Centraliza operaciones matemáticas de capacidad que antes estaban dispersas.
 * 
 * Principios: SRP (solo calcula), DIP (interfaz para abstracción)
 */
public interface ICapacidadCalculator {
    
    /**
     * Calcula el volumen total ocupado en un lote.
     * Volumen total = SUM(cantidad * volumen_unitario).
     * 
     * @param materiales Materiales del lote
     * @return Volumen total ocupado
     */
    int calcularVolumenTotal(List<LoteMaterialInfo> materiales);
    
    /**
     * Calcula el porcentaje de capacidad usada.
     * 
     * @param capacidadUsada Volumen actualmente usado
     * @param capacidadTotal Volumen máximo disponible
     * @return Porcentaje (0-100)
     */
    int calcularPorcentajeUso(int capacidadUsada, int capacidadTotal);
    
    /**
     * Valida si agregar una cantidad causaría overflow.
     * 
     * @param capacidadUsada Volumen actual
     * @param volumenAgregar Volumen a agregar
     * @param capacidadTotal Límite máximo
     * @return true si cabe el nuevo volumen
     */
    boolean cabeLaCapacidad(int capacidadUsada, int volumenAgregar, int capacidadTotal);
    
    /**
     * Valida si un lote está en "estado de advertencia" (>= 80% lleno).
     * 
     * @param capacidadUsada Volumen actual
     * @param capacidadTotal Límite máximo
     * @return true si el porcentaje es >= 80%
     */
    boolean requiereAdvertencia(int capacidadUsada, int capacidadTotal);
    
    /**
     * Obtiene el espacio disponible restante.
     * 
     * @param capacidadUsada Volumen actual
     * @param capacidadTotal Límite máximo
     * @return Espacio disponible
     */
    int calcularEspacioDisponible(int capacidadUsada, int capacidadTotal);
}
