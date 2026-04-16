package com.example.features.catalogo.service;

import com.example.features.catalogo.dao.CatalogoDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock  CatalogoDAO dao;
    @InjectMocks CatalogoService service;

    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_daoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new CatalogoService(null));
    }

    // ── obtenerCatalogo ───────────────────────────────────────────────────────

    @Test
    void obtenerCatalogo_exito_retornaMapa() {
        Map<Integer, String> mapa = Map.of(400, "Tornillera");
        when(dao.obtenerTodasLasDescripciones()).thenReturn(mapa);

        assertEquals(mapa, service.obtenerCatalogo());
    }

    @Test
    void obtenerCatalogo_daoLanzaExcepcion_retornaMapaVacio() {
        when(dao.obtenerTodasLasDescripciones()).thenThrow(new RuntimeException("DB error"));

        assertTrue(service.obtenerCatalogo().isEmpty());
    }

    // ── obtenerDescripcion ────────────────────────────────────────────────────

    @Test
    void obtenerDescripcion_exito_retornaDescripcion() {
        when(dao.obtenerDescripcion(400)).thenReturn("Tornillera");

        assertEquals("Tornillera", service.obtenerDescripcion(400));
    }

    @Test
    void obtenerDescripcion_daoLanzaExcepcion_retornaNull() {
        when(dao.obtenerDescripcion(400)).thenThrow(new RuntimeException("DB error"));

        assertNull(service.obtenerDescripcion(400));
    }

    // ── obtenerVolumen ────────────────────────────────────────────────────────

    @Test
    void obtenerVolumen_exito_retornaVolumen() {
        when(dao.obtenerVolumen(400)).thenReturn(15);

        assertEquals(15, service.obtenerVolumen(400));
    }

    @Test
    void obtenerVolumen_daoLanzaExcepcion_retornaNull() {
        when(dao.obtenerVolumen(400)).thenThrow(new RuntimeException("DB error"));

        assertNull(service.obtenerVolumen(400));
    }

    // ── obtenerVolumenes ──────────────────────────────────────────────────────

    @Test
    void obtenerVolumenes_exito_retornaMapa() {
        Map<Integer, Integer> mapa = Map.of(400, 15);
        when(dao.obtenerTodosLosVolumenes()).thenReturn(mapa);

        assertEquals(mapa, service.obtenerVolumenes());
    }

    @Test
    void obtenerVolumenes_daoLanzaExcepcion_retornaMapaVacio() {
        when(dao.obtenerTodosLosVolumenes()).thenThrow(new RuntimeException("DB error"));

        assertTrue(service.obtenerVolumenes().isEmpty());
    }

    // ── guardarDescripcion ────────────────────────────────────────────────────

    @Test
    void guardarDescripcion_descripcionNula_retornaFalseSinLlamarDAO() {
        assertFalse(service.guardarDescripcion(400, null));
        verifyNoInteractions(dao);
    }

    @Test
    void guardarDescripcion_descripcionVacia_retornaFalseSinLlamarDAO() {
        assertFalse(service.guardarDescripcion(400, "  "));
        verifyNoInteractions(dao);
    }

    @Test
    void guardarDescripcion_valida_delegaADAO() {
        when(dao.guardarDescripcion(400, "Tornillera")).thenReturn(true);

        assertTrue(service.guardarDescripcion(400, "Tornillera"));
        verify(dao).guardarDescripcion(400, "Tornillera");
    }

    @Test
    void guardarDescripcion_daoLanzaExcepcion_retornaFalse() {
        when(dao.guardarDescripcion(400, "Tornillera")).thenThrow(new RuntimeException("DB error"));

        assertFalse(service.guardarDescripcion(400, "Tornillera"));
    }

    // ── contar ────────────────────────────────────────────────────────────────

    @Test
    void contar_exito_retornaConteo() {
        when(dao.contar()).thenReturn(10L);

        assertEquals(10L, service.contar());
    }

    @Test
    void contar_daoLanzaExcepcion_retornaCero() {
        when(dao.contar()).thenThrow(new RuntimeException("DB error"));

        assertEquals(0L, service.contar());
    }
}
