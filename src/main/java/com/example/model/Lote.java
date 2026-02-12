package com.example.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Lote {
    private final int id;
    private final String idNegocio;
    private final int anio;
    private final int secuencia;
    private final String autoclaveNombre;
    private final int capacidadTotal;
    private final int capacidadUsada;
    private final LocalDateTime fechaInicio;
    private final LocalDateTime fechaFin;
    private final List<LoteMaterialInfo> materiales;

    public Lote(int id, String idNegocio, int anio, int secuencia, String autoclaveNombre,
                int capacidadTotal, int capacidadUsada, LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        this(id, idNegocio, anio, secuencia, autoclaveNombre, capacidadTotal, capacidadUsada, fechaInicio, fechaFin, new ArrayList<>());
    }

    public Lote(int id, String idNegocio, int anio, int secuencia, String autoclaveNombre,
                int capacidadTotal, int capacidadUsada, LocalDateTime fechaInicio, LocalDateTime fechaFin,
                List<LoteMaterialInfo> materiales) {
        this.id = id;
        this.idNegocio = idNegocio;
        this.anio = anio;
        this.secuencia = secuencia;
        this.autoclaveNombre = autoclaveNombre;
        this.capacidadTotal = capacidadTotal;
        this.capacidadUsada = capacidadUsada;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.materiales = new ArrayList<>(materiales);
    }

    public int getId() {
        return id;
    }

    public String getIdNegocio() {
        return idNegocio;
    }

    public int getAnio() {
        return anio;
    }

    public int getSecuencia() {
        return secuencia;
    }

    public String getAutoclaveNombre() {
        return autoclaveNombre;
    }

    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    public int getCapacidadUsada() {
        return capacidadUsada;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    public boolean estaActivo() {
        return fechaFin == null;
    }

    public List<LoteMaterialInfo> getMateriales() {
        return new ArrayList<>(materiales);
    }

    public void addMaterial(LoteMaterialInfo material) {
        this.materiales.add(material);
    }
}
