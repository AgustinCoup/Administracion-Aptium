package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.TipoJabon;

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

    public void lanzarCiclo(int lavarropasNumero, TipoJabon tipoJabon, BigDecimal litrosJabon,
                             boolean suavizante, BigDecimal litrosTotales,
                             List<ElementoCicloMovimiento> movimientos) {
        ValidationException.Builder v = ValidationException.builder();

        v.addErrorIf(lavarropasNumero < 1 || lavarropasNumero > 13,
            "El número de lavarropas debe estar entre 1 y 13.");
        v.addErrorIf(tipoJabon == null,
            "Debe seleccionar un tipo de jabón.");
        v.addErrorIf(litrosJabon == null || litrosJabon.compareTo(BigDecimal.ZERO) <= 0,
            "Los litros de jabón deben ser mayores a cero.");
        v.addErrorIf(movimientos == null || movimientos.isEmpty(),
            "Debe agregar al menos un elemento al ciclo.");

        if (movimientos != null) {
            for (ElementoCicloMovimiento m : movimientos) {
                v.addErrorIf(m.getCantidad() <= 0,
                    "La cantidad de cada elemento debe ser mayor a cero.");
            }
        }

        v.throwIfHasErrors();

        dao.lanzarCiclo(lavarropasNumero, tipoJabon, litrosJabon, suavizante, litrosTotales, movimientos);
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
