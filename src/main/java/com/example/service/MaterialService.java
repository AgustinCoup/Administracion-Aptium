package com.example.service;

import com.example.model.EstadoEquipo;
import com.example.model.MaterialDAO;
import com.example.util.Logger;
import java.util.Map;

/**
 * Servicio de negocio para operaciones con materiales.
 * Encapsula el acceso a datos en MaterialDAO.
 */
public class MaterialService {

    private MaterialDAO materialDAO;

    public MaterialService() {
        this.materialDAO = new MaterialDAO();
    }

    /**
     * Actualiza múltiples materiales de un equipo en una sola transacción.
     * 
     * @param equipoId ID del equipo
     * @param actualizaciones Map con código de material y su nuevo estado
     * @return true si la operación fue exitosa
     */
    public boolean actualizarMultiplesMateriales(int equipoId, Map<Integer, EstadoEquipo> actualizaciones) {
        if (actualizaciones == null || actualizaciones.isEmpty()) {
            return true;
        }

        try {
            return materialDAO.actualizarMultiplesMateriales(equipoId, actualizaciones);
        } catch (Exception e) {
            Logger.error("Error al actualizar múltiples materiales", e);
            return false;
        }
    }
}
