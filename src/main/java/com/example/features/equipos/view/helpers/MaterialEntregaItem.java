package com.example.features.equipos.view.helpers;

public class MaterialEntregaItem {
    private final String material;
    private final int cantidad;
    private final boolean entregado;

    public MaterialEntregaItem(String material, int cantidad, boolean entregado) {
        this.material = material;
        this.cantidad = cantidad;
        this.entregado = entregado;
    }

    public String getMaterial() {
        return material;
    }

    public int getCantidad() {
        return cantidad;
    }

    public boolean isEntregado() {
        return entregado;
    }
}


