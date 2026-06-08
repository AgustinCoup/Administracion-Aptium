package com.example.features.lavadero.model;

import java.math.BigDecimal;

public class BolsaLavadero {

    private Integer    id;
    private int        ingresoId;
    private BigDecimal pesoKg;

    public BolsaLavadero() {}

    public BolsaLavadero(BigDecimal pesoKg) {
        this.pesoKg = pesoKg;
    }

    public Integer    getId()                    { return id; }
    public void       setId(Integer id)          { this.id = id; }

    public int        getIngresoId()             { return ingresoId; }
    public void       setIngresoId(int id)       { this.ingresoId = id; }

    public BigDecimal getPesoKg()                { return pesoKg; }
    public void       setPesoKg(BigDecimal peso) { this.pesoKg = peso; }
}
