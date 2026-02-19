package com.example.model;

import static org.junit.Assert.*;

import org.junit.Test;
import java.awt.Color;

/**
 * Tests para el enum EstadoEquipo.
 */
public class EstadoEquipoTest {
    
    /**
     * Test: Verificar que todos los estados existan.
     */
    @Test
    public void values_EstadosExisten() {
        EstadoEquipo[] estados = EstadoEquipo.values();
        
        assertEquals("Deben existir 7 estados", 7, estados.length);
        assertTrue(contieneEstado(estados, EstadoEquipo.NUEVO));
        assertTrue(contieneEstado(estados, EstadoEquipo.LAVANDO));
        assertTrue(contieneEstado(estados, EstadoEquipo.LAVADO));
        assertTrue(contieneEstado(estados, EstadoEquipo.EMPAQUETADO));
        assertTrue(contieneEstado(estados, EstadoEquipo.ESTERILIZANDO));
        assertTrue(contieneEstado(estados, EstadoEquipo.ESTERILIZADO));
        assertTrue(contieneEstado(estados, EstadoEquipo.ENTREGADO));
    }
    
    /**
     * Test: getNombre debe retornar el nombre correcto.
     */
    @Test
    public void getNombre_RetornaNombreCorrecto() {
        assertEquals("Nuevo", EstadoEquipo.NUEVO.getNombre());
        assertEquals("Lavando", EstadoEquipo.LAVANDO.getNombre());
        assertEquals("Lavado", EstadoEquipo.LAVADO.getNombre());
        assertEquals("Empaquetado", EstadoEquipo.EMPAQUETADO.getNombre());
        assertEquals("Esterilizando", EstadoEquipo.ESTERILIZANDO.getNombre());
        assertEquals("Esterilizado", EstadoEquipo.ESTERILIZADO.getNombre());
        assertEquals("Entregado", EstadoEquipo.ENTREGADO.getNombre());
    }
    
    /**
     * Test: getOrden debe retornar el orden correcto.
     */
    @Test
    public void getOrden_RetornaOrdenCorrecto() {
        assertEquals(1, EstadoEquipo.NUEVO.getOrden());
        assertEquals(2, EstadoEquipo.LAVANDO.getOrden());
        assertEquals(3, EstadoEquipo.LAVADO.getOrden());
        assertEquals(4, EstadoEquipo.EMPAQUETADO.getOrden());
        assertEquals(5, EstadoEquipo.ESTERILIZANDO.getOrden());
        assertEquals(6, EstadoEquipo.ESTERILIZADO.getOrden());
        assertEquals(7, EstadoEquipo.ENTREGADO.getOrden());
    }
    
    /**
     * Test: getColor debe retornar colores válidos.
     */
    @Test
    public void getColor_RetornaColorValido() {
        assertNotNull("NUEVO debe tener color", EstadoEquipo.NUEVO.getColor());
        assertNotNull("LAVANDO debe tener color", EstadoEquipo.LAVANDO.getColor());
        assertNotNull("ESTERILIZADO debe tener color", EstadoEquipo.ESTERILIZADO.getColor());
    }
    
    /**
     * Test: getSiguiente debe retornar el siguiente estado en orden.
     */
    @Test
    public void getSiguiente_RetornaSiguienteEstado() {
        assertEquals(EstadoEquipo.LAVANDO, EstadoEquipo.NUEVO.getSiguiente());
        assertEquals(EstadoEquipo.LAVADO, EstadoEquipo.LAVANDO.getSiguiente());
        assertEquals(EstadoEquipo.EMPAQUETADO, EstadoEquipo.LAVADO.getSiguiente());
        assertEquals(EstadoEquipo.ESTERILIZANDO, EstadoEquipo.EMPAQUETADO.getSiguiente());
        assertEquals(EstadoEquipo.ESTERILIZADO, EstadoEquipo.ESTERILIZANDO.getSiguiente());
        assertEquals(EstadoEquipo.ENTREGADO, EstadoEquipo.ESTERILIZADO.getSiguiente());
        assertNull("Después de ENTREGADO no hay siguiente", EstadoEquipo.ENTREGADO.getSiguiente());
    }
    
    /**
     * Test: esFinal debe retornar true solo para ESTERILIZADO.
     */
    @Test
    public void esFinal_SoloEsterilizadoEsTrue() {
        assertFalse(EstadoEquipo.NUEVO.esFinal());
        assertFalse(EstadoEquipo.LAVANDO.esFinal());
        assertFalse(EstadoEquipo.LAVADO.esFinal());
        assertFalse(EstadoEquipo.EMPAQUETADO.esFinal());
        assertFalse(EstadoEquipo.ESTERILIZANDO.esFinal());
        assertTrue(EstadoEquipo.ESTERILIZADO.esFinal());
        assertFalse(EstadoEquipo.ENTREGADO.esFinal());
    }
    
    /**
     * Test: esInicial debe retornar true solo para NUEVO.
     */
    @Test
    public void esInicial_SoloNuevoEsTrue() {
        assertTrue(EstadoEquipo.NUEVO.esInicial());
        assertFalse(EstadoEquipo.LAVANDO.esInicial());
        assertFalse(EstadoEquipo.LAVADO.esInicial());
        assertFalse(EstadoEquipo.EMPAQUETADO.esInicial());
        assertFalse(EstadoEquipo.ESTERILIZANDO.esInicial());
        assertFalse(EstadoEquipo.ESTERILIZADO.esInicial());
        assertFalse(EstadoEquipo.ENTREGADO.esInicial());
    }
    
    private boolean contieneEstado(EstadoEquipo[] estados, EstadoEquipo buscado) {
        for (EstadoEquipo estado : estados) {
            if (estado == buscado) {
                return true;
            }
        }
        return false;
    }
}
