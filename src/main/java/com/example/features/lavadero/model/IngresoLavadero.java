package com.example.features.lavadero.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IngresoLavadero {

    private Integer       id;
    private int           clienteId;
    private LocalDateTime fechaIngreso;
    private final List<BolsaLavadero> bolsas = new ArrayList<>();

    public Integer       getId()                          { return id; }
    public void          setId(Integer id)                { this.id = id; }

    public int           getClienteId()                   { return clienteId; }
    public void          setClienteId(int clienteId)      { this.clienteId = clienteId; }

    public LocalDateTime getFechaIngreso()                { return fechaIngreso; }
    public void          setFechaIngreso(LocalDateTime f) { this.fechaIngreso = f; }

    public List<BolsaLavadero> getBolsas()                { return Collections.unmodifiableList(bolsas); }

    public void agregarBolsa(BolsaLavadero bolsa) {
        bolsas.add(bolsa);
    }

    public BigDecimal getPesoTotal() {
        return bolsas.stream()
            .map(BolsaLavadero::getPesoKg)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
