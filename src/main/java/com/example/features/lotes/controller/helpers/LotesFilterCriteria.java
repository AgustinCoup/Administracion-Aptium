package com.example.features.lotes.controller.helpers;

public class LotesFilterCriteria {

    private final String id;
    private final String equipo;
    private final String fechaInicio;

    public LotesFilterCriteria(String id, String equipo, String fechaInicio) {
        this.id = id;
        this.equipo = equipo;
        this.fechaInicio = fechaInicio;
    }

    public String getId() {
        return id;
    }

    public String getEquipo() {
        return equipo;
    }

    public String getFechaInicio() {
        return fechaInicio;
    }
}
