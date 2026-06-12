package com.example.features.lavadero.model;

public class ElementoClasificacion {

    private final int elementoId;
    private final int cantidad;

    public ElementoClasificacion(int elementoId, int cantidad) {
        this.elementoId = elementoId;
        this.cantidad   = cantidad;
    }

    public int getElementoId() { return elementoId; }
    public int getCantidad()   { return cantidad; }
}
