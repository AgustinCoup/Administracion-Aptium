package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.IngresoLavaderoDAO;
import com.example.features.lavadero.model.BolsaLavadero;
import com.example.features.lavadero.model.IngresoLavadero;
import com.example.features.lavadero.model.IngresoLavaderoResumen;

import java.math.BigDecimal;
import java.util.List;

public class LavaderoService {

    private final IngresoLavaderoDAO dao;

    public LavaderoService(IngresoLavaderoDAO dao) {
        if (dao == null) throw new IllegalArgumentException("dao no puede ser nulo");
        this.dao = dao;
    }

    public boolean registrarIngreso(IngresoLavadero ingreso) {
        ValidationException.Builder v = ValidationException.builder();

        v.addErrorIf(ingreso.getClienteId() <= 0,
            "Debe seleccionar un cliente.");
        v.addErrorIf(ingreso.getBolsas().isEmpty(),
            "Debe agregar al menos una bolsa.");

        for (BolsaLavadero bolsa : ingreso.getBolsas()) {
            v.addErrorIf(bolsa.getPesoKg() == null || bolsa.getPesoKg().compareTo(BigDecimal.ZERO) <= 0,
                "El peso de cada bolsa debe ser mayor a cero.");
        }

        v.throwIfHasErrors();

        return dao.guardar(ingreso);
    }

    public List<IngresoLavaderoResumen> obtenerIngresosSinClasificar() {
        return dao.findSinClasificar();
    }
}
