package com.example.features.lavadero.service;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CicloLavaderoService {

    private final CicloLavaderoDAO dao;

    public CicloLavaderoService(CicloLavaderoDAO dao) {
        if (dao == null) throw new IllegalArgumentException("CicloLavaderoDAO no puede ser nulo");
        this.dao = dao;
    }

    public Map<Integer, CicloLavadero> obtenerCiclosActivosPorLavarropas() {
        return dao.obtenerCiclosActivosPorLavarropas();
    }

    public List<ElementoCicloItem> obtenerElementosDisponiblesParaCiclo() {
        return dao.obtenerElementosDisponiblesParaCiclo();
    }

    public void lanzarCiclo(int lavarropasNumero, ConfiguracionCiclo config,
                             List<ElementoCicloMovimiento> movimientos) {
        ValidationException.Builder v = ValidationException.builder();

        v.addErrorIf(lavarropasNumero < 1 || lavarropasNumero > Constantes.Lavadero.CANTIDAD_LAVARROPAS,
            "El número de lavarropas debe estar entre 1 y " + Constantes.Lavadero.CANTIDAD_LAVARROPAS + ".");
        v.addErrorIf(config == null,
            "Debe configurar el ciclo.");
        if (config != null) {
            v.addErrorIf(config.tipoLavado() == null,
                "Debe seleccionar el tipo de lavado.");
            v.addErrorIf(config.jabon() == null,
                "Debe seleccionar un jabón.");
            v.addErrorIf(config.litrosJabon() == null || config.litrosJabon().compareTo(BigDecimal.ZERO) <= 0,
                "Los mililitros de jabón deben ser mayores a cero.");
        }
        v.addErrorIf(movimientos == null || movimientos.isEmpty(),
            "Debe agregar al menos un elemento al ciclo.");

        if (movimientos != null) {
            for (ElementoCicloMovimiento m : movimientos) {
                v.addErrorIf(m.getCantidad() <= 0,
                    "La cantidad de cada elemento debe ser mayor a cero.");
            }
        }

        v.throwIfHasErrors();

        dao.lanzarCiclo(lavarropasNumero, config, movimientos);
    }

    public List<CicloLavadero> obtenerCiclosFinalizados() {
        return dao.obtenerCiclosFinalizados();
    }

    public List<CicloLavadero> obtenerTodosLosCiclos() {
        return dao.obtenerTodosLosCiclos();
    }

    public List<ElementoCicloItem> obtenerElementosDeCiclo(int cicloId) {
        return dao.obtenerElementosDeCiclo(cicloId);
    }

    public void finalizarCiclo(int cicloId) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(cicloId <= 0, "ID de ciclo inválido.");
        v.throwIfHasErrors();
        dao.finalizarCiclo(cicloId);
    }
}
