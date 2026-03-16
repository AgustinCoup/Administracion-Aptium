package com.example.features.equipos.ortopedias.controller.helpers;

import java.util.List;

public class CdeFilterCriteria {

    private final String cliente;
    private final String institucion;
    private final List<String> estados;

    public CdeFilterCriteria(String cliente, String institucion, List<String> estados) {
        this.cliente = cliente;
        this.institucion = institucion;
        this.estados = estados;
    }

    public String getCliente() {
        return cliente;
    }

    public String getInstitucion() {
        return institucion;
    }

    public List<String> getEstados() {
        return estados;
    }
}
