package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.HistorialLavaderoDAO;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.LineaHistorial;

import java.util.List;

/**
 * Capa de validación sobre {@link HistorialLavaderoDAO}. Sólo valida y delega, igual que
 * {@link SalidaLavaderoService}: cero JDBC acá.
 *
 * <p>El historial es de sólo lectura. {@link #obtenerHistorial()} trae la tabla maestra de
 * ingresos para filtrar en memoria; {@link #obtenerDetalle(int)} lee la trazabilidad de un
 * solo ingreso, bajo demanda.</p>
 */
public class HistorialLavaderoService {

    private final HistorialLavaderoDAO dao;

    public HistorialLavaderoService(HistorialLavaderoDAO dao) {
        if (dao == null) throw new IllegalArgumentException("HistorialLavaderoDAO no puede ser nulo");
        this.dao = dao;
    }

    /** Todos los ingresos, del más reciente al más viejo. El filtrado es en memoria. */
    public List<IngresoHistorial> obtenerHistorial() {
        return dao.obtenerHistorial();
    }

    /** Trazabilidad completa de un ingreso. */
    public List<LineaHistorial> obtenerDetalle(int ingresoId) {
        ValidationException.builder()
            .addErrorIf(ingresoId <= 0, "El id del ingreso tiene que ser mayor que cero.")
            .throwIfHasErrors();
        return dao.findDetalle(ingresoId);
    }
}
