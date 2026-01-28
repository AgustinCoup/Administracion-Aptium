package com.example.model;

import com.example.database.Conexion;
import com.example.service.EquipoService;
import com.example.service.CatalogoService;
import com.example.util.Logger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Modelo principal de la aplicación - Coordinador de Servicios.
 * No contiene lógica de negocio, solo coordina entre servicios.
 * 
 * Delegación de responsabilidades:
 * - EquipoService: Gestión completa de equipos
 * - CatalogoService: Gestión del catálogo de materiales
 * - Conexion: Gestión de conexiones a BD
 * 
 * Esta arquitectura sigue el patrón Service Layer y respeta el SRP (Single Responsibility Principle).
 */
public class AppModel {

    // Servicios de negocio (inyección de dependencias simple)
    private EquipoService equipoService;
    private CatalogoService catalogoService;

    public AppModel() {
        this.equipoService = new EquipoService();
        this.catalogoService = new CatalogoService();
    }

    /**
     * Verifica que la aplicacion pueda abrir una conexion basica.
     * También inicializa la estructura de la base de datos si es necesario.
     */
    public boolean validarConexion() {
        // Primero inicializar la estructura de base de datos (crea BD y tablas si no existen)
        Conexion.inicializarBaseDeDatos();
        
        // Luego verificar que la conexión funcione correctamente
        Connection conn = Conexion.conectar();
        if (conn == null) {
            return false;
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
            // Ignoramos errores al cerrar la prueba de conexion.
        }
        return true;
    }

    /**
     * Devuelve una nueva conexion para operaciones posteriores.
     */
    public Connection nuevaConexion() {
        return Conexion.conectar();
    }

    // ==================== DELEGACIÓN A EQUIPO SERVICE ====================

    /**
     * Guarda un nuevo equipo completo con sus materiales en la base de datos.
     * Delega a EquipoService.
     */
    public boolean guardarEquipo(Equipo equipo) {
        return equipoService.guardarEquipo(equipo);
    }

    /**
     * Obtiene todos los equipos registrados en la base de datos.
     */
    public List<Equipo> obtenerTodosLosEquipos() {
        return equipoService.obtenerTodos();
    }

    /**
     * Obtiene un equipo específico por su ID.
     */
    public Equipo obtenerEquipoPorId(String id) {
        return equipoService.obtenerPorId(id);
    }

    /**
     * Actualiza el estado de un equipo existente.
     */
    public boolean actualizarEquipo(Equipo equipo) {
        return equipoService.actualizar(equipo);
    }

    /**
     * Obtiene el total de equipos en la base de datos.
     */
    public long contarEquipos() {
        return equipoService.contar();
    }

    // ==================== DELEGACIÓN A CATALOGO SERVICE ====================

    /**
     * Obtiene todas las descripciones del catálogo de materiales.
     */
    public Map<Integer, String> obtenerCatalogo() {
        return catalogoService.obtenerCatalogo();
    }

    /**
     * Obtiene la descripción de un material específico.
     */
    public String obtenerDescripcionMaterial(int codigo) {
        return catalogoService.obtenerDescripcion(codigo);
    }

    /**
     * Acceso directo a servicios (para consultas más complejas si es necesario).
     */
    public EquipoService getEquipoService() {
        return equipoService;
    }

    public CatalogoService getCatalogoService() {
        return catalogoService;
    }
}
