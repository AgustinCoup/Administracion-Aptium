package com.example.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.model.LoteDAO;
import com.example.model.Lote;
import com.example.model.LoteMaterialInfo;
import com.example.model.LoteMovimiento;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests para LoteService.
 * Usa Mockito para simular el DAO.
 */
public class LoteServiceTest {
    
    @Mock
    private LoteDAO loteDAO;
    
    private LoteService loteService;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        loteService = new LoteService(loteDAO);
    }
    
    /**
     * Test: Constructor con DAO null debe lanzar excepción.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_DAONull_LanzaExcepcion() {
        new LoteService(null);
    }
    
    /**
     * Test: obtenerLotesActivosPorAutoclave debe delegar al DAO.
     */
    @Test
    public void obtenerLotesActivosPorAutoclave_DelgaAlDAO() {
        Map<String, Lote> lotes = new HashMap<>();
        Lote lote1 = new Lote(1, "2025-001", 2025, 1, "Autoclave A", 100, 50, LocalDateTime.now(), null);
        lotes.put("Autoclave A", lote1);
        
        when(loteDAO.obtenerLotesActivosPorAutoclave()).thenReturn(lotes);
        
        Map<String, Lote> resultado = loteService.obtenerLotesActivosPorAutoclave();
        
        assertEquals("Debe retornar 1 lote", 1, resultado.size());
        assertTrue("Debe contener Autoclave A", resultado.containsKey("Autoclave A"));
        verify(loteDAO, times(1)).obtenerLotesActivosPorAutoclave();
    }
    
    /**
     * Test: obtenerLotesActivosPorAutoclave con error debe retornar mapa vacío.
     */
    @Test
    public void obtenerLotesActivosPorAutoclave_ErrorEnDAO_RetornaMapaVacio() {
        when(loteDAO.obtenerLotesActivosPorAutoclave()).thenThrow(new RuntimeException("Error"));
        
        Map<String, Lote> resultado = loteService.obtenerLotesActivosPorAutoclave();
        
        assertNotNull("No debe retornar null", resultado);
        assertTrue("Debe retornar mapa vacío", resultado.isEmpty());
    }
    
    /**
     * Test: obtenerMaterialesPorLote debe delegar al DAO.
     */
    @Test
    public void obtenerMaterialesPorLote_DelgaAlDAO() {
        List<LoteMaterialInfo> materiales = new ArrayList<>();
        materiales.add(new LoteMaterialInfo(1, 10, 400, "Material 1", 5, 10));
        materiales.add(new LoteMaterialInfo(2, 11, 401, "Material 2", 3, 15));
        
        when(loteDAO.obtenerMaterialesPorLote(1)).thenReturn(materiales);
        
        List<LoteMaterialInfo> resultado = loteService.obtenerMaterialesPorLote(1);
        
        assertEquals("Debe retornar 2 materiales", 2, resultado.size());
        verify(loteDAO, times(1)).obtenerMaterialesPorLote(1);
    }
    
    /**
     * Test: obtenerMaterialesPorLote con error debe retornar lista vacía.
     */
    @Test
    public void obtenerMaterialesPorLote_ErrorEnDAO_RetornaListaVacia() {
        when(loteDAO.obtenerMaterialesPorLote(1)).thenThrow(new RuntimeException("Error"));
        
        List<LoteMaterialInfo> resultado = loteService.obtenerMaterialesPorLote(1);
        
        assertNotNull("No debe retornar null", resultado);
        assertTrue("Debe retornar lista vacía", resultado.isEmpty());
    }
    
    /**
     * Test: lanzarLote debe delegar al DAO.
     */
    @Test
    public void lanzarLote_DatosValidos_DelgaAlDAO() {
        List<LoteMovimiento> movimientos = new ArrayList<>();
        movimientos.add(new LoteMovimiento(1, 5, 50));
        
        Lote loteEsperado = new Lote(1, "2025-001", 2025, 1, "Autoclave A", 100, 50, LocalDateTime.now(), null);
        
        when(loteDAO.lanzarLote("Autoclave A", 100, 50, movimientos)).thenReturn(loteEsperado);
        
        Lote resultado = loteService.lanzarLote("Autoclave A", 100, 50, movimientos);
        
        assertNotNull("Debe retornar un lote", resultado);
        assertEquals("El ID de negocio debe coincidir", "2025-001", resultado.getIdNegocio());
        verify(loteDAO, times(1)).lanzarLote("Autoclave A", 100, 50, movimientos);
    }
    
    /**
     * Test: lanzarLote con error debe retornar null.
     */
    @Test
    public void lanzarLote_ErrorEnDAO_RetornaNull() {
        List<LoteMovimiento> movimientos = new ArrayList<>();
        
        when(loteDAO.lanzarLote(anyString(), anyInt(), anyInt(), anyList()))
            .thenThrow(new RuntimeException("Error"));
        
        Lote resultado = loteService.lanzarLote("Autoclave A", 100, 50, movimientos);
        
        assertNull("Debe retornar null en caso de error", resultado);
    }
    
    /**
     * Test: finalizarLote debe delegar al DAO.
     */
    @Test
    public void finalizarLote_LoteValido_DelgaAlDAO() {
        when(loteDAO.finalizarLote(1)).thenReturn(true);
        
        boolean resultado = loteService.finalizarLote(1);
        
        assertTrue("Debe retornar true", resultado);
        verify(loteDAO, times(1)).finalizarLote(1);
    }
    
    /**
     * Test: finalizarLote con error debe retornar false.
     */
    @Test
    public void finalizarLote_ErrorEnDAO_RetornaFalse() {
        when(loteDAO.finalizarLote(1)).thenThrow(new RuntimeException("Error"));
        
        boolean resultado = loteService.finalizarLote(1);
        
        assertFalse("Debe retornar false en caso de error", resultado);
    }
}
