package com.example.app;

import com.example.infrastructure.db.ConnectionPool;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.service.EquipoCorreccionService;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.ortopedias.service.MaterialService;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosCorreccionService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.catalogo.service.CatalogoOtrosService;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.clientes.model.Cliente;
import com.example.features.clientes.service.ClienteService;
import com.example.features.profesionales.model.Profesional;
import com.example.features.profesionales.service.ProfesionalService;
import com.example.features.instituciones.model.Institucion;
import com.example.features.instituciones.service.InstitucionService;
import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.equipos.ortopedias.service.EquipoReporteService;
import com.example.features.equipos.otros.service.EquipoOtrosReporteService;
import com.example.features.lotes.service.LoteReporteService;
import com.example.features.lotes.service.LoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.example.features.autoclaves.model.Autoclave;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;

/**
 * Fachada principal de la aplicación.
 *
 * <p>Responsabilidad única: exponer operaciones de negocio con nombre semántico,
 * delegando la implementación a los servicios correspondientes.
 * Ningún controlador debe referenciar servicios internos directamente.
 *
 * <p>Regla de extensión: cada nueva operación de negocio se agrega aquí como
 * un método de delegación explícito. No exponer servicios internos (get*Service).
 */
public class AppModel {

    private static final Logger log = LoggerFactory.getLogger(AppModel.class);

    private final EquipoService          equipoService;
    private final CatalogoService        catalogoService;
    private final ClienteService         clienteService;
    private final ProfesionalService     profesionalService;
    private final InstitucionService     institucionService;
    private final MaterialService        materialService;
    private final AutoclaveService       autoclaveService;
    private final LoteService            loteService;
    private final CatalogoOtrosService   catalogoOtrosService;
    private final EquipoOtrosService     equipoOtrosService;
    private final EquipoCorreccionService equipoCorreccionService;
    private final EquipoOtrosCorreccionService equipoOtrosCorreccionService;
    private final IEstadoValidator        estadoValidator;
    private final LoteReporteService         loteReporteService;
    private final EquipoReporteService       equipoReporteService;
    private final EquipoOtrosReporteService  equipoOtrosReporteService;

    /**
     * Constructor con inyección de dependencias.
     * Todas las dependencias se resuelven en {@link AppContext}.
     */
    public AppModel(AppContext context) {
        if (context == null) {
            throw new IllegalArgumentException("AppContext no puede ser nulo");
        }
        this.equipoService       = context.getEquipoService();
        this.catalogoService     = context.getCatalogoService();
        this.clienteService      = context.getClienteService();
        this.profesionalService  = context.getProfesionalService();
        this.institucionService  = context.getInstitucionService();
        this.materialService     = context.getMaterialService();
        this.autoclaveService    = context.getAutoclaveService();
        this.loteService         = context.getLoteService();
        this.catalogoOtrosService = context.getCatalogoOtrosService();
        this.equipoOtrosService = context.getEquipoOtrosService();
        this.equipoCorreccionService = context.getEquipoCorreccionService();
        this.equipoOtrosCorreccionService = context.getEquipoOtrosCorreccionService();
        this.estadoValidator           = context.getEstadoValidator();
        this.loteReporteService        = new LoteReporteService(this);
        this.equipoReporteService      = new EquipoReporteService(this);
        this.equipoOtrosReporteService = new EquipoOtrosReporteService(this);
    }

    // ==================== INFRAESTRUCTURA ====================

    /**
     * Verifica que la aplicación pueda abrir una conexión básica.
     */
    public boolean validarConexion() {
        try (Connection conn = ConnectionPool.getConnection()) {
            return conn != null;
        } catch (SQLException e) {
            log.error("Error al validar conexión", e);
            return false;
        }
    }

    // ==================== ESTADO VALIDATOR ====================

    public boolean esAvanzableManualmente(EstadoEquipo estadoActual, EstadoEquipo estadoSiguiente) {
        return estadoValidator.esAvanzableManualmente(estadoActual, estadoSiguiente);
    }

    // ==================== EQUIPOS ====================

    public boolean guardarEquipo(Equipo equipo) {
        return equipoService.guardarEquipo(equipo);
    }

    public List<Equipo> obtenerTodosLosEquipos() {
        return equipoService.obtenerTodos();
    }

    public Equipo obtenerEquipoPorId(String id) {
        return equipoService.obtenerPorId(id);
    }

    public boolean actualizarEquipo(Equipo equipo) {
        return equipoService.actualizar(equipo);
    }

    public long contarEquipos() {
        return equipoService.contar();
    }

    public List<Equipo> obtenerEquiposEntreFechas(LocalDate desde, LocalDate hasta, Integer clienteId, Integer institucionId) {
        return equipoService.obtenerEntreFechas(desde, hasta, clienteId, institucionId);
    }

    // ==================== MATERIALES ====================

    /**
     * Aplica una lista de movimientos de material dentro de una transacción.
     * Cada movimiento avanza una cantidad hacia el siguiente estado de flujo.
     *
     * @param equipoId   ID del equipo al que pertenecen los materiales
     * @param movimientos Lista de movimientos a aplicar
     * @return true si todas las operaciones fueron exitosas
     */
    public boolean aplicarMovimientos(int equipoId, List<MovimientoMaterial> movimientos) {
        return materialService.aplicarMovimientos(equipoId, movimientos);
    }

    /**
     * Marca como entregados todos los materiales pendientes de una institución.
     *
     * @param nroInstitucion ID de la institución a entregar
     * @return true si la operación fue exitosa
     */
    public boolean entregarInstitucionCompleta(int nroInstitucion) {
        return materialService.entregarInstitucionCompleta(nroInstitucion);
    }

    // ==================== CATÁLOGO ====================

    public Map<Integer, String> obtenerCatalogo() {
        return catalogoService.obtenerCatalogo();
    }

    public String obtenerDescripcionMaterial(int codigo) {
        return catalogoService.obtenerDescripcion(codigo);
    }

    public Map<Integer, Integer> obtenerVolumenesCatalogo() {
        return catalogoService.obtenerVolumenes();
    }

    // ==================== AUTOCLAVES ====================

    public List<Autoclave> obtenerAutoclaves() {
        return autoclaveService.obtenerTodos();
    }

    // ==================== LOTES ====================

    public Map<String, Lote> obtenerLotesActivosPorAutoclave() {
        return loteService.obtenerLotesActivosPorAutoclave();
    }

    public List<Lote> obtenerLotesFinalizados() {
        return loteService.obtenerLotesFinalizados();
    }

    public List<Lote> obtenerTodosLosLotes() {
        return loteService.obtenerTodosLosLotes();
    }

    public List<LoteMaterialInfo> obtenerMaterialesPorLote(int loteId) {
        return loteService.obtenerMaterialesPorLote(loteId);
    }

    public Lote lanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                           List<LoteMovimiento> movimientos,
                           Map<Integer, Integer> volumenesPorIngreso) {
        return loteService.lanzarLote(autoclaveNombre, capacidadTotal, capacidadUsada,
            movimientos, volumenesPorIngreso);
    }

    /** Litros declarados por ingreso (equipo_otros) para un lote. */
    public Map<Integer, Integer> obtenerVolumenesPorLote(int loteId) {
        return loteService.obtenerVolumenesPorLote(loteId);
    }

    public boolean finalizarLote(int loteId) {
        return loteService.finalizarLote(loteId);
    }

    public boolean marcarLoteFallo(int loteId) {
        return loteService.marcarLoteFallo(loteId);
    }

    public List<Lote> obtenerLotesEnRango(LocalDate desde, LocalDate hasta) {
        return loteService.obtenerLotesEnRango(desde, hasta);
    }

    public List<String> obtenerClientesPorLote(int loteId) {
        return loteService.obtenerClientesPorLote(loteId);
    }

    public Map<String, List<String>> obtenerMaterialesPorClientePorLote(int loteId) {
        return loteService.obtenerMaterialesPorClientePorLote(loteId);
    }

    public Map<String, List<String>> obtenerOtrosPorClientePorLote(int loteId) {
        return loteService.obtenerOtrosPorClientePorLote(loteId);
    }

    // ==================== CLIENTES ====================

    public List<Cliente> buscarClientes(String substring) {
        return clienteService.buscarClientes(substring);
    }

    public Cliente obtenerClientePorId(int id) {
        return clienteService.obtenerClientePorId(id);
    }

    public boolean guardarCliente(Cliente cliente) {
        return clienteService.guardarCliente(cliente);
    }

    public List<Cliente> obtenerTodosLosClientes() {
        return clienteService.obtenerTodosLosClientes();
    }

    public void eliminarCliente(int id) {
        clienteService.eliminarCliente(id);
    }

    public void fusionarClientes(int idOrigen, int idDestino) {
        clienteService.fusionarClientes(idOrigen, idDestino);
    }

    // ==================== PROFESIONALES ====================

    public List<Profesional> buscarProfesionales(String nombre) {
        return profesionalService.buscarProfesionales(nombre);
    }

    public Profesional obtenerProfesionalPorId(int id) {
        return profesionalService.obtenerProfesionalPorId(id);
    }

    public boolean guardarProfesional(Profesional profesional) {
        return profesionalService.guardarProfesional(profesional);
    }

    // ==================== INSTITUCIONES ====================

    public List<Institucion> buscarInstituciones(String nombre) {
        return institucionService.buscarInstituciones(nombre);
    }

    public Institucion obtenerInstitucionPorId(int id) {
        return institucionService.obtenerInstitucionPorId(id);
    }

    public boolean guardarInstitucion(Institucion institucion) {
        return institucionService.guardarInstitucion(institucion);
    }

    // ==================== EQUIPOS OTROS ====================

    public List<EquipoOtros> obtenerTodosLosEquiposOtros() {
        return equipoOtrosService.obtenerTodos();
    }

    public EquipoOtros obtenerEquipoOtrosPorId(int id) {
        return equipoOtrosService.obtenerPorId(id);
    }

    public boolean guardarEquipoOtros(EquipoOtros equipo) {
        return equipoOtrosService.guardarEquipo(equipo);
    }

    public boolean aplicarMovimientosOtros(int equipoId, List<MovimientoMaterial> movimientos) {
        return equipoOtrosService.aplicarMovimientos(equipoId, movimientos);
    }

    public boolean entregarClienteOtrosCompleto(int nroCliente) {
        return equipoOtrosService.entregarClienteCompleto(nroCliente);
    }

    public List<EquipoOtros> obtenerEquiposOtrosEntreFechas(LocalDate desde, LocalDate hasta, Integer clienteId) {
        return equipoOtrosService.obtenerEntreFechas(desde, hasta, clienteId);
    }

    // ==================== CATÁLOGO OTROS ====================

    public List<String> buscarMaterialesOtrosPorDescripcion(String texto) {
        return catalogoOtrosService.buscarPorDescripcionParcial(texto);
    }

    public boolean existeMaterialOtros(String descripcion) {
        return catalogoOtrosService.existeDescripcion(descripcion);
    }

    // ==================== CORRECCIONES ====================

    /**
     * Expone el servicio completo porque CorreccionsController lo necesita como
     * objeto para inicializar PantallaAuditoria. Excepción reconocida al patrón facade.
     */
    public EquipoCorreccionService getEquipoCorreccionService() {
        return equipoCorreccionService;
    }

    public EquipoOtrosCorreccionService getEquipoOtrosCorreccionService() {
        return equipoOtrosCorreccionService;
    }

    public LoteReporteService getLoteReporteService() {
        return loteReporteService;
    }

    public EquipoReporteService getEquipoReporteService() {
        return equipoReporteService;
    }

    public EquipoOtrosReporteService getEquipoOtrosReporteService() {
        return equipoOtrosReporteService;
    }
}


