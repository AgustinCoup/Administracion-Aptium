package com.example.features.lotes.model;

public class LoteMaterialInfo {
    private final int materialId;
    private final int equipoId;
    private final int codigoCatalogo;
    private final String descripcion;
    private final int cantidad;
    private final int volumen;

    public LoteMaterialInfo(int materialId, int equipoId, int codigoCatalogo, String descripcion,
                             int cantidad, int volumen) {
        this.materialId = materialId;
        this.equipoId = equipoId;
        this.codigoCatalogo = codigoCatalogo;
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

    public int getCodigoCatalogo() {
        return codigoCatalogo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getVolumen() {
        return volumen;
    }

    public int getVolumenTotal() {
        return cantidad * volumen;
    }
}


