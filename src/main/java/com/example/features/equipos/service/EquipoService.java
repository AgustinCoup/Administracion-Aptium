package com.example.features.equipos.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.model.Equipo;
import com.example.features.equipos.dao.EquipoDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Servicio de negocio para operaciones con Equipos.
 * Encapsula toda la lógica relacionada con gestión de equipos.
 * 
 * Esta clase tiene una única responsabilidad: gestionar equipos.
 * Delega el acceso a datos en EquipoDAO.
 * 
 * MANEJO DE EXCEPCIONES:
 * - Valida datos antes de llamar al DAO
 * - Propaga DatabaseException del DAO
 * - Lanza ValidationException si los datos son inválidos
 * 
 * DEPENDENCY INJECTION:
 * - Recibe DAO por constructor (permite testing con mocks)
 * - Sin dependencias hardcodeadas
 */
public class EquipoService {

    private static final Logger log = LoggerFactory.getLogger(EquipoService.class);

    private final EquipoDAO equipoDAO;

    /**
     * Constructor con inyección de dependencias.
     * 
     * @param equipoDAO DAO para acceso a datos
     */
    public EquipoService(EquipoDAO equipoDAO) {
        if (equipoDAO == null) {
            throw new IllegalArgumentException("EquipoDAO no puede ser nulo");
        }
        this.equipoDAO = equipoDAO;
    }

    /**
     * Guarda un nuevo equipo completo con sus materiales en la base de datos.
     * 
     * VALIDACIONES:
     * - El equipo no puede ser null
     * - Debe tener al menos un material
     * - Cliente y nombres deben estar completos
     * 
     * @param equipo Objeto Equipo con todos los datos a guardar
     * @return true si se guardó correctamente
     * @throws ValidationException si los datos son inválidos
     * @throws DatabaseException si hay error en la base de datos
     */
    public boolean guardarEquipo(Equipo equipo) {
        // Validaciones de negocio
        ValidationException.Builder validationBuilder = ValidationException.builder();
        
        validationBuilder
            .addErrorIf(equipo == null, "El equipo no puede ser nulo")
            .addErrorIf(equipo != null && equipo.getMateriales().isEmpty(), 
                       "El equipo debe tener al menos un material")
            .addErrorIf(equipo != null && equipo.getClienteNombre() == null || 
                       (equipo != null && equipo.getClienteNombre().trim().isEmpty()), 
                       "El nombre del cliente es obligatorio");
        
        validationBuilder.throwIfHasErrors();
        
        try {
            boolean resultado = equipoDAO.guardarEquipo(equipo);
            if (resultado) {
                log.info("Equipo guardado exitosamente: {}", equipo.getClienteNombre());
            }
            return resultado;
        } catch (DatabaseException e) {
            log.error("Error al guardar equipo: {}", equipo.getClienteNombre(), e);
            throw e; // Re-lanzar para que el Controller lo maneje
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
            log.error("Error al obtener equipos", e);
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
            log.error("Error al obtener equipo con ID: {}", id, e);
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
            log.warn("Intento de actualizar equipo inválido");
            return false;
        }
        
        try {
            boolean resultado = equipoDAO.actualizar(equipo);
            if (resultado) {
                log.info("Equipo actualizado: {}", equipo.getId());
            }
            return resultado;
        } catch (Exception e) {
            log.error("Error al actualizar equipo", e);
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
            log.error("Error al contar equipos", e);
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
            log.error("Error al verificar existencia de equipo", e);
            return false;
        }
    }
}


