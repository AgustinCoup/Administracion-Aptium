package com.example.features.lotes.view.helpers;

import java.io.Serializable;

public class MaterialLoteItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int materialId;
    private final int equipoId;
    private final String descripcion;
    private int cantidad;
    private final int volumen;

    public MaterialLoteItem(int materialId, int equipoId, String descripcion, int cantidad, int volumen) {
        this.materialId = materialId;
        this.equipoId = equipoId;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.volumen = volumen;
    }

    public int getMaterialId() {
        return materialId;
    }

    public int getEquipoId() {
        return equipoId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getVolumen() {
        return volumen;
    }

    public int getVolumenTotal() {
        return cantidad * volumen;
    }
}


