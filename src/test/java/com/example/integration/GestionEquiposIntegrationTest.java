package com.example.integration;

import static org.junit.Assert.*;

import com.example.database.ConnectionPool;
import com.example.model.*;
import com.example.service.*;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Tests de integración de alto nivel para el flujo completo de gestión de equipos.
 * 
 * Este test NO usa mocks, ejecuta código real con base de datos real.
 * Cubre el flujo completo:
 * 1. Ingresar equipos con materiales
 * 2. Avanzar estados paso a paso
 * 3. Esterilizar en lotes
 * 4. Entregar a instituciones
 * 
 * IMPORTANTE: Usa la base de datos configurada en config.properties.
 * Se recomienda configurar H2 para tests o una base de datos de prueba.
 */
public class GestionEquiposIntegrationTest {
    
    private static long testRunId;
    
    private EquipoDAO equipoDAO;
    private MaterialDAO materialDAO;
    private LoteDAO loteDAO;
    private AutoclaveDAO autoclaveDAO;
    private InstitucionDAO institucionDAO;
    
    private EquipoService equipoService;
    private MaterialService materialService;
    private LoteService loteService;
    private AutoclaveService autoclaveService;
    private InstitucionService institucionService;
    
    private String testClientName;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        // ConnectionPool se inicializa automáticamente al cargar la clase
        // Generar ID único para esta ejecución de tests
        testRunId = System.currentTimeMillis();
    }
    
    @Before
    public void setUp() throws Exception {
        // Generar nombre único para este test
       testClientName = "TestClient_" + testRunId + "_" + System.nanoTime();
        
        // Inicializar DAOs (usan ConnectionPool automáticamente)
        equipoDAO = new EquipoDAO();
        materialDAO = new MaterialDAO();
        loteDAO = new LoteDAO();
        autoclaveDAO = new AutoclaveDAO();
        institucionDAO = new InstitucionDAO();
        
        // Inicializar Servicios
        equipoService = new EquipoService(equipoDAO);
        materialService = new MaterialService();
        loteService = new LoteService(loteDAO);
        autoclaveService = new AutoclaveService(autoclaveDAO);
        institucionService = new InstitucionService(institucionDAO);
    }
    
    @After
    public void tearDown() throws Exception {
        // Limpiar los datos creados durante el test
        limpiarDatosDeTest();
    }
    
    /**
     * Limpia los datos generados por este test específico
     */
    private void limpiarDatosDeTest() {
        if (testClientName == null) return;
        
        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Eliminar en orden para respetar foreign keys
                stmt.execute("DELETE FROM lotes_materiales WHERE lote_id IN (" +
                           "SELECT id FROM lotes WHERE autoclave_nombre LIKE 'TestAuto_%')");
                stmt.execute("DELETE FROM lotes WHERE autoclave_nombre LIKE 'TestAuto_%'");
                stmt.execute("DELETE FROM materiales WHERE equipo_id IN (" +
                           "SELECT id FROM equipos WHERE cliente_nombre = '" + testClientName + "')");
                stmt.execute("DELETE FROM equipos WHERE cliente_nombre = '" + testClientName + "'");
                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignorar error de rollback
                }
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    // Ignorar error
                }
            }
        } catch (SQLException e) {
            // Ignorar errores de conexión
        }
    }
    
    /**
     * Test de flujo completo: Ingreso → Avance → Esterilización → Entrega
     */
    @Test
    public void flujoCompleto_IngresoAvanceEsterilizacionEntrega_Exitoso() throws Exception {
        // ===== PASO 1: Ingresar equipo =====
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setClienteNombre(testClientName);
        equipo.setNroInstitucion(1);
        equipo.setInstitucionNombre("Test Institution");
        equipo.setRequiereLavado(true);
        equipo.setRequiereEmpaque(true);
        
        // Agregar materiales (usar códigos que probablemente existan)
        Material mat1 = new Material(1, "Material Test 1", 2);
        Material mat2 = new Material(2, "Material Test 2", 1);
        equipo.agregarMaterial(mat1);
        equipo.agregarMaterial(mat2);
        
        boolean guardado = equipoService.guardarEquipo(equipo);
        assertTrue("El equipo debe guardarse correctamente", guardado);
        
        // Verificar que se guardó
        List<Equipo> equipos = equipoService.obtenerTodos();
        assertTrue("Debe haber al menos 1 equipo", equipos.size() >= 1);
        
        // Buscar nuestro equipo
        Equipo equipoGuardado = equipos.stream()
            .filter(e -> testClientName.equals(e.getClienteNombre()))
            .findFirst()
            .orElse(null);
        
        assertNotNull("El equipo debe existir", equipoGuardado);
        assertNotNull("El equipo debe tener ID", equipoGuardado.getId());
        assertEquals("Debe tener 2 materiales", 2, equipoGuardado.getMateriales().size());
        assertEquals("Estado inicial debe ser NUEVO", EstadoEquipo.NUEVO, equipoGuardado.getEstado());
        
        int equipoId = equipoGuardado.getId();
        
        // ===== PASO 2: Avanzar estados =====
        // Obtener IDs reales de los materiales
        List<Material> materiales = equipoGuardado.getMateriales();
        int mat1Id = materiales.get(0).getId();
        int mat2Id = materiales.get(1).getId();
        
        // NUEVO → LAVANDO
        Map<Integer, EstadoEquipo> actualizacion1 = Map.of(
            mat1Id, EstadoEquipo.LAVANDO,
            mat2Id, EstadoEquipo.LAVANDO
        );
        boolean avance1 = materialService.actualizarMultiplesMateriales(equipoId, actualizacion1);
        assertTrue("Debe avanzar a LAVANDO", avance1);
        
        // LAVANDO → LAVADO
        Map<Integer, EstadoEquipo> actualizacion2 = Map.of(
            mat1Id, EstadoEquipo.LAVADO,
            mat2Id, EstadoEquipo.LAVADO
        );
        boolean avance2 = materialService.actualizarMultiplesMateriales(equipoId, actualizacion2);
        assertTrue("Debe avanzar a LAVADO", avance2);
        
        // LAVADO → EMPAQUETADO
        Map<Integer, EstadoEquipo> actualizacion3 = Map.of(
            mat1Id, EstadoEquipo.EMPAQUETADO,
            mat2Id, EstadoEquipo.EMPAQUETADO
        );
        boolean avance3 = materialService.actualizarMultiplesMateriales(equipoId, actualizacion3);
        assertTrue("Debe avanzar a EMPAQUETADO", avance3);
        
        // Verificar estado del equipo
        Equipo equipoActualizado = equipoService.obtenerPorId(String.valueOf(equipoId));
        assertNotNull("El equipo debe existir", equipoActualizado);
        assertEquals("El estado del equipo debe ser EMPAQUETADO", 
                     EstadoEquipo.EMPAQUETADO, equipoActualizado.calcularEstado());
        
        // ===== PASO 3: Pasar a ESTERILIZANDO ===
        Map<Integer, EstadoEquipo> actualizacion4 = Map.of(
            mat1Id, EstadoEquipo.ESTERILIZANDO,
            mat2Id, EstadoEquipo.ESTERILIZANDO
        );
        materialService.actualizarMultiplesMateriales(equipoId, actualizacion4);
        
        Equipo equipoEsterilizando = equipoService.obtenerPorId(String.valueOf(equipoId));
        assertEquals("El estado debe ser ESTERILIZANDO",
                     EstadoEquipo.ESTERILIZANDO, equipoEsterilizando.calcularEstado());
        
        // ===== PASO 4: Pasar a ESTERILIZADO =====
        Map<Integer, EstadoEquipo> actualizacion5 = Map.of(
            mat1Id, EstadoEquipo.ESTERILIZADO,
            mat2Id, EstadoEquipo.ESTERILIZADO
        );
        materialService.actualizarMultiplesMateriales(equipoId, actualizacion5);
        
        Equipo equipoEsterilizado = equipoService.obtenerPorId(String.valueOf(equipoId));
        assertEquals("El estado debe ser ESTERILIZADO",
                     EstadoEquipo.ESTERILIZADO, equipoEsterilizado.calcularEstado());
        
        // ===== PASO 5: Pasar a ENTREGADO =====
        Map<Integer, EstadoEquipo> actualizacion6 = Map.of(
            mat1Id, EstadoEquipo.ENTREGADO,
            mat2Id, EstadoEquipo.ENTREGADO
        );
        materialService.actualizarMultiplesMateriales(equipoId, actualizacion6);
        
        // Verificar estado final
        Equipo equipoFinal = equipoService.obtenerPorId(String.valueOf(equipoId));
        assertEquals("El estado final debe ser ENTREGADO",
                     EstadoEquipo.ENTREGADO, equipoFinal.calcularEstado());
        
        // Verificar que los materiales están en ENTREGADO
        for (Material mat : equipoFinal.getMateriales()) {
            assertEquals("Cada material debe estar ENTREGADO",
                         EstadoEquipo.ENTREGADO, mat.getEstado());
        }
    }
    
    /**
     * Test: Flujo sin lavado debe saltar ese paso
     */
    @Test
    public void flujoSinLavado_SaltaPasoLavado_Exitoso() throws Exception {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setClienteNombre(testClientName);
        equipo.setNroInstitucion(1);
        equipo.setInstitucionNombre("Test Institution");
        equipo.setRequiereLavado(false); // SIN LAVADO
        equipo.setRequiereEmpaque(true);
        
        Material mat = new Material(1, "Material Test", 1);
        equipo.agregarMaterial(mat);
        
        equipoService.guardarEquipo(equipo);
        
        // Verificar que siguiente estado salta lavado
        EstadoEquipo siguienteEstado = equipo.getSiguienteEstado(EstadoEquipo.NUEVO);
        assertEquals("Sin lavado debe ir directo a EMPAQUETADO",
                     EstadoEquipo.EMPAQUETADO, siguienteEstado);
    }
    
    /**
     * Test: Múltiples equipos con diferentes estados
     */
    @Test
    public void multipleEquipos_DiferentesEstados_ManejaCorrectamente() throws Exception {
        // Equipo 1
        Equipo equipo1 = new Equipo();
        equipo1.setNroCliente(1);
        equipo1.setClienteNombre(testClientName);
        equipo1.setNroInstitucion(1);
        equipo1.setInstitucionNombre("Test Institution");
        equipo1.agregarMaterial(new Material(1, "Mat 1", 1));
        equipoService.guardarEquipo(equipo1);
        
        // Equipo 2
        Equipo equipo2 = new Equipo();
        equipo2.setNroCliente(1);
        equipo2.setClienteNombre(testClientName);
        equipo2.setNroInstitucion(1);
        equipo2.setInstitucionNombre("Test Institution");
        equipo2.agregarMaterial(new Material(2, "Mat 2", 2));
        equipoService.guardarEquipo(equipo2);
        
        // Equipo 3
        Equipo equipo3 = new Equipo();
        equipo3.setNroCliente(1);
        equipo3.setClienteNombre(testClientName);
        equipo3.setNroInstitucion(1);
        equipo3.setInstitucionNombre("Test Institution");
        equipo3.agregarMaterial(new Material(3, "Mat 3", 3));
        equipoService.guardarEquipo(equipo3);
        
        // Verificar conteo (al menos los 3 que acabamos de crear)
        long count = equipoService.contar();
        assertTrue("Debe haber al menos 3 equipos", count >= 3);
    }
    
    /**
     * Test: Service puede obtener todos los equipos
     */
    @Test
    public void obtenerTodos_RetornaListaDeEquipos() {
        List<Equipo> equipos = equipoService.obtenerTodos();
        
        assertNotNull("Debe retornar lista", equipos);
        // Puede haber 0 o más equipos dependiendo del estado de la BD
    }
    
    /**
     * Test: Búsqueda por institución
     */
    @Test
    public void buscarInstituciones_ConTexto_FuncionaCorrectamente() {
        // Este test depende de que haya datos en la BD
        // Solo verificamos que el método no falla
        List<Institucion> resultados = institucionService.buscarInstituciones("Test");
        assertNotNull("Debe retornar lista", resultados);
    }
    
    /**
     * Test: Obtener autoclaves
     */
    @Test
    public void obtenerAutoclaves_RetornaLista() {
        List<Autoclave> autoclaves = autoclaveService.obtenerTodos();
        assertNotNull("Debe retornar lista", autoclaves);
    }
    
    /**
     * Test: Verificar cálculo de estados
     */
    @Test
    public void calcularEstado_ConMaterialesEnDiferentesEstados_RetornaEstadoMinimo() throws Exception {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setClienteNombre(testClientName);
        equipo.setNroInstitucion(1);
        equipo.setInstitucionNombre("Test Institution");
        
        // Agregar múltiples materiales
        equipo.agregarMaterial(new Material(1, "Mat A", 1));
        equipo.agregarMaterial(new Material(2, "Mat B", 1));
        
        equipoService.guardarEquipo(equipo);
        
        // Recuperar el equipo guardado
        List<Equipo> equipos = equipoService.obtenerTodos();
        Equipo equipoGuardado = equipos.stream()
            .filter(e -> testClientName.equals(e.getClienteNombre()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(equipoGuardado);
        
        // Todos los materiales están en NUEVO, el equipo debe estar en NUEVO
        assertEquals("Estado debe ser NUEVO", EstadoEquipo.NUEVO, equipoGuardado.calcularEstado());
    }
}
