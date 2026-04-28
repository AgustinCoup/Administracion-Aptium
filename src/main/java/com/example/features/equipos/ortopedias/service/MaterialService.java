package com.example.features.equipos.ortopedias.service;

import com.example.common.exception.ValidationException;
import com.example.features.equipos.ortopedias.dao.MaterialDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;

import java.util.List;
import java.util.Map;

/**
 * Servicio de negocio para operaciones con materiales.
 * Encapsula el acceso a datos en MaterialDAO.
 */
public class MaterialService {

    private final MaterialDAO materialDAO;

    public MaterialService(MaterialDAO materialDAO) {
        if (materialDAO == null) {
            throw new IllegalArgumentException("MaterialDAO no puede ser nulo");
        }
        this.materialDAO = materialDAO;
    }

    /**
     * Actualiza múltiples materiales de un equipo en una sola transacción.
     * 
     * @param equipoId ID del equipo
     * @param actualizaciones Map con código de material y su nuevo estado
     * @return true si la operación fue exitosa
     */
    public boolean actualizarMultiplesMateriales(int equipoId, Map<Integer, EstadoEquipo> actualizaciones) {
        if (actualizaciones == null || actualizaciones.isEmpty()) return true;
        return materialDAO.actualizarMultiplesMateriales(equipoId, actualizaciones);
    }

    /**
     * Aplica movimientos de subcantidades en lote dentro de una transaccion.
     *
     * @param equipoId ID del equipo
     * @param movimientos Lista de movimientos a aplicar
     * @return true si la operacion fue exitosa
     */
    public boolean aplicarMovimientos(int equipoId, List<MovimientoMaterial> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) return true;

        ValidationException.Builder builder = ValidationException.builder();
        for (MovimientoMaterial mov : movimientos) {
            builder.addErrorIf(mov.getMaterialId() <= 0,
                "ID de material inválido: " + mov.getMaterialId());
            builder.addErrorIf(mov.getCantidad() <= 0,
                "La cantidad a mover debe ser mayor a cero (material " + mov.getMaterialId() + ")");
            builder.addErrorIf(mov.getEstadoDestino() == null,
                "El estado destino no puede ser nulo (material " + mov.getMaterialId() + ")");
        }
        builder.throwIfHasErrors();

        return materialDAO.aplicarMovimientos(equipoId, movimientos);
    }

    /**
     * Marca todos los materiales entregables de una institución como entregados.
     * Solo afecta materiales que estén >= ESTERILIZADO y < ENTREGADO.
     * 
     * @param nroInstitucion Número de institución
     * @return true si la operación fue exitosa
     */
    public boolean entregarInstitucionCompleta(int nroInstitucion) {
        return materialDAO.entregarInstitucionCompleta(nroInstitucion);
    }
}


