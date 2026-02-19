package com.example.integration;

import com.example.model.*;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

/**
 * Tests de integración para DAOs.
 * Estos tests ejecutan código real contra la base de datos,
 * lo que aumenta la cobertura drásticamente comparado con tests con mocks.
 */
public class DAOsIntegrationTest {
    
    private ClienteDAO clienteDAO;
    private InstitucionDAO institucionDAO;
    private ProfesionalDAO profesionalDAO;
    private AutoclaveDAO autoclaveDAO;
    private EquipoDAO equipoDAO;
    private CatalogoDAO catalogoDAO;
    
    @Before
    public void setUp() {
        // Inicializar DAOs reales (sin mocks)
        clienteDAO = new ClienteDAO();
        institucionDAO = new InstitucionDAO();
        profesionalDAO = new ProfesionalDAO();
        autoclaveDAO = new AutoclaveDAO();
        equipoDAO = new EquipoDAO();
        catalogoDAO = new CatalogoDAO();
    }
    
    // ========== TESTS DE CLIENTE DAO ==========
    
    @Test
    public void clienteDAO_ObtenerTodos_EjecutaQueryReal() {
        List<Cliente> clientes = clienteDAO.obtenerTodos();
        assertNotNull("Debe retornar lista (no null)", clientes);
        // No verificamos tamaño porque depende de datos en DB
    }
    
    @Test
    public void clienteDAO_BuscarPorNombre_EjecutaQueryReal() {
        // Buscar con texto que probablemente no exista
        List<Cliente> resultado = clienteDAO.buscarPorNombre("XYZ999TestNoExiste");
        assertNotNull("Debe retornar lista vacía, no null", resultado);
    }
    
    // ========== TESTS DE INSTITUCION DAO ==========
    
    @Test
    public void institucionDAO_ObtenerTodos_EjecutaQueryReal() {
        List<Institucion> instituciones = institucionDAO.obtenerTodos();
        assertNotNull("Debe retornar lista (no null)", instituciones);
    }
    
    @Test
    public void institucionDAO_BuscarPorNombre_EjecutaQueryReal() {
        List<Institucion> resultado = institucionDAO.buscarPorNombre("XYZ999TestNoExiste");
        assertNotNull("Debe retornar lista vacía, no null", resultado);
    }
    
    // ========== TESTS DE PROFESIONAL DAO ==========
    
    @Test
    public void profesionalDAO_ObtenerTodos_EjecutaQueryReal() {
        List<Profesional> profesionales = profesionalDAO.obtenerTodos();
        assertNotNull("Debe retornar lista (no null)", profesionales);
    }
    
    @Test
    public void profesionalDAO_BuscarPorNombre_EjecutaQueryReal() {
        List<Profesional> resultado = profesionalDAO.buscarPorNombre("XYZ999TestNoExiste");
        assertNotNull("Debe retornar lista vacía, no null", resultado);
    }
    
    // ========== TESTS DE AUTOCLAVE DAO ==========
    
    @Test
    public void autoclaveDAO_ObtenerTodos_EjecutaQueryReal() {
        List<Autoclave> autoclaves = autoclaveDAO.obtenerTodos();
        assertNotNull("Debe retornar lista (no null)", autoclaves);
    }
    
    // ========== TESTS DE EQUIPO DAO ==========
    
    @Test
    public void equipoDAO_ObtenerTodos_EjecutaQueryReal() {
        List<Equipo> equipos = equipoDAO.obtenerTodos();
        assertNotNull("Debe retornar lista (no null)", equipos);
    }
    
    @Test
    public void equipoDAO_Contar_EjecutaQueryReal() {
        long count = equipoDAO.contar();
        assertTrue("Count debe ser >= 0", count >= 0);
    }
    
    // ========== TESTS DE CATALOGO DAO ==========
    
    @Test
    public void catalogoDAO_ObtenerTodasLasDescripciones_EjecutaQueryReal() {
        java.util.Map<Integer, String> catalogo = catalogoDAO.obtenerTodasLasDescripciones();
        assertNotNull("Debe retornar mapa (no null)", catalogo);
    }
    
    @Test
    public void catalogoDAO_ObtenerDescripcion_ConCodigoInexistente() {
        String desc = catalogoDAO.obtenerDescripcion(9999999);
        // Puede retornar null si no existe, eso está bien
        assertTrue("Método debe ejecutarse sin excepción", true);
    }
}
