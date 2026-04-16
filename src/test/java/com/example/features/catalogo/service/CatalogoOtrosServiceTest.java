package com.example.features.catalogo.service;

import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoOtrosServiceTest {

    @Mock  CatalogoOtrosDAO dao;
    @InjectMocks CatalogoOtrosService service;

    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_daoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new CatalogoOtrosService(null));
    }

    // ── buscarPorDescripcionParcial ───────────────────────────────────────────

    @Test
    void buscar_textoNulo_retornaVacioSinLlamarDAO() {
        assertTrue(service.buscarPorDescripcionParcial(null).isEmpty());
        verifyNoInteractions(dao);
    }

    @Test
    void buscar_textoVacio_retornaVacioSinLlamarDAO() {
        assertTrue(service.buscarPorDescripcionParcial("  ").isEmpty());
        verifyNoInteractions(dao);
    }

    @Test
    void buscar_textoValido_delegaADAO() {
        List<String> lista = List.of("Remito A", "Remito B");
        when(dao.buscarPorDescripcionParcial("Rem")).thenReturn(lista);

        assertEquals(lista, service.buscarPorDescripcionParcial("Rem"));
        verify(dao).buscarPorDescripcionParcial("Rem");
    }

    @Test
    void buscar_textoConEspacios_trimAntesDeDelegar() {
        when(dao.buscarPorDescripcionParcial("abc")).thenReturn(List.of("abc test"));

        service.buscarPorDescripcionParcial("  abc  ");
        verify(dao).buscarPorDescripcionParcial("abc");
    }

    @Test
    void buscar_daoLanzaExcepcion_retornaVacio() {
        when(dao.buscarPorDescripcionParcial("Rem")).thenThrow(new RuntimeException("DB error"));

        assertTrue(service.buscarPorDescripcionParcial("Rem").isEmpty());
    }
}
