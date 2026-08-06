package com.example.features.lavadero.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CicloLavadero {

    public static final String ESTADO_ACTIVO     = "ACTIVO";
    public static final String ESTADO_FINALIZADO = "FINALIZADO";

    private final int id;
    private final int lavarropasNumero;
    private final JabonCatalogo jabon;
    private final BigDecimal litrosJabon;
    private final boolean suavizante;
    private final boolean potenciador;
    private final BigDecimal litrosTotales;
    private final LocalDateTime fechaInicio;
    private final LocalDateTime fechaFin;
    private final List<ElementoCicloItem> materiales;

    public CicloLavadero(int id, int lavarropasNumero, JabonCatalogo jabon,
                         BigDecimal litrosJabon, boolean suavizante, boolean potenciador,
                         BigDecimal litrosTotales,
                         LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        this(id, lavarropasNumero, jabon, litrosJabon, suavizante, potenciador, litrosTotales,
             fechaInicio, fechaFin, new ArrayList<>());
    }

    public CicloLavadero(int id, int lavarropasNumero, JabonCatalogo jabon,
                         BigDecimal litrosJabon, boolean suavizante, boolean potenciador,
                         BigDecimal litrosTotales,
                         LocalDateTime fechaInicio, LocalDateTime fechaFin,
                         List<ElementoCicloItem> materiales) {
        this.id               = id;
        this.lavarropasNumero = lavarropasNumero;
        this.jabon            = jabon;
        this.litrosJabon      = litrosJabon;
        this.suavizante       = suavizante;
        this.potenciador      = potenciador;
        this.litrosTotales    = litrosTotales;
        this.fechaInicio      = fechaInicio;
        this.fechaFin         = fechaFin;
        this.materiales       = new ArrayList<>(materiales);
    }

    public int getId()                     { return id; }
    public int getLavarropasNumero()       { return lavarropasNumero; }
    public JabonCatalogo getJabon()        { return jabon; }
    public BigDecimal getLitrosJabon()     { return litrosJabon; }
    public boolean isSuavizante()          { return suavizante; }
    public boolean isPotenciador()         { return potenciador; }
    public BigDecimal getLitrosTotales()   { return litrosTotales; }
    public LocalDateTime getFechaInicio()  { return fechaInicio; }
    public LocalDateTime getFechaFin()     { return fechaFin; }

    /** Derivado de {@code fechaFin}: no hay un estado almacenado que pueda contradecirlo. */
    public String getEstado() {
        return estaActivo() ? ESTADO_ACTIVO : ESTADO_FINALIZADO;
    }

    public List<ElementoCicloItem> getMateriales() {
        return new ArrayList<>(materiales);
    }

    public boolean estaActivo() {
        return fechaFin == null;
    }
}
