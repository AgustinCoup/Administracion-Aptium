package com.example.features.equipos.otros.model;

/**
 * DTO para el reporte de equipos_otros. Cada instancia representa una fila.
 * Los campos son Strings para compatibilidad directa con $F{...} en el JRXML.
 */
public class EquipoOtrosReporteDTO {

    private final String fechaIngreso;
    private final String cliente;
    private final String tipoIngreso;
    private final String materiales;
    private final String lotesEsterilizacion;

    public EquipoOtrosReporteDTO(String fechaIngreso, String cliente,
                                 String tipoIngreso, String materiales,
                                 String lotesEsterilizacion) {
        this.fechaIngreso        = fechaIngreso;
        this.cliente             = cliente;
        this.tipoIngreso         = tipoIngreso;
        this.materiales          = materiales;
        this.lotesEsterilizacion = lotesEsterilizacion;
    }

    public String getFechaIngreso()        { return fechaIngreso; }
    public String getCliente()             { return cliente; }
    public String getTipoIngreso()         { return tipoIngreso; }
    public String getMateriales()          { return materiales; }
    public String getLotesEsterilizacion() { return lotesEsterilizacion; }
}
