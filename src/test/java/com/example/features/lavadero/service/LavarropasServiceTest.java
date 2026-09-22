package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
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
        List<Lavarropas> lista = Arrays.asList(new Lavarropas(1, true), new Lavarropas(2, false));
        when(dao.obtenerTodos()).thenReturn(lista);
        assertSame(lista, service.obtenerTodos());
        verify(dao).obtenerTodos();
    }

    @Test
    void obtenerDibujables_delegaADAO() {
        List<Lavarropas> lista = List.of(new Lavarropas(1, true));
        when(dao.obtenerDibujables()).thenReturn(lista);
        assertSame(lista, service.obtenerDibujables());
        verify(dao).obtenerDibujables();
    }

    @Test
    void agregar_numeroValido_delegaADAO() {
        service.agregar(14);
        verify(dao).agregar(14);
    }

    /**
     * El piso lo pone el service; el techo, la base. No hay una constante con "cuántos lavarropas
     * hay": desde que se dan de alta, eso es un dato.
     */
    @Test
    void agregar_numeroMenorA1_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.agregar(0));
        verifyNoInteractions(dao);
    }

    /**
     * No hay techo: si lo hubiera, dar de alta el lavarropas 14 sería imposible y el ABM entero no
     * serviría para nada.
     */
    @Test
    void agregar_numeroAlto_noLoRechazaElService() {
        service.agregar(9999);
        verify(dao).agregar(9999);
    }

    @Test
    void darDeBaja_delegaADAO() {
        service.darDeBaja(3);
        verify(dao).darDeBaja(3);
    }

    @Test
    void reactivar_delegaADAO() {
        service.reactivar(3);
        verify(dao).reactivar(3);
    }
}
