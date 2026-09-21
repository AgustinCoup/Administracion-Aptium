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
    private final TipoLavado tipoLavado;
    private final JabonCatalogo jabon;
    private final BigDecimal litrosJabon;
    private final List<InsumoCatalogo> insumos;
    private final LocalDateTime fechaInicio;
    private final LocalDateTime fechaFin;
    private final List<ElementoCicloItem> materiales;

    public CicloLavadero(int id, int lavarropasNumero, TipoLavado tipoLavado, JabonCatalogo jabon,
                         BigDecimal litrosJabon, List<InsumoCatalogo> insumos,
                         LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        this(id, lavarropasNumero, tipoLavado, jabon, litrosJabon, insumos,
             fechaInicio, fechaFin, new ArrayList<>());
    }

    /**
     * @param insumos insumos extra del ciclo; opcionales, {@code null} se normaliza a lista vacía
     */
    public CicloLavadero(int id, int lavarropasNumero, TipoLavado tipoLavado, JabonCatalogo jabon,
                         BigDecimal litrosJabon, List<InsumoCatalogo> insumos,
                         LocalDateTime fechaInicio, LocalDateTime fechaFin,
                         List<ElementoCicloItem> materiales) {
        this.id               = id;
        this.lavarropasNumero = lavarropasNumero;
        this.tipoLavado       = tipoLavado;
        this.jabon            = jabon;
        this.litrosJabon      = litrosJabon;
        this.insumos          = insumos == null ? List.of() : List.copyOf(insumos);
        this.fechaInicio      = fechaInicio;
        this.fechaFin         = fechaFin;
        this.materiales       = new ArrayList<>(materiales);
    }

    public int getId()                     { return id; }
    public int getLavarropasNumero()       { return lavarropasNumero; }
    public TipoLavado getTipoLavado()      { return tipoLavado; }
    public JabonCatalogo getJabon()        { return jabon; }
    public BigDecimal getLitrosJabon()     { return litrosJabon; }
    public LocalDateTime getFechaInicio()  { return fechaInicio; }
    public LocalDateTime getFechaFin()     { return fechaFin; }

    /** Derivado de {@code fechaFin}: no hay un estado almacenado que pueda contradecirlo. */
    public String getEstado() {
        return estaActivo() ? ESTADO_ACTIVO : ESTADO_FINALIZADO;
    }

    public List<ElementoCicloItem> getMateriales() {
        return new ArrayList<>(materiales);
    }

    /** Insumos extra del ciclo; nunca {@code null}, vacía si el ciclo no lleva ninguno. */
    public List<InsumoCatalogo> getInsumos() {
        return List.copyOf(insumos);
    }

    public boolean estaActivo() {
        return fechaFin == null;
    }
}
