package com.example.model;

import static org.junit.Assert.*;

import org.junit.Test;
import java.time.LocalDateTime;

/**
 * Tests para la clase Material.
 */
public class MaterialTest {
    
    /**
     * Test: Constructor con 3 parámetros debe asignar valores correctamente.
     */
    @Test
    public void constructor_TresParametros_AsignaCorrectamente() {
        Material material = new Material(400, "Caja de Cirugía", 5);
        
        assertNull("El ID debe ser null", material.getId());
        assertEquals("El código debe ser 400", 400, material.getCodigo());
        assertEquals("La descripción debe coincidir", "Caja de Cirugía", material.getDescripcion());
        assertEquals("La cantidad debe ser 5", 5, material.getCantidad());
        assertEquals("El estado por defecto debe ser NUEVO", EstadoEquipo.NUEVO, material.getEstado());
    }
    
    /**
     * Test: Constructor con 4 parámetros debe incluir estado.
     */
    @Test
    public void constructor_CuatroParametros_IncluyeEstado() {
        Material material = new Material(400, "Material Test", 3, EstadoEquipo.LAVADO);
        
        assertEquals("El estado debe ser LAVADO", EstadoEquipo.LAVADO, material.getEstado());
        assertEquals("El código debe ser 400", 400, material.getCodigo());
    }
    
    /**
     * Test: Constructor con 5 parámetros debe incluir ID.
     */
    @Test
    public void constructor_CincoParametros_IncluyeId() {
        Material material = new Material(100, 400, "Material Test", 3, EstadoEquipo.LAVADO);
        
        assertEquals("El ID debe ser 100", Integer.valueOf(100), material.getId());
        assertEquals("El código debe ser 400", 400, material.getCodigo());
        assertEquals("El estado debe ser LAVADO", EstadoEquipo.LAVADO, material.getEstado());
    }
    
    /**
     * Test: Constructor completo debe asignar todos los valores.
     */
    @Test
    public void constructor_Completo_AsignaTodo() {
        LocalDateTime fecha = LocalDateTime.now();
        Material material = new Material(100, 400, "Material Test", 3, EstadoEquipo.LAVADO, fecha);
        
        assertEquals("El ID debe ser 100", Integer.valueOf(100), material.getId());
        assertEquals("El código debe ser 400", 400, material.getCodigo());
        assertEquals("La descripción debe coincidir", "Material Test", material.getDescripcion());
        assertEquals("La cantidad debe ser 3", 3, material.getCantidad());
        assertEquals("El estado debe ser LAVADO", EstadoEquipo.LAVADO, material.getEstado());
        assertEquals("La fecha debe coincidir", fecha, material.getUltimoMovimiento());
    }
    
    /**
     * Test: esPersistido debe retornar false si no tiene ID.
     */
    @Test
    public void esPersistido_SinId_RetornaFalse() {
        Material material = new Material(400, "Material Test", 3);
        
        assertFalse("Material sin ID no debe estar persistido", material.esPersistido());
    }
    
    /**
     * Test: esPersistido debe retornar true si tiene ID.
     */
    @Test
    public void esPersistido_ConId_RetornaTrue() {
        Material material = new Material(100, 400, "Material Test", 3, EstadoEquipo.NUEVO);
        
        assertTrue("Material con ID debe estar persistido", material.esPersistido());
    }
    
    /**
     * Test: Setters deben actualizar valores correctamente.
     */
    @Test
    public void setters_ActualizanValoresCorrectamente() {
        Material material = new Material(400, "Material Test", 3);
        LocalDateTime fecha = LocalDateTime.now();
        
        material.setId(50);
        material.setCantidad(10);
        material.setEstado(EstadoEquipo.ESTERILIZADO);
        material.setUltimoMovimiento(fecha);
        
        assertEquals("El ID debe actualizarse", Integer.valueOf(50), material.getId());
        assertEquals("La cantidad debe actualizarse", 10, material.getCantidad());
        assertEquals("El estado debe actualizarse", EstadoEquipo.ESTERILIZADO, material.getEstado());
        assertEquals("La fecha debe actualizarse", fecha, material.getUltimoMovimiento());
    }
}
