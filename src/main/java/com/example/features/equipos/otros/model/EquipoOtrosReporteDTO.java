package com.example.features.equipos.otros.model;

/**
 * DTO para el reporte de equipos_otros. Cada instancia representa una fila.
 * Los campos son Strings para compatibilidad directa con $F{...} en el JRXML, salvo
 * {@code litros}: es numérico porque el reporte lo suma ({@code $V{totalLitros}}), y el
 * "—" de un ingreso sin litros lo pinta el JRXML, no este DTO.
 */
public class EquipoOtrosReporteDTO {

    private final String fechaIngreso;
    private final String cliente;
    private final String tipoIngreso;
    private final String materiales;
    private final String lotesEsterilizacion;
    private final int    litros;

    public EquipoOtrosReporteDTO(String fechaIngreso, String cliente,
                                 String tipoIngreso, String materiales,
                                 String lotesEsterilizacion, int litros) {
        this.fechaIngreso        = fechaIngreso;
        this.cliente             = cliente;
        this.tipoIngreso         = tipoIngreso;
        this.materiales          = materiales;
        this.lotesEsterilizacion = lotesEsterilizacion;
        this.litros              = litros;
    }

    public String getFechaIngreso()        { return fechaIngreso; }
    public String getCliente()             { return cliente; }
    public String getTipoIngreso()         { return tipoIngreso; }
    public String getMateriales()          { return materiales; }
    public String getLotesEsterilizacion() { return lotesEsterilizacion; }
    public int    getLitros()              { return litros; }
}
