package com.example.view.helpers;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests para AutoclaveTableModel.
 * Valida la correcta visualización de datos de autoclaves.
 */
public class AutoclaveTableModelTest {
    
    private AutoclaveTableModel model;
    
    @Before
    public void setUp() {
        model = new AutoclaveTableModel();
    }
    
    /**
     * Test: El modelo debe tener 3 columnas.
     */
    @Test
    public void getColumnCount_SiempreRetorna3() {
        assertEquals("Debe tener 3 columnas", 3, model.getColumnCount());
    }
    
    /**
     * Test: Las columnas deben tener nombres correctos.
     */
    @Test
    public void getColumnName_RetornaNombresCorrectos() {
        assertEquals("Primera columna debe ser Nombre", "Nombre", model.getColumnName(0));
        assertEquals("Segunda columna debe ser Estado", "Estado", model.getColumnName(1));
        assertEquals("Tercera columna debe ser Capacidad", "Capacidad", model.getColumnName(2));
    }
    
    /**
     * Test: Modelo vacío debe tener 0 filas.
     */
    @Test
    public void getRowCount_ModeloVacio_Retorna0() {
        assertEquals("Modelo vacío debe tener 0 filas", 0, model.getRowCount());
    }
    
    /**
     * Test: Agregar autoclaves debe incrementar el número de filas.
     */
    @Test
    public void setAutoclaves_ListaConDatos_IncrementaFilas() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        autoclaves.add(new AutoclaveItem("Autoclave 1", 100, false, null, 0));
        autoclaves.add(new AutoclaveItem("Autoclave 2", 150, false, null, 0));
        
        model.setAutoclaves(autoclaves);
        
        assertEquals("Debe tener 2 filas", 2, model.getRowCount());
    }
    
    /**
     * Test: getValueAt debe retornar el nombre en columna 0.
     */
    @Test
    public void getValueAt_Columna0_RetornaNombre() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        autoclaves.add(new AutoclaveItem("Autoclave Test", 100, false, null, 0));
        model.setAutoclaves(autoclaves);
        
        Object valor = model.getValueAt(0, 0);
        
        assertEquals("Debe retornar el nombre", "Autoclave Test", valor);
    }
    
    /**
     * Test: getValueAt debe retornar "Libre" si no está ocupado.
     */
    @Test
    public void getValueAt_Columna1NoOcupado_RetornaLibre() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        autoclaves.add(new AutoclaveItem("Autoclave Test", 100, false, null, 0));
        model.setAutoclaves(autoclaves);
        
        Object valor = model.getValueAt(0, 1);
        
        assertEquals("Debe retornar 'Libre'", "Libre", valor);
    }
    
    /**
     * Test: getValueAt debe retornar "Ocupado" si está ocupado.
     */
    @Test
    public void getValueAt_Columna1Ocupado_RetornaOcupado() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        autoclaves.add(new AutoclaveItem("Autoclave Test", 100, true, 1, 50));
        model.setAutoclaves(autoclaves);
        
        Object valor = model.getValueAt(0, 1);
        
        assertEquals("Debe retornar 'Ocupado'", "Ocupado", valor);
    }
    
    /**
     * Test: getValueAt debe retornar la capacidad en columna 2.
     */
    @Test
    public void getValueAt_Columna2_RetornaCapacidad() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        autoclaves.add(new AutoclaveItem("Autoclave Test", 150, false, null, 0));
        model.setAutoclaves(autoclaves);
        
        Object valor = model.getValueAt(0, 2);
        
        assertEquals("Debe retornar la capacidad", 150, valor);
    }
    
    /**
     * Test: getAutoclaveAt debe retornar la autoclave en el índice.
     */
    @Test
    public void getAutoclaveAt_IndiceValido_RetornaAutoclave() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        AutoclaveItem item1 = new AutoclaveItem("Autoclave 1", 100, false, null, 0);
        AutoclaveItem item2 = new AutoclaveItem("Autoclave 2", 150, false, null, 0);
        autoclaves.add(item1);
        autoclaves.add(item2);
        model.setAutoclaves(autoclaves);
        
        AutoclaveItem resultado = model.getAutoclaveAt(1);
        
        assertEquals("Debe retornar item2", item2, resultado);
        assertEquals("El nombre debe coincidir", "Autoclave 2", resultado.getNombre());
    }
    
    /**
     * Test: getAutoclaveAt con índice inválido debe retornar null.
     */
    @Test
    public void getAutoclaveAt_IndiceInvalido_RetornaNull() {
        assertNull("Índice negativo debe retornar null", model.getAutoclaveAt(-1));
        assertNull("Índice fuera de rango debe retornar null", model.getAutoclaveAt(10));
    }
    
    /**
     * Test: clear debe eliminar todos los datos.
     */
    @Test
    public void clear_ConDatos_EliminaTodo() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        autoclaves.add(new AutoclaveItem("Autoclave 1", 100, false, null, 0));
        model.setAutoclaves(autoclaves);
        
        model.clear();
        
        assertEquals("Debe tener 0 filas después de clear", 0, model.getRowCount());
    }
    
    /**
     * Test: Todas las celdas deben ser no editables.
     */
    @Test
    public void isCellEditable_TodasLasCeldas_RetornaFalse() {
        List<AutoclaveItem> autoclaves = new ArrayList<>();
        autoclaves.add(new AutoclaveItem("Autoclave 1", 100, false, null, 0));
        model.setAutoclaves(autoclaves);
        
        assertFalse("Columna 0 no debe ser editable", model.isCellEditable(0, 0));
        assertFalse("Columna 1 no debe ser editable", model.isCellEditable(0, 1));
        assertFalse("Columna 2 no debe ser editable", model.isCellEditable(0, 2));
    }
    
    /**
     * Test: getColumnClass debe retornar el tipo correcto.
     */
    @Test
    public void getColumnClass_RetornaTipoCorrecto() {
        assertEquals("Columna 0 debe ser String", String.class, model.getColumnClass(0));
        assertEquals("Columna 1 debe ser String", String.class, model.getColumnClass(1));
        assertEquals("Columna 2 debe ser Integer", Integer.class, model.getColumnClass(2));
    }
}
