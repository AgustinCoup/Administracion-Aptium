package com.example.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.exception.DatabaseException;
import com.example.exception.ValidationException;
import com.example.model.Equipo;
import com.example.model.EquipoDAO;
import com.example.model.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests para EquipoService.
 * Usa Mockito para simular el DAO.
 */
public class EquipoServiceTest {
    
    @Mock
    private EquipoDAO equipoDAO;
    
    private EquipoService equipoService;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        equipoService = new EquipoService(equipoDAO);
    }
    
    /**
     * Test: Constructor con DAO null debe lanzar excepción.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_DAONull_LanzaExcepcion() {
        new EquipoService(null);
    }
    
    /**
     * Test: guardarEquipo con equipo null debe lanzar ValidationException.
     */
    @Test(expected = ValidationException.class)
    public void guardarEquipo_EquipoNull_LanzaValidationException() {
        equipoService.guardarEquipo(null);
    }
    
    /**
     * Test: guardarEquipo sin materiales debe lanzar ValidationException.
     */
    @Test(expected = ValidationException.class)
    public void guardarEquipo_SinMateriales_LanzaValidationException() {
        Equipo equipo = new Equipo();
        equipo.setClienteNombre("Cliente Test");
        
        equipoService.guardarEquipo(equipo);
    }
    
    /**
     * Test: guardarEquipo sin nombre de cliente debe lanzar ValidationException.
     */
    @Test(expected = ValidationException.class)
    public void guardarEquipo_SinNombreCliente_LanzaValidationException() {
        Equipo equipo = new Equipo();
        equipo.agregarMaterial(new Material(400, "Material Test", 2));
        
        equipoService.guardarEquipo(equipo);
    }
    
    /**
     * Test: guardarEquipo válido debe delegar al DAO.
     */
    @Test
    public void guardarEquipo_EquipoValido_DelgaAlDAO() {
        Equipo equipo = new Equipo();
        equipo.setClienteNombre("Cliente Test");
        equipo.agregarMaterial(new Material(400, "Material Test", 2));
        
        when(equipoDAO.guardarEquipo(any(Equipo.class))).thenReturn(true);
        
        boolean resultado = equipoService.guardarEquipo(equipo);
        
        assertTrue("Debe retornar true", resultado);
        verify(equipoDAO, times(1)).guardarEquipo(equipo);
    }
    
    /**
     * Test: guardarEquipo que falla en el DAO debe lanzar DatabaseException.
     */
    @Test(expected = DatabaseException.class)
    public void guardarEquipo_ErrorEnDAO_LanzaDatabaseException() {
        Equipo equipo = new Equipo();
        equipo.setClienteNombre("Cliente Test");
        equipo.agregarMaterial(new Material(400, "Material Test", 2));
        
        when(equipoDAO.guardarEquipo(any(Equipo.class)))
            .thenThrow(new DatabaseException("Error en BD"));
        
        equipoService.guardarEquipo(equipo);
    }
    
    /**
     * Test: obtenerTodos debe delegar al DAO.
     */
    @Test
    public void obtenerTodos_DelgaAlDAO() {
        List<Equipo> equipos = new ArrayList<>();
        equipos.add(new Equipo());
        equipos.add(new Equipo());
        
        when(equipoDAO.obtenerTodos()).thenReturn(equipos);
        
        List<Equipo> resultado = equipoService.obtenerTodos();
        
        assertEquals("Debe retornar 2 equipos", 2, resultado.size());
        verify(equipoDAO, times(1)).obtenerTodos();
    }
    
    /**
     * Test: obtenerTodos con error debe retornar lista vacía.
     */
    @Test
    public void obtenerTodos_ErrorEnDAO_RetornaListaVacia() {
        when(equipoDAO.obtenerTodos()).thenThrow(new RuntimeException("Error"));
        
        List<Equipo> resultado = equipoService.obtenerTodos();
        
        assertNotNull("No debe retornar null", resultado);
        assertTrue("Debe retornar lista vacía", resultado.isEmpty());
    }
    
    /**
     * Test: obtenerPorId debe delegar al DAO.
     */
    @Test
    public void obtenerPorId_IdValido_DelgaAlDAO() {
        Equipo equipo = new Equipo();
        equipo.setId(1);
        
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipo);
        
        Equipo resultado = equipoService.obtenerPorId("1");
        
        assertNotNull("Debe retornar el equipo", resultado);
        assertEquals("El ID debe coincidir", Integer.valueOf(1), resultado.getId());
        verify(equipoDAO, times(1)).obtenerPorId("1");
    }
    
    /**
     * Test: actualizar con equipo válido debe delegar al DAO.
     */
    @Test
    public void actualizar_EquipoValido_DelgaAlDAO() {
        Equipo equipo = new Equipo();
        equipo.setId(1);
        
        when(equipoDAO.actualizar(any(Equipo.class))).thenReturn(true);
        
        boolean resultado = equipoService.actualizar(equipo);
        
        assertTrue("Debe retornar true", resultado);
        verify(equipoDAO, times(1)).actualizar(equipo);
    }
    
    /**
     * Test: actualizar con equipo null debe retornar false.
     */
    @Test
    public void actualizar_EquipoNull_RetornaFalse() {
        boolean resultado = equipoService.actualizar(null);
        
        assertFalse("Debe retornar false", resultado);
        verify(equipoDAO, never()).actualizar(any(Equipo.class));
    }
    
    /**
     * Test: actualizar con equipo sin ID debe retornar false.
     */
    @Test
    public void actualizar_EquipoSinId_RetornaFalse() {
        Equipo equipo = new Equipo();
        
        boolean result = equipoService.actualizar(equipo);
        
        assertFalse("Debe retornar false", result);
        verify(equipoDAO, never()).actualizar(any(Equipo.class));
    }
    
    /**
     * Test: contar debe delegar al DAO.
     */
    @Test
    public void contar_DelgaAlDAO() {
        when(equipoDAO.contar()).thenReturn(10L);
        
        long resultado = equipoService.contar();
        
        assertEquals("Debe retornar 10", 10L, resultado);
        verify(equipoDAO, times(1)).contar();
    }
}
