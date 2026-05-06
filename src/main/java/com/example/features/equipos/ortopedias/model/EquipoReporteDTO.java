package com.example.features.equipos.ortopedias.model;

/**
 * DTO para el reporte de ortopedias. Cada instancia representa una fila.
 * Los campos son Strings para compatibilidad directa con $F{...} en el JRXML.
 */
public class EquipoReporteDTO {

    private final String fechaIngreso;
    private final String profesional;
    private final String paciente;
    private final String institucion;
    private final String materiales;

    public EquipoReporteDTO(String fechaIngreso, String profesional,
                            String paciente, String institucion, String materiales) {
        this.fechaIngreso = fechaIngreso;
        this.profesional  = profesional;
        this.paciente     = paciente;
        this.institucion  = institucion;
        this.materiales   = materiales;
    }

    public String getFechaIngreso() { return fechaIngreso; }
    public String getProfesional()  { return profesional; }
    public String getPaciente()     { return paciente; }
    public String getInstitucion()  { return institucion; }
    public String getMateriales()   { return materiales; }
}
