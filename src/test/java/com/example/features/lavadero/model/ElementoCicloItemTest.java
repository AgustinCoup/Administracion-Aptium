package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElementoCicloItemTest {

    @Test
    void disponible_esTotal_cuandoNadaProcesado() {
        ElementoCicloItem item = new ElementoCicloItem(1, 1, "Batas", 5, 0, "Cliente A");
        assertEquals(5, item.getCantidadDisponible());
    }

    @Test
    void disponible_esCero_cuandoTodoProcesado() {
        ElementoCicloItem item = new ElementoCicloItem(1, 1, "Batas", 5, 5, "Cliente A");
        assertEquals(0, item.getCantidadDisponible());
    }

    @Test
    void disponible_esParcial_cuandoSubcantidadProcesada() {
        ElementoCicloItem item = new ElementoCicloItem(1, 1, "Batas", 5, 3, "Cliente A");
        assertEquals(2, item.getCantidadDisponible());
    }

    @Test
    void cantidadEnCiclo_esZero_porDefecto() {
        ElementoCicloItem item = new ElementoCicloItem(1, 1, "Toallon", 10, 0, "Cliente B");
        assertEquals(0, item.getCantidadEnCiclo());
    }

    @Test
    void setCantidadEnCiclo_actualizaCorrecto() {
        ElementoCicloItem item = new ElementoCicloItem(1, 1, "Toallon", 10, 0, "Cliente B");
        item.setCantidadEnCiclo(4);
        assertEquals(4, item.getCantidadEnCiclo());
    }

    @Test
    void getters_devuelvenCamposCorrectos() {
        ElementoCicloItem item = new ElementoCicloItem(7, 3, "Sabana grande", 8, 2, "Hospital X");
        assertEquals(7, item.getElementoClasificacionId());
        assertEquals(3, item.getIngresoId());
        assertEquals("Sabana grande", item.getElementoNombre());
        assertEquals(8, item.getCantidadTotal());
        assertEquals(2, item.getCantidadYaProcesada());
        assertEquals("Hospital X", item.getClienteNombre());
    }

    @Test
    void isEquipo_conCategoriaEquipo_retornaTrue() {
        ElementoCicloItem item = new ElementoCicloItem(1, 1, "Equipo Trauma", 1, 0, "Cliente", "EQUIPO");
        assertTrue(item.isEquipo());
    }

    @Test
    void isEquipo_conCategoriaRegular_retornaFalse() {
        ElementoCicloItem item = new ElementoCicloItem(1, 1, "Bolsa", 3, 0, "Cliente", "REGULAR");
        assertFalse(item.isEquipo());
    }
}
