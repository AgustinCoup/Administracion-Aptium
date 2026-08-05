package com.example.features.lotes.view.helpers;

import java.time.LocalDateTime;

/**
 * Datos del ingreso (equipo de origen) de un material, para mostrar en el tooltip
 * de las tablas de Gestionar Lotes.
 *
 * <p>Value type inmutable, sin lógica ni dependencias de Swing. Se construye con
 * los factory methods {@link #deOrtopedia} y {@link #deOtros}: cada tipo de equipo
 * llena solo los campos que le aplican.</p>
 */
public final class IngresoInfo {

    private final boolean esOtros;
    private final String clienteNombre;
    private final String profesionalNombre;
    private final String pacienteNombre;
    private final String institucionNombre;
    private final boolean esRemito;
    private final String remitoId;
    private final LocalDateTime fechaIngreso;

    private IngresoInfo(boolean esOtros, String clienteNombre, String profesionalNombre,
                        String pacienteNombre, String institucionNombre,
                        boolean esRemito, String remitoId, LocalDateTime fechaIngreso) {
        this.esOtros = esOtros;
        this.clienteNombre = clienteNombre;
        this.profesionalNombre = profesionalNombre;
        this.pacienteNombre = pacienteNombre;
        this.institucionNombre = institucionNombre;
        this.esRemito = esRemito;
        this.remitoId = remitoId;
        this.fechaIngreso = fechaIngreso;
    }

    /** Ingreso de ortopedia: datos médicos completos, sin remito. */
    public static IngresoInfo deOrtopedia(String clienteNombre, String profesionalNombre,
                                          String pacienteNombre, String institucionNombre,
                                          LocalDateTime fechaIngreso) {
        return new IngresoInfo(false, clienteNombre, profesionalNombre, pacienteNombre,
                institucionNombre, false, null, fechaIngreso);
    }

    /** Ingreso de "otros": solo cliente, fecha y —si aplica— el remito. */
    public static IngresoInfo deOtros(String clienteNombre, boolean esRemito,
                                      String remitoId, LocalDateTime fechaIngreso) {
        return new IngresoInfo(true, clienteNombre, null, null, null,
                esRemito, remitoId, fechaIngreso);
    }

    public boolean isEsOtros()             { return esOtros; }
    public String getClienteNombre()       { return clienteNombre; }
    public String getProfesionalNombre()   { return profesionalNombre; }
    public String getPacienteNombre()      { return pacienteNombre; }
    public String getInstitucionNombre()   { return institucionNombre; }
    public boolean isEsRemito()            { return esRemito; }
    public String getRemitoId()            { return remitoId; }
    public LocalDateTime getFechaIngreso() { return fechaIngreso; }
}
