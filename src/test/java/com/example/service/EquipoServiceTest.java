package com.example.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.example.exception.ValidationException;
import com.example.model.Equipo;
import com.example.model.EquipoDAO;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests unitarios para EquipoService usando Mockito.
 * 
 * DEMUESTRA LAS VENTAJAS DE DEPENDENCY INJECTION:
 * - No necesitamos base de datos real
 * - Tests rápidos (sin I/O)
 * - Control total sobre el comportamiento del DAO
 * - Tests aislados y predecibles
 */
public class EquipoServiceTest {
    
    private EquipoService service;
    private EquipoDAO mockDAO;
    
    /**
     * Setup ejecutado antes de cada test.
     * Crea un mock del DAO y lo inyecta en el Service.
     * 
     * ESTO ES POSIBLE GRACIAS A DEPENDENCY INJECTION.
     */
    @Before
    public void setUp() {
        // Crear mock del DAO (sin tocar la base de datos)
        mockDAO = mock(EquipoDAO.class);
        
        // Inyectar el mock en el service
        service = new EquipoService(mockDAO);
    }
    
    /**
     * Test: guardar equipo válido debe retornar true.
     */
    @Test
    public void guardarEquipo_EquipoValido_RetornaTrue() {
        // Arrange: Preparar datos
        Equipo equipo = new Equipo();
        equipo.setClienteNombre("Cliente Test");
        equipo.agregarMaterial(new com.example.model.Material(400, "Tornillera", 1));
        
        // Configurar comportamiento del mock
        when(mockDAO.guardarEquipo(equipo)).thenReturn(true);
        
        // Act: Ejecutar
        boolean resultado = service.guardarEquipo(equipo);
        
        // Assert: Verificar
        assertTrue("Debería retornar true para equipo válido", resultado);
        verify(mockDAO, times(1)).guardarEquipo(equipo);
    }
    
    /**
     * Test: guardar equipo nulo debe lanzar ValidationException.
     */
    @Test(expected = ValidationException.class)
    public void guardarEquipo_EquipoNulo_LanzaValidationException() {
        // Act & Assert
        service.guardarEquipo(null);
    }
    
    /**
     * Test: guardar equipo sin materiales debe lanzar ValidationException.
     */
    @Test(expected = ValidationException.class)
    public void guardarEquipo_SinMateriales_LanzaValidationException() {
        // Arrange
        Equipo equipo = new Equipo();
        equipo.setClienteNombre("Cliente Test");
        // NO agregamos materiales
        
        // Act & Assert
        service.guardarEquipo(equipo);
    }
    
    /**
     * Test: obtener equipo existente retorna el equipo.
     */
    @Test
    public void obtenerPorId_EquipoExiste_RetornaEquipo() {
        // Arrange
        Equipo equipoEsperado = new Equipo();
        equipoEsperado.setId(1);
        equipoEsperado.setClienteNombre("Cliente Test");
        
        when(mockDAO.obtenerPorId("1")).thenReturn(equipoEsperado);
        
        // Act
        Equipo resultado = service.obtenerPorId("1");
        
        // Assert
        assertNotNull("No debería ser nulo", resultado);
        assertEquals("IDs deben coincidir", Integer.valueOf(1), resultado.getId());
        assertEquals("Nombres deben coincidir", "Cliente Test", resultado.getClienteNombre());
    }
}
