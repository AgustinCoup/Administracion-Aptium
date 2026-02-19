package com.example.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.model.InstitucionDAO;
import com.example.model.Institucion;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests para InstitucionService.
 * Usa Mockito para simular el DAO.
 */
public class InstitucionServiceTest {
    
    @Mock
    private InstitucionDAO institucionDAO;
    
    private InstitucionService institucionService;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        institucionService = new InstitucionService(institucionDAO);
    }
    
    /**
     * Test: Constructor con DAO null debe lanzar excepción.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_DAONull_LanzaExcepcion() {
        new InstitucionService(null);
    }
    
    /**
     * Test: buscarInstituciones con menos de 3 caracteres debe retornar lista vacía.
     */
    @Test
    public void buscarInstituciones_MenosDe3Caracteres_RetornaListaVacia() {
        List<Institucion> resultado = institucionService.buscarInstituciones("ab");
        
        assertTrue("Debe retornar lista vacía", resultado.isEmpty());
        verify(institucionDAO, never()).buscarPorNombre(anyString());
    }
    
    /**
     * Test: buscarInstituciones con null debe retornar lista vacía.
     */
    @Test
    public void buscarInstituciones_Null_RetornaListaVacia() {
        List<Institucion> resultado = institucionService.buscarInstituciones(null);
        
        assertTrue("Debe retornar lista vacía", resultado.isEmpty());
        verify(institucionDAO, never()).buscarPorNombre(anyString());
    }
    
    /**
     * Test: buscarInstituciones con texto válido debe delegar al DAO.
     */
    @Test
    public void buscarInstituciones_TextoValido_DelgaAlDAO() {
        List<Institucion> instituciones = new ArrayList<>();
        instituciones.add(new Institucion(1, "Hospital General"));
        
        when(institucionDAO.buscarPorNombre("Hosp")).thenReturn(instituciones);
        
        List<Institucion> resultado = institucionService.buscarInstituciones("Hosp");
        
        assertEquals("Debe retornar 1 institución", 1, resultado.size());
        verify(institucionDAO, times(1)).buscarPorNombre("Hosp");
    }
    
    /**
     * Test: obtenerInstitucionPorId debe delegar al DAO.
     */
    @Test
    public void obtenerInstitucionPorId_DelgaAlDAO() {
        Institucion institucion = new Institucion(1, "Hospital General");
        
        when(institucionDAO.obtenerPorId(1)).thenReturn(institucion);
        
        Institucion resultado = institucionService.obtenerInstitucionPorId(1);
        
        assertNotNull("Debe retornar la institución", resultado);
        assertEquals("El ID debe coincidir", Integer.valueOf(1), Integer.valueOf(resultado.getId()));
        verify(institucionDAO, times(1)).obtenerPorId(1);
    }
    
    /**
     * Test: obtenerTodasLasInstituciones debe delegar al DAO.
     */
    @Test
    public void obtenerTodasLasInstituciones_DelgaAlDAO() {
        List<Institucion> instituciones = new ArrayList<>();
        instituciones.add(new Institucion(1, "Hospital A"));
        instituciones.add(new Institucion(2, "Hospital B"));
        
        when(institucionDAO.obtenerTodos()).thenReturn(instituciones);
        
        List<Institucion> resultado = institucionService.obtenerTodasLasInstituciones();
        
        assertEquals("Debe retornar 2 instituciones", 2, resultado.size());
        verify(institucionDAO, times(1)).obtenerTodos();
    }
    
    /**
     * Test: guardarInstitucion con institución null debe lanzar excepción.
     */
    @Test(expected = IllegalArgumentException.class)
    public void guardarInstitucion_InstitucionNull_LanzaExcepcion() {
        institucionService.guardarInstitucion(null);
    }
    
    /**
     * Test: guardarInstitucion con nombre vacío debe lanzar excepción.
     */
    @Test(expected = IllegalArgumentException.class)
    public void guardarInstitucion_NombreVacio_LanzaExcepcion() {
        Institucion institucion = new Institucion(0, "");
        institucionService.guardarInstitucion(institucion);
    }
    
    /**
     * Test: guardarInstitucion con nombre null debe lanzar excepción.
     */
    @Test(expected = IllegalArgumentException.class)
    public void guardarInstitucion_NombreNull_LanzaExcepcion() {
        Institucion institucion = new Institucion(0, null);
        institucionService.guardarInstitucion(institucion);
    }
    
    /**
     * Test: guardarInstitucion válida debe delegar al DAO.
     */
    @Test
    public void guardarInstitucion_InstitucionValida_DelgaAlDAO() {
        Institucion institucion = new Institucion(0, "Hospital Nuevo");
        
        when(institucionDAO.guardar(any(Institucion.class))).thenReturn(true);
        
        boolean resultado = institucionService.guardarInstitucion(institucion);
        
        assertTrue("Debe retornar true", resultado);
        verify(institucionDAO, times(1)).guardar(institucion);
    }
}
