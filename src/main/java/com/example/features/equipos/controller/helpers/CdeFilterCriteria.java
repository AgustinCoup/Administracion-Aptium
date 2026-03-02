package com.example.features.equipos.controller.helpers;

public class CdeFilterCriteria {

    private final String cliente;
    private final String institucion;
    private final String estado;

    public CdeFilterCriteria(String cliente, String institucion, String estado) {
        this.cliente = cliente;
        this.institucion = institucion;
        this.estado = estado;
    }

    public String getCliente() {
        return cliente;
    }

    public String getInstitucion() {
        return institucion;
    }

    public String getEstado() {
        return estado;
    }
}
