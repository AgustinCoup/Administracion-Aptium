package com.example.features.lotes.model;

public class LoteMovimiento {
    private final int materialId;
    private final int equipoId;
    private final int cantidad;

    public LoteMovimiento(int materialId, int equipoId, int cantidad) {
        this.materialId = materialId;
        this.equipoId = equipoId;
        this.cantidad = cantidad;
    }

    public int getMaterialId() {
        return materialId;
    }

    public int getEquipoId() {
        return equipoId;
    }

    public int getCantidad() {
        return cantidad;
    }
}


