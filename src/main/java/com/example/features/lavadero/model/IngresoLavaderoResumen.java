package com.example.features.lavadero.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class IngresoLavaderoResumen {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final int           id;
    private final String        clienteNombre;
    private final LocalDateTime fechaIngreso;
    private final BigDecimal    pesoTotalKg;
    private final int           cantBolsas;

    public IngresoLavaderoResumen(int id, String clienteNombre, LocalDateTime fechaIngreso,
                                  BigDecimal pesoTotalKg, int cantBolsas) {
        this.id            = id;
        this.clienteNombre = clienteNombre;
        this.fechaIngreso  = fechaIngreso;
        this.pesoTotalKg   = pesoTotalKg;
        this.cantBolsas    = cantBolsas;
    }

    public int           getId()            { return id; }
    public String        getClienteNombre() { return clienteNombre; }
    public LocalDateTime getFechaIngreso()  { return fechaIngreso; }
    public BigDecimal    getPesoTotalKg()   { return pesoTotalKg; }
    public int           getCantBolsas()    { return cantBolsas; }

    @Override
    public String toString() {
        String fecha = fechaIngreso != null ? fechaIngreso.format(FMT) : "-";
        return clienteNombre + " - " + fecha + " - " + pesoTotalKg + " kg - " + cantBolsas + " bolsas";
    }
}
