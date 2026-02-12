package com.example.view.helpers;

public class AutoclaveItem {
    private final String nombre;
    private final int capacidad;
    private final boolean ocupado;
    private final Integer loteId;
    private final int capacidadUsada;

    public AutoclaveItem(String nombre, int capacidad, boolean ocupado, Integer loteId, int capacidadUsada) {
        this.nombre = nombre;
        this.capacidad = capacidad;
        this.ocupado = ocupado;
        this.loteId = loteId;
        this.capacidadUsada = capacidadUsada;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public boolean isOcupado() {
        return ocupado;
    }

    public Integer getLoteId() {
        return loteId;
    }

    public int getCapacidadUsada() {
        return capacidadUsada;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
