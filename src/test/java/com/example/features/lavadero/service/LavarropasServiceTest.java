package com.example.features.lavadero.service;

import com.example.features.lavadero.dao.LavarropasDAO;
import com.example.features.lavadero.model.Lavarropas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LavarropasServiceTest {

    @Mock
    private LavarropasDAO dao;

    private LavarropasService service;

    @BeforeEach
    void setUp() {
        service = new LavarropasService(dao);
    }

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new LavarropasService(null));
    }

    @Test
    void obtenerTodos_delegaADAO() {
        List<Lavarropas> lista = Arrays.asList(new Lavarropas(1, 50), new Lavarropas(2, 50));
        when(dao.obtenerTodos()).thenReturn(lista);
        assertSame(lista, service.obtenerTodos());
        verify(dao).obtenerTodos();
    }
}
