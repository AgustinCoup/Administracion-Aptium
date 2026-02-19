package com.example.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests para la clase Equipo.
 * Valida la lógica de negocio en la entidad.
 */
public class EquipoTest {
    
    private Equipo equipo;
    
    @Before
    public void setUp() {
        equipo = new Equipo();
        equipo.setClienteNombre("Cliente Test");
        equipo.setNroCliente(1);
    }
    
    /**
     * Test: Constructor debe inicializar estado en NUEVO.
     */
    @Test
    public void constructor_DebeTenerEstadoNuevo() {
        Equipo nuevoEquipo = new Equipo();
        assertEquals("El estado inicial debe ser NUEVO", EstadoEquipo.NUEVO, nuevoEquipo.getEstado());
    }
    
    /**
     * Test: Constructor debe inicializar lista de materiales vacía.
     */
    @Test
    public void constructor_DebeTenerListaMaterialesVacia() {
        Equipo nuevoEquipo = new Equipo();
        assertNotNull("La lista de materiales no debe ser null", nuevoEquipo.getMateriales());
        assertTrue("La lista de materiales debe estar vacía", nuevoEquipo.getMateriales().isEmpty());
    }
    
    /**
     * Test: Agregar material debe incrementar la lista.
     */
    @Test
    public void agregarMaterial_MaterialValido_AgregaALista() {
        Material material = new Material(400, "Caja de Cirugía", 5);
        
        equipo.agregarMaterial(material);
        
        assertEquals("Debe haber 1 material", 1, equipo.getMateriales().size());
        assertEquals("El material debe ser el agregado", material, equipo.getMateriales().get(0));
    }
    
    /**
     * Test: Calcular estado sin materiales debe retornar NUEVO.
     */
    @Test
    public void calcularEstado_SinMateriales_RetornaNuevo() {
        EstadoEquipo estado = equipo.calcularEstado();
        
        assertEquals("Sin materiales debe retornar NUEVO", EstadoEquipo.NUEVO, estado);
    }
    
    /**
     * Test: Calcular estado debe retornar el más atrasado.
     */
    @Test
    public void calcularEstado_VariosEstados_RetornaMasAtrasado() {
        Material mat1 = new Material(400, "Material 1", 2, EstadoEquipo.LAVADO);
        Material mat2 = new Material(401, "Material 2", 3, EstadoEquipo.ESTERILIZADO);
        Material mat3 = new Material(402, "Material 3", 1, EstadoEquipo.EMPAQUETADO);
        
        equipo.agregarMaterial(mat1);
        equipo.agregarMaterial(mat2);
        equipo.agregarMaterial(mat3);
        
        EstadoEquipo estado = equipo.calcularEstado();
        
        assertEquals("Debe retornar LAVADO que es el más atrasado", EstadoEquipo.LAVADO, estado);
    }
    
    /**
     * Test: getSiguienteEstado con lavado activo debe seguir el flujo completo.
     */
    @Test
    public void getSiguienteEstado_ConLavado_SigueFlujoCompleto() {
        equipo.setRequiereLavado(true);
        equipo.setRequiereEmpaque(true);
        
        assertEquals("Después de NUEVO debe ser LAVANDO", 
            EstadoEquipo.LAVANDO, equipo.getSiguienteEstado(EstadoEquipo.NUEVO));
        assertEquals("Después de LAVANDO debe ser LAVADO", 
            EstadoEquipo.LAVADO, equipo.getSiguienteEstado(EstadoEquipo.LAVANDO));
        assertEquals("Después de LAVADO debe ser EMPAQUETADO", 
            EstadoEquipo.EMPAQUETADO, equipo.getSiguienteEstado(EstadoEquipo.LAVADO));
        assertEquals("Después de EMPAQUETADO debe ser ESTERILIZANDO", 
            EstadoEquipo.ESTERILIZANDO, equipo.getSiguienteEstado(EstadoEquipo.EMPAQUETADO));
        assertEquals("Después de ESTERILIZANDO debe ser ESTERILIZADO", 
            EstadoEquipo.ESTERILIZADO, equipo.getSiguienteEstado(EstadoEquipo.ESTERILIZANDO));
        assertNull("Después de ESTERILIZADO no hay siguiente", 
            equipo.getSiguienteEstado(EstadoEquipo.ESTERILIZADO));
    }
    
    /**
     * Test: getSiguienteEstado sin lavado debe saltarlo.
     */
    @Test
    public void getSiguienteEstado_SinLavado_SaltaLavado() {
        equipo.setRequiereLavado(false);
        equipo.setRequiereEmpaque(true);
        
        assertEquals("Después de NUEVO debe ser EMPAQUETADO (sin lavado)", 
            EstadoEquipo.EMPAQUETADO, equipo.getSiguienteEstado(EstadoEquipo.NUEVO));
    }
    
    /**
     * Test: getSiguienteEstado sin empaque ni lavado debe ir directo a esterilizado.
     */
    @Test
    public void getSiguienteEstado_SinEmpaqueNiLavado_VaDirectoAEsterilizando() {
        equipo.setRequiereLavado(false);
        equipo.setRequiereEmpaque(false);
        
        assertEquals("Debe ir de NUEVO a ESTERILIZANDO directamente", 
            EstadoEquipo.ESTERILIZANDO, equipo.getSiguienteEstado(EstadoEquipo.NUEVO));
    }
    
    /**
     * Test: Getters y setters básicos deben funcionar.
     */
    @Test
    public void settersGetters_FuncionanCorrectamente() {
        equipo.setId(100);
        equipo.setNroCliente(5);
        equipo.setClienteNombre("Cliente ABC");
        equipo.setNroProfesional(10);
        equipo.setProfesionalNombre("Dr. Smith");
        equipo.setPacienteNombre("Paciente Test");
        equipo.setNroInstitucion(20);
        equipo.setInstitucionNombre("Hospital General");
        equipo.setEstado(EstadoEquipo.LAVADO);
        
        assertEquals(Integer.valueOf(100), equipo.getId());
        assertEquals(5, equipo.getNroCliente());
        assertEquals("Cliente ABC", equipo.getClienteNombre());
        assertEquals(Integer.valueOf(10), equipo.getNroProfesional());
        assertEquals("Dr. Smith", equipo.getProfesionalNombre());
        assertEquals("Paciente Test", equipo.getPacienteNombre());
        assertEquals(Integer.valueOf(20), equipo.getNroInstitucion());
        assertEquals("Hospital General", equipo.getInstitucionNombre());
        assertEquals(EstadoEquipo.LAVADO, equipo.getEstado());
    }
}
