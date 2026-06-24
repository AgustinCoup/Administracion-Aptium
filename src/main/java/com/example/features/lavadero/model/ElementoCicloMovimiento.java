package com.example.features.lavadero.model;

public class ElementoCicloMovimiento {

    private final int elementoClasificacionId;
    private final int cantidad;

    public ElementoCicloMovimiento(int elementoClasificacionId, int cantidad) {
        this.elementoClasificacionId = elementoClasificacionId;
        this.cantidad                = cantidad;
    }

    public int getElementoClasificacionId() { return elementoClasificacionId; }
    public int getCantidad()                { return cantidad; }
}
