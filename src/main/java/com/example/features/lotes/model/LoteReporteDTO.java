package com.example.features.lotes.model;

/**
 * DTO de solo lectura que mapea exactamente los campos definidos en ReporteLotes.jrxml.
 *
 * Campos del JRXML:
 *   - fechaInicio   → fecha de inicio del lote (dd/MM/yyyy)
 *   - codigoNegocio → id_negocio del lote (ej. "2025-001")
 *   - equipo        → nombre del autoclave
 *   - detalles      → detalles del lote concatenados con " / "
 *
 * JasperReports requiere que los getters sigan el convenio JavaBean
 * (getX() para el campo "x"). Esta clase lo cumple.
 */
public class LoteReporteDTO {

    private final String fechaInicio;
    private final String codigoNegocio;
    private final String equipo;
    private final String detalles;

    public LoteReporteDTO(String fechaInicio,
                          String codigoNegocio,
                          String equipo,
                          String detalles) {
        this.fechaInicio   = fechaInicio   != null ? fechaInicio   : "";
        this.codigoNegocio = codigoNegocio != null ? codigoNegocio : "";
        this.equipo        = equipo        != null ? equipo        : "";
        this.detalles      = detalles      != null ? detalles      : "";
    }

    // ── Getters requeridos por JasperReports (JavaBean) ───────────────────────

    public String getFechaInicio()   { return fechaInicio; }
    public String getCodigoNegocio() { return codigoNegocio; }
    public String getEquipo()        { return equipo; }
    public String getDetalles()      { return detalles; }
}