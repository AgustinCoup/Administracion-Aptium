package com.example.features.equipos.ortopedias.controller.helpers;

/**
 * Clase auxiliar para agrupar materiales del mismo tipo (código de catálogo).
 * Acumula cantidades totales y cantidades entregadas para consolidar la visualización.
 */
public class MaterialAgrupado {
    private String descripcion;
    private int cantidadTotal = 0;
    private int cantidadEntregada = 0;
    
    public MaterialAgrupado(String descripcion) {
        this.descripcion = descripcion;
    }
    
    public void agregar(int cantidad, boolean entregado) {
        this.cantidadTotal += cantidad;
        if (entregado) {
            this.cantidadEntregada += cantidad;
        }
    }
    
    public String getDescripcion() {
        return descripcion;
    }
    
    public int getCantidadTotal() {
        return cantidadTotal;
    }
    
    public int getCantidadEntregada() {
        return cantidadEntregada;
    }
    
    public boolean todosEntregados() {
        return cantidadEntregada == cantidadTotal;
    }
}


