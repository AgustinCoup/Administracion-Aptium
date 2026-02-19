package com.example.view.helpers;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.example.model.Equipo;
import com.example.model.Material;
import com.example.model.EstadoEquipo;

/**
 * Tests para MaterialTableModel.
 * Valida la correcta visualización de materiales de un equipo.
 */
public class MaterialTableModelTest {
    
    private MaterialTableModel model;
    
    @Before
    public void setUp() {
        model = new MaterialTableModel();
    }
    
    /**
     * Test: El modelo debe tener 4 columnas.
     */
    @Test
    public void getColumnCount_Retorna4() {
        assertEquals("Debe tener 4 columnas", 4, model.getColumnCount());
    }
    
    /**
     * Test: Modelo sin cargar equipo debe tener 0 filas.
     */
    @Test
    public void getRowCount_SinEquipo_Retorna0() {
        assertEquals("Modelo sin equipo debe tener 0 filas", 0, model.getRowCount());
    }
    
    /**
     * Test: cargarMateriales con equipo null debe limpiar la tabla.
     */
    @Test
    public void cargarMateriales_EquipoNull_LimpiaTabla() {
        Equipo equipo = new Equipo();
        equipo.agregarMaterial(new Material(400, "Material 1", 2));
        model.cargarMateriales(equipo);
        assertEquals(1, model.getRowCount());
        
        model.cargarMateriales(null);
        
        assertEquals("Debe limpiar la tabla", 0, model.getRowCount());
    }
    
    /**
     * Test: cargarMateriales con equipo sin materiales debe tener 0 filas.
     */
    @Test
    public void cargarMateriales_EquipoSinMateriales_Retorna0() {
        Equipo equipo = new Equipo();
        
        model.cargarMateriales(equipo);
        
        assertEquals("Debe tener 0 filas", 0, model.getRowCount());
    }
    
    /**
     * Test: cargarMateriales con materiales debe crear filas.
     */
    @Test
    public void cargarMateriales_ConMateriales_CreaFilas() {
        Equipo equipo = new Equipo();
        equipo.agregarMaterial(new Material(400, "Material 1", 2));
        equipo.agregarMaterial(new Material(401, "Material 2", 3));
        equipo.agregarMaterial(new Material(402, "Material 3", 1));
        
        model.cargarMateriales(equipo);
        
        assertEquals("Debe tener 3 filas", 3, model.getRowCount());
    }
    
    /**
     * Test: getValueAt debe retornar la descripción del material.
     */
    @Test
    public void getValueAt_Columna0_RetornaDescripcion() {
        Equipo equipo = new Equipo();
        equipo.agregarMaterial(new Material(400, "Caja de Cirugía", 5));
        model.cargarMateriales(equipo);
        
        Object valor = model.getValueAt(0, 0);
        
        assertEquals("Debe retornar la descripción", "Caja de Cirugía", valor);
    }
    
    /**
     * Test: getValueAt debe retornar la cantidad del material.
     */
    @Test
    public void getValueAt_Columna1_RetornaCantidad() {
        Equipo equipo = new Equipo();
        equipo.agregarMaterial(new Material(400, "Material Test", 7));
        model.cargarMateriales(equipo);
        
        Object valor = model.getValueAt(0, 1);
        
        assertEquals("Debe retornar la cantidad", 7, valor);
    }
    
    /**
     * Test: getValueAt debe retornar el estado del material.
     */
    @Test
    public void getValueAt_Columna2_RetornaEstado() {
        Equipo equipo = new Equipo();
        Material material = new Material(400, "Material Test", 5, EstadoEquipo.LAVADO);
        equipo.agregarMaterial(material);
        model.cargarMateriales(equipo);
        
        Object valor = model.getValueAt(0, 2);
        
        assertEquals("Debe retornar el nombre del estado", "Lavado", valor);
    }
    
    /**
     * Test: limpiar debe eliminar todos los datos.
     */
    @Test
    public void limpiar_ConDatos_EliminaTodo() {
        Equipo equipo = new Equipo();
        equipo.agregarMaterial(new Material(400, "Material 1", 2));
        equipo.agregarMaterial(new Material(401, "Material 2", 3));
        model.cargarMateriales(equipo);
        
        model.limpiar();
        
        assertEquals("Debe tener 0 filas después de limpiar", 0, model.getRowCount());
    }
    
    /**
     * Test: Todas las celdas deben ser no editables.
     */
    @Test
    public void isCellEditable_CualquierCelda_RetornaFalse() {
        Equipo equipo = new Equipo();
        equipo.agregarMaterial(new Material(400, "Material Test", 5));
        model.cargarMateriales(equipo);
        
        assertFalse("Columna 0 no debe ser editable", model.isCellEditable(0, 0));
        assertFalse("Columna 1 no debe ser editable", model.isCellEditable(0, 1));
        assertFalse("Columna 2 no debe ser editable", model.isCellEditable(0, 2));
        assertFalse("Columna 3 no debe ser editable", model.isCellEditable(0, 3));
    }
}
