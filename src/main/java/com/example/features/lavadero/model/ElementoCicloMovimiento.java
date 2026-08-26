package com.example.features.lavadero.model;

public class ElementoCicloMovimiento {

    private final int elementoClasificacionId;
    private final int cantidad;
    private final Integer instanciaEquipoId;

    public ElementoCicloMovimiento(int elementoClasificacionId, int cantidad) {
        this(elementoClasificacionId, cantidad, null);
    }

    public ElementoCicloMovimiento(int elementoClasificacionId, int cantidad, Integer instanciaEquipoId) {
        this.elementoClasificacionId = elementoClasificacionId;
        this.cantidad                = cantidad;
        this.instanciaEquipoId       = instanciaEquipoId;
    }

    public int getElementoClasificacionId() { return elementoClasificacionId; }
    public int getCantidad()                { return cantidad; }
    public Integer getInstanciaEquipoId()   { return instanciaEquipoId; }
}
