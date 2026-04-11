package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.common.model.EntregaDestinoKey;

import java.util.ArrayList;
import java.util.List;

public class InstitucionAcumulador {
    private final EntregaDestinoKey key;
    private final String nombre;
    private final List<Integer> equipoIds = new ArrayList<>();

    public InstitucionAcumulador(EntregaDestinoKey key, String nombre) {
        this.key = key;
        this.nombre = nombre;
    }

    public void agregarEquipo(Integer equipoId) {
        if (equipoId != null && !equipoIds.contains(equipoId)) {
            equipoIds.add(equipoId);
        }
    }

    public EntregaDestinoKey getKey() { return key; }
    public String getNombre()         { return nombre; }
    public int getEquiposCount()      { return equipoIds.size(); }
}
