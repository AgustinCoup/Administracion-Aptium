package com.example.features.lavadero.model;

import java.io.Serializable;

public class ElementoCicloItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int elementoClasificacionId;
    private final int ingresoId;
    private final String elementoNombre;
    private final int cantidadTotal;
    private final int cantidadYaProcesada;
    private final String clienteNombre;

    private int cantidadEnCiclo;

    public ElementoCicloItem(int elementoClasificacionId, int ingresoId, String elementoNombre,
                              int cantidadTotal, int cantidadYaProcesada, String clienteNombre) {
        this.elementoClasificacionId = elementoClasificacionId;
        this.ingresoId               = ingresoId;
        this.elementoNombre          = elementoNombre;
        this.cantidadTotal           = cantidadTotal;
        this.cantidadYaProcesada     = cantidadYaProcesada;
        this.clienteNombre           = clienteNombre;
        this.cantidadEnCiclo         = 0;
    }

    public int getElementoClasificacionId() { return elementoClasificacionId; }
    public int getIngresoId()               { return ingresoId; }
    public String getElementoNombre()       { return elementoNombre; }
    public int getCantidadTotal()           { return cantidadTotal; }
    public int getCantidadYaProcesada()     { return cantidadYaProcesada; }
    public String getClienteNombre()        { return clienteNombre; }

    public int getCantidadDisponible()      { return cantidadTotal - cantidadYaProcesada; }

    public int getCantidadEnCiclo()         { return cantidadEnCiclo; }
    public void setCantidadEnCiclo(int cantidadEnCiclo) { this.cantidadEnCiclo = cantidadEnCiclo; }
}
