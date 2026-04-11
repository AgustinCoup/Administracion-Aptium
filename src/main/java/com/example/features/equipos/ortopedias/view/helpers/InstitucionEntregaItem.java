package com.example.features.equipos.ortopedias.view.helpers;

import com.example.common.model.EntregaDestinoKey;

public class InstitucionEntregaItem {
    private final EntregaDestinoKey key;
    private final String nombre;
    private final int equiposCount;

    public InstitucionEntregaItem(EntregaDestinoKey key, String nombre, int equiposCount) {
        this.key = key;
        this.nombre = nombre;
        this.equiposCount = equiposCount;
    }

    public EntregaDestinoKey getKey()  { return key; }
    public String getNombre()          { return nombre; }
    public int getEquiposCount()       { return equiposCount; }
}
