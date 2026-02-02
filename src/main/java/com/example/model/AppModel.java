package com.example.model;

import com.example.database.ConnectionPool;
import com.example.service.EquipoService;
import com.example.service.CatalogoService;
import com.example.service.ClienteService;
import com.example.service.ProfesionalService;
import com.example.service.InstitucionService;
import com.example.service.MaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Modelo principal de la aplicación - Coordinador de Servicios.
 * 
 * Responsabilidad: Actúa como fachada que coordina entre los servicios
 * de negocio. No contiene lógica de negocio propia.
 * 
 * Servicios delegados:
 * - EquipoService: Gestión completa del ciclo de vida de equipos
 * - CatalogoService: Gestión del catálogo de materiales
 * - ClienteService: Gestión de búsqueda y consultas de clientes
 * 
 * Arquitectura: Service Layer con inyección de dependencias.
 * Respeta Single Responsibility Principle.
 */
public class AppModel {

    private static final Logger log = LoggerFactory.getLogger(AppModel.class);

    /**
     * Servicio de gestión de equipos ortopédicos.
     */
    private EquipoService equipoService;
    
    /**
     * Servicio de gestión del catálogo de materiales.
     */
    private CatalogoService catalogoService;
    
    /**
     * Servicio de gestión de clientes para autocompletado.
     */
    private ClienteService clienteService;
    
    /**
     * Servicio de gestión de profesionales para autocompletado.
     */
    private ProfesionalService profesionalService;
    
    /**
     * Servicio de gestión de instituciones para autocompletado.
     */
    private InstitucionService institucionService;

    /**
     * Servicio de gestión de materiales.
     */
    private MaterialService materialService;

    /**
     * Constructor con inyección de dependencias.
     * Todos los servicios deben ser inyectados por App.java
     */
    public AppModel(
        EquipoService equipoService,
        CatalogoService catalogoService,
        ClienteService clienteService,
        ProfesionalService profesionalService,
        InstitucionService institucionService
    ) {
        this.equipoService = equipoService;
        this.catalogoService = catalogoService;
        this.clienteService = clienteService;
        this.profesionalService = profesionalService;
        this.institucionService = institucionService;
        this.materialService = new MaterialService();
    }

    /**
     * Verifica que la aplicacion pueda abrir una conexion basica.
     * La inicialización de la BD se hace en App.java con DatabaseInitializer.
     */
    public boolean validarConexion() {
        try (Connection conn = ConnectionPool.getConnection()) {
            return conn != null;
        } catch (SQLException e) {
            log.error("Error al validar conexión", e);
            return false;
        }
    }

    /**
     * Devuelve una nueva conexion para operaciones posteriores.
     */
    public Connection nuevaConexion() throws SQLException {
        return ConnectionPool.getConnection();
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

    // ==================== DELEGACIÓN A CLIENTE SERVICE ====================

    /**
     * Busca clientes cuyo nombre contenga el substring proporcionado.
     * 
     * Utilizado por el componente de autocompletado en el formulario.
     * Requiere mínimo 3 caracteres para ejecutar la búsqueda.
     * 
     * @param substring Texto a buscar en los nombres de clientes
     * @return Lista de clientes que coinciden con la búsqueda
     */
    public List<Cliente> buscarClientes(String substring) {
        return clienteService.buscarClientes(substring);
    }

    /**
     * Obtiene un cliente específico por su identificador.
     * 
     * @param id Identificador único del cliente
     * @return Cliente encontrado, o null si no existe
     */
    public Cliente obtenerClientePorId(int id) {
        return clienteService.obtenerClientePorId(id);
    }

    /**
     * Guarda un nuevo cliente en la base de datos.
     * 
     * @param cliente Cliente a guardar
     * @return true si se guardó exitosamente, false en caso contrario
     */
    public boolean guardarCliente(Cliente cliente) {
        return clienteService.guardarCliente(cliente);
    }

    // ==================== DELEGACIÓN A PROFESIONAL SERVICE ====================

    /**
     * Guarda un nuevo profesional en la base de datos.
     * 
     * @param profesional Profesional a guardar
     * @return true si se guardó exitosamente, false en caso contrario
     */
    public boolean guardarProfesional(Profesional profesional) {
        return profesionalService.guardarProfesional(profesional);
    }

    // ==================== DELEGACIÓN A INSTITUCIÓN SERVICE ====================

    /**
     * Guarda una nueva institución en la base de datos.
     * 
     * @param institucion Institución a guardar
     * @return true si se guardó exitosamente, false en caso contrario
     */
    public boolean guardarInstitucion(Institucion institucion) {
        return institucionService.guardarInstitucion(institucion);
    }

    // ==================== ACCESO DIRECTO A SERVICIOS ====================

    /**
     * Proporciona acceso directo al servicio de equipos.
     * Utilizado para consultas y operaciones especializadas.
     * 
     * @return Servicio de equipos
     */
    public EquipoService getEquipoService() {
        return equipoService;
    }

    /**
     * Proporciona acceso directo al servicio de catálogo.
     * 
     * @return Servicio de catálogo de materiales
     */
    public CatalogoService getCatalogoService() {
        return catalogoService;
    }

    /**
     * Proporciona acceso directo al servicio de clientes.
     * 
     * @return Servicio de búsqueda y gestión de clientes
     */
    public ClienteService getClienteService() {
        return clienteService;
    }

    /**
     * Proporciona acceso directo al servicio de materiales.
     * 
     * @return Servicio de materiales
     */
    public MaterialService getMaterialService() {
        return materialService;
    }
    
    // ==================== DELEGACIÓN A PROFESIONAL SERVICE ====================
    
    /**
     * Busca profesionales cuyo nombre contenga el texto proporcionado.
     * Requiere mínimo 3 caracteres.
     * 
     * @param nombre Texto a buscar
     * @return Lista de profesionales que coinciden con la búsqueda
     */
    public List<Profesional> buscarProfesionales(String nombre) {
        return profesionalService.buscarProfesionales(nombre);
    }
    
    /**
     * Obtiene un profesional por su ID.
     * 
     * @param id Identificador único del profesional
     * @return Profesional encontrado, o null si no existe
     */
    public Profesional obtenerProfesionalPorId(int id) {
        return profesionalService.obtenerProfesionalPorId(id);
    }
    
    /**
     * Proporciona acceso directo al servicio de profesionales.
     * 
     * @return Servicio de búsqueda y gestión de profesionales
     */
    public ProfesionalService getProfesionalService() {
        return profesionalService;
    }
    
    // ==================== DELEGACIÓN A INSTITUCION SERVICE ====================
    
    /**
     * Busca instituciones cuyo nombre contenga el texto proporcionado.
     * Requiere mínimo 3 caracteres.
     * 
     * @param nombre Texto a buscar
     * @return Lista de instituciones que coinciden con la búsqueda
     */
    public List<Institucion> buscarInstituciones(String nombre) {
        return institucionService.buscarInstituciones(nombre);
    }
    
    /**
     * Obtiene una institución por su ID.
     * 
     * @param id Identificador único de la institución
     * @return Institución encontrada, o null si no existe
     */
    public Institucion obtenerInstitucionPorId(int id) {
        return institucionService.obtenerInstitucionPorId(id);
    }
    
    /**
     * Proporciona acceso directo al servicio de instituciones.
     * 
     * @return Servicio de búsqueda y gestión de instituciones
     */
    public InstitucionService getInstitucionService() {
        return institucionService;
    }
}
