package com.example.features.lavadero.model;

public class JabonCatalogo {

    private final int    id;
    private final String nombre;

    public JabonCatalogo(int id, String nombre) {
        this.id     = id;
        this.nombre = nombre;
    }

    public int    getId()     { return id; }
    public String getNombre() { return nombre; }

    @Override
    public String toString() { return nombre; }
}
