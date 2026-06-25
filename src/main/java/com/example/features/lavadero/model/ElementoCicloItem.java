package com.example.features.lavadero.model;

import java.io.Serializable;

public class ElementoCicloItem implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CATEGORIA_EQUIPO  = "EQUIPO";
    public static final String CATEGORIA_REGULAR = "REGULAR";

    private final int elementoClasificacionId;
    private final int ingresoId;
    private final String elementoNombre;
    private final int cantidadTotal;
    private final int cantidadYaProcesada;
    private final String clienteNombre;
    private final String categoria;

    private int cantidadEnCiclo;

    public ElementoCicloItem(int elementoClasificacionId, int ingresoId, String elementoNombre,
                              int cantidadTotal, int cantidadYaProcesada, String clienteNombre) {
        this(elementoClasificacionId, ingresoId, elementoNombre, cantidadTotal, cantidadYaProcesada,
             clienteNombre, CATEGORIA_REGULAR);
    }

    public ElementoCicloItem(int elementoClasificacionId, int ingresoId, String elementoNombre,
                              int cantidadTotal, int cantidadYaProcesada, String clienteNombre,
                              String categoria) {
        this.elementoClasificacionId = elementoClasificacionId;
        this.ingresoId               = ingresoId;
        this.elementoNombre          = elementoNombre;
        this.cantidadTotal           = cantidadTotal;
        this.cantidadYaProcesada     = cantidadYaProcesada;
        this.clienteNombre           = clienteNombre;
        this.categoria               = categoria;
        this.cantidadEnCiclo         = 0;
    }

    public int getElementoClasificacionId() { return elementoClasificacionId; }
    public int getIngresoId()               { return ingresoId; }
    public String getElementoNombre()       { return elementoNombre; }
    public int getCantidadTotal()           { return cantidadTotal; }
    public int getCantidadYaProcesada()     { return cantidadYaProcesada; }
    public String getClienteNombre()        { return clienteNombre; }
    public String getCategoria()            { return categoria; }
    public boolean isEquipo()               { return CATEGORIA_EQUIPO.equals(categoria); }

    public int getCantidadDisponible()      { return cantidadTotal - cantidadYaProcesada; }

    public int getCantidadEnCiclo()         { return cantidadEnCiclo; }
    public void setCantidadEnCiclo(int cantidadEnCiclo) { this.cantidadEnCiclo = cantidadEnCiclo; }
}
