package com.example.features.equipos.ortopedias.view.helpers;

public class InstitucionEntregaItem {
    private final int id;
    private final String nombre;
    private final int equiposCount;

    public InstitucionEntregaItem(int id, String nombre, int equiposCount) {
        this.id = id;
        this.nombre = nombre;
        this.equiposCount = equiposCount;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getEquiposCount() {
        return equiposCount;
    }
}


