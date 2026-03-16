package com.example.features.equipos.ortopedias.controller.helpers;

import java.util.ArrayList;
import java.util.List;

public class InstitucionAcumulador {
    private final int id;
    private final String nombre;
    private final List<Integer> equipoIds = new ArrayList<>();

    public InstitucionAcumulador(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public void agregarEquipo(Integer equipoId) {
        if (equipoId != null && !equipoIds.contains(equipoId)) {
            equipoIds.add(equipoId);
        }
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getEquiposCount() {
        return equipoIds.size();
    }
}


