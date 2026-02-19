package com.example.util;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests para la clase Validador.
 * Valida las reglas de validación de negocio.
 */
public class ValidadorTest {
    
    /**
     * Test: noEstaVacio con string null debe retornar false.
     */
    @Test
    public void noEstaVacio_Null_RetornaFalse() {
        assertFalse("Null no debe ser válido", Validador.noEstaVacio(null));
    }
    
    /**
     * Test: noEstaVacio con string vacío debe retornar false.
     */
    @Test
    public void noEstaVacio_StringVacio_RetornaFalse() {
        assertFalse("String vacío no debe ser válido", Validador.noEstaVacio(""));
    }
    
    /**
     * Test: noEstaVacio con string solo espacios debe retornar false.
     */
    @Test
    public void noEstaVacio_SoloEspacios_RetornaFalse() {
        assertFalse("Solo espacios no debe ser válido", Validador.noEstaVacio("   "));
        assertFalse("Tab y espacios no debe ser válido", Validador.noEstaVacio("\t  \n"));
    }
    
    /**
     * Test: noEstaVacio con texto válido debe retornar true.
     */
    @Test
    public void noEstaVacio_TextoValido_RetornaTrue() {
        assertTrue("Texto válido debe ser true", Validador.noEstaVacio("Texto válido"));
        assertTrue("Un solo carácter debe ser válido", Validador.noEstaVacio("A"));
    }
    
    /**
     * Test: esFormatoNombre debe rechazar textos sin espacio.
     */
    @Test
    public void esFormatoNombre_SinEspacio_RetornaFalse() {
        assertFalse("Una sola palabra no es válida", Validador.esFormatoNombre("Apellido"));
    }
    
    /**
     * Test: esFormatoNombre debe aceptar formato correcto.
     */
    @Test
    public void esFormatoNombre_FormatoCorrecto_RetornaTrue() {
        assertTrue("Apellido Nombre debe ser válido", Validador.esFormatoNombre("García Juan"));
        assertTrue("Con acentos debe ser válido", Validador.esFormatoNombre("Martínez María"));
        assertTrue("Con ñ debe ser válido", Validador.esFormatoNombre("Muñoz José"));
        assertTrue("Múltiples palabras debe ser válido", Validador.esFormatoNombre("De La Cruz Pedro"));
    }
    
    /**
     * Test: esFormatoNombre debe rechazar null o vacío.
     */
    @Test
    public void esFormatoNombre_NullOVacio_RetornaFalse() {
        assertFalse("Null no debe ser válido", Validador.esFormatoNombre(null));
        assertFalse("Vacío no debe ser válido", Validador.esFormatoNombre(""));
        assertFalse("Solo espacios no debe ser válido", Validador.esFormatoNombre("   "));
    }
}
