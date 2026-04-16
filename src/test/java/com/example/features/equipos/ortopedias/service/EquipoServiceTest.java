package com.example.features.equipos.ortopedias.service;

import com.example.common.exception.ValidationException;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.Material;
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
class EquipoServiceTest {

    @Mock
    private EquipoDAO equipoDAO;

    private EquipoService service;

    @BeforeEach
    void setUp() {
        service = new EquipoService(equipoDAO);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new EquipoService(null));
    }

    // ── guardarEquipo ────────────────────────────────────────────────────────

    @Test
    void guardarEquipo_null_lanzaValidationException() {
        assertThrows(ValidationException.class, () -> service.guardarEquipo(null));
        verifyNoInteractions(equipoDAO);
    }

    @Test
    void guardarEquipo_sinMateriales_lanzaValidationException() {
        Equipo e = equipoConCliente("Lopez");
        // Sin materiales → error
        assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
        verifyNoInteractions(equipoDAO);
    }

    @Test
    void guardarEquipo_clienteNombreNull_lanzaValidationException() {
        Equipo e = new Equipo();
        e.agregarMaterial(new Material(100, "Mat", 1));
        // clienteNombre null
        assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
        verifyNoInteractions(equipoDAO);
    }

    @Test
    void guardarEquipo_clienteNombreVacio_lanzaValidationException() {
        Equipo e = equipoConCliente("   ");
        e.agregarMaterial(new Material(100, "Mat", 1));
        assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
        verifyNoInteractions(equipoDAO);
    }

    @Test
    void guardarEquipo_valido_delegaADAO() {
        Equipo e = equipoConCliente("Lopez");
        e.agregarMaterial(new Material(100, "Mat", 1));
        when(equipoDAO.guardarEquipo(e)).thenReturn(true);

        assertTrue(service.guardarEquipo(e));
        verify(equipoDAO).guardarEquipo(e);
    }

    // ── obtenerTodos ─────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_delegaADAO() {
        List<Equipo> lista = Arrays.asList(new Equipo(), new Equipo());
        when(equipoDAO.obtenerTodos()).thenReturn(lista);

        assertEquals(lista, service.obtenerTodos());
    }

    // ── obtenerPorId ─────────────────────────────────────────────────────────

    @Test
    void obtenerPorId_delegaADAO() {
        Equipo e = new Equipo();
        when(equipoDAO.obtenerPorId("42")).thenReturn(e);

        assertSame(e, service.obtenerPorId("42"));
    }

    // ── actualizar ───────────────────────────────────────────────────────────

    @Test
    void actualizar_null_retornaFalse() {
        assertFalse(service.actualizar(null));
        verifyNoInteractions(equipoDAO);
    }

    @Test
    void actualizar_idNull_retornaFalse() {
        Equipo e = new Equipo(); // id es null por defecto
        assertFalse(service.actualizar(e));
        verifyNoInteractions(equipoDAO);
    }

    @Test
    void actualizar_valido_delegaADAO() {
        Equipo e = new Equipo();
        e.setId(1);
        when(equipoDAO.actualizar(e)).thenReturn(true);

        assertTrue(service.actualizar(e));
    }

    // ── contar / existe ───────────────────────────────────────────────────────

    @Test
    void contar_delegaADAO() {
        when(equipoDAO.contar()).thenReturn(7L);
        assertEquals(7L, service.contar());
    }

    @Test
    void existe_delegaADAO() {
        when(equipoDAO.existe("5")).thenReturn(true);
        assertTrue(service.existe("5"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Equipo equipoConCliente(String nombre) {
        Equipo e = new Equipo();
        e.setClienteNombre(nombre);
        e.setNroCliente(1);
        return e;
    }
}
