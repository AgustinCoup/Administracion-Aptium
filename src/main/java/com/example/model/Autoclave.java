package com.example.model;

public class Autoclave {
    private final String nombre;
    private final int capacidad;

    public Autoclave(String nombre, int capacidad) {
        this.nombre = nombre;
        this.capacidad = capacidad;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCapacidad() {
        return capacidad;
    }
}
