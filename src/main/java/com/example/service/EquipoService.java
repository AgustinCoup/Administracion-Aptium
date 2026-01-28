package com.example.service;

import com.example.model.Equipo;
import com.example.model.EquipoDAO;
import com.example.util.Logger;
import java.util.List;

/**
 * Servicio de negocio para operaciones con Equipos.
 * Encapsula toda la lógica relacionada con gestión de equipos.
 * 
 * Esta clase tiene una única responsabilidad: gestionar equipos.
 * Delega el acceso a datos en EquipoDAO.
 */
public class EquipoService {

    private EquipoDAO equipoDAO;

    public EquipoService() {
        this.equipoDAO = new EquipoDAO();
    }

    /**
     * Guarda un nuevo equipo completo con sus materiales en la base de datos.
     * 
     * @param equipo Objeto Equipo con todos los datos a guardar
     * @return true si se guardó correctamente, false si hubo error
     */
    public boolean guardarEquipo(Equipo equipo) {
        if (equipo == null) {
            Logger.warning("Intento de guardar equipo nulo");
            return false;
        }
        
        try {
            boolean resultado = equipoDAO.guardarEquipo(equipo);
            if (resultado) {
                Logger.info("Equipo guardado exitosamente: " + equipo.getClienteNombre());
            } else {
                Logger.error("Error al guardar equipo: " + equipo.getClienteNombre());
            }
            return resultado;
        } catch (Exception e) {
            Logger.error("Excepción durante guardado de equipo", e);
            return false;
        }
    }

    /**
     * Obtiene todos los equipos registrados en la base de datos.
     * 
     * @return Lista de equipos
     */
    public List<Equipo> obtenerTodos() {
        try {
            return equipoDAO.obtenerTodos();
        } catch (Exception e) {
            Logger.error("Error al obtener equipos", e);
            return List.of(); // Retorna lista vacía en caso de error
        }
    }

    /**
     * Obtiene un equipo específico por su ID.
     * 
     * @param id ID único del equipo
     * @return El equipo si existe, null si no
     */
    public Equipo obtenerPorId(String id) {
        try {
            return equipoDAO.obtenerPorId(id);
        } catch (Exception e) {
            Logger.error("Error al obtener equipo con ID: " + id, e);
            return null;
        }
    }

    /**
     * Actualiza el estado de un equipo existente.
     * 
     * @param equipo Equipo con el nuevo estado
     * @return true si se actualizó correctamente
     */
    public boolean actualizar(Equipo equipo) {
        if (equipo == null || equipo.getId() == null) {
            Logger.warning("Intento de actualizar equipo inválido");
            return false;
        }
        
        try {
            boolean resultado = equipoDAO.actualizar(equipo);
            if (resultado) {
                Logger.info("Equipo actualizado: " + equipo.getId());
            }
            return resultado;
        } catch (Exception e) {
            Logger.error("Error al actualizar equipo", e);
            return false;
        }
    }

    /**
     * Obtiene el total de equipos en la base de datos.
     * 
     * @return Cantidad de equipos
     */
    public long contar() {
        try {
            return equipoDAO.contar();
        } catch (Exception e) {
            Logger.error("Error al contar equipos", e);
            return 0;
        }
    }

    /**
     * Verifica si existe un equipo con el ID especificado.
     * 
     * @param id ID del equipo
     * @return true si existe
     */
    public boolean existe(String id) {
        try {
            return equipoDAO.existe(id);
        } catch (Exception e) {
            Logger.error("Error al verificar existencia de equipo", e);
            return false;
        }
    }
}
