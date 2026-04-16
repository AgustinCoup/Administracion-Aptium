package com.example.features.profesionales.service;

import com.example.features.profesionales.dao.ProfesionalDAO;
import com.example.features.profesionales.model.Profesional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfesionalServiceTest {

    @Mock  ProfesionalDAO dao;
    @InjectMocks ProfesionalService service;

    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_daoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProfesionalService(null));
    }

    // ── guardarProfesional ────────────────────────────────────────────────────

    @Test
    void guardarProfesional_nulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.guardarProfesional(null));
    }

    @Test
    void guardarProfesional_nombreNulo_lanzaIllegalArgument() {
        Profesional p = new Profesional();
        assertThrows(IllegalArgumentException.class,
            () -> service.guardarProfesional(p));
    }

    @Test
    void guardarProfesional_nombreVacio_lanzaIllegalArgument() {
        Profesional p = new Profesional();
        p.setNombre("   ");
        assertThrows(IllegalArgumentException.class,
            () -> service.guardarProfesional(p));
    }

    @Test
    void guardarProfesional_valido_delegaADAO() {
        Profesional p = new Profesional();
        p.setNombre("Dr. Test");
        when(dao.guardar(p)).thenReturn(true);

        assertTrue(service.guardarProfesional(p));
        verify(dao).guardar(p);
    }

    // ── buscarProfesionales ───────────────────────────────────────────────────

    @Test
    void buscarProfesionales_nombreNulo_retornaVacio() {
        assertTrue(service.buscarProfesionales(null).isEmpty());
        verifyNoInteractions(dao);
    }

    @Test
    void buscarProfesionales_nombreCorto_retornaVacio() {
        assertTrue(service.buscarProfesionales("ab").isEmpty());
        verifyNoInteractions(dao);
    }

    @Test
    void buscarProfesionales_nombreValido_delegaADAO() {
        List<Profesional> lista = List.of(new Profesional(1, "Dr. García"));
        when(dao.buscarPorNombre("Gar")).thenReturn(lista);

        assertEquals(lista, service.buscarProfesionales("Gar"));
        verify(dao).buscarPorNombre("Gar");
    }

    // ── obtenerProfesionalPorId ───────────────────────────────────────────────

    @Test
    void obtenerProfesionalPorId_delegaADAO() {
        Profesional p = new Profesional(1, "Dr. García");
        when(dao.obtenerPorId(1)).thenReturn(p);

        assertEquals(p, service.obtenerProfesionalPorId(1));
    }

    // ── obtenerTodosLosProfesionales ──────────────────────────────────────────

    @Test
    void obtenerTodosLosProfesionales_delegaADAO() {
        List<Profesional> lista = List.of(new Profesional(1, "Dr. García"));
        when(dao.obtenerTodos()).thenReturn(lista);

        assertEquals(lista, service.obtenerTodosLosProfesionales());
    }
}
