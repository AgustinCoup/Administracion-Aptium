package com.example.features.autoclaves.service;

import com.example.features.autoclaves.dao.AutoclaveDAO;
import com.example.features.autoclaves.model.Autoclave;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoclaveServiceTest {

    @Mock  AutoclaveDAO dao;
    @InjectMocks AutoclaveService service;

    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_daoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new AutoclaveService(null));
    }

    // ── obtenerTodos ──────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_delegaADAO() {
        List<Autoclave> lista = List.of(new Autoclave("E01", 120));
        when(dao.obtenerTodos()).thenReturn(lista);

        List<Autoclave> resultado = service.obtenerTodos();

        assertEquals(lista, resultado);
        verify(dao).obtenerTodos();
    }

    @Test
    void obtenerTodos_listaVacia_retornaVacia() {
        when(dao.obtenerTodos()).thenReturn(List.of());
        assertTrue(service.obtenerTodos().isEmpty());
    }
}
