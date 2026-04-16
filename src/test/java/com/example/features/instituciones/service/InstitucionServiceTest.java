package com.example.features.instituciones.service;

import com.example.features.instituciones.dao.InstitucionDAO;
import com.example.features.instituciones.model.Institucion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitucionServiceTest {

    @Mock  InstitucionDAO dao;
    @InjectMocks InstitucionService service;

    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_daoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new InstitucionService(null));
    }

    // ── guardarInstitucion ────────────────────────────────────────────────────

    @Test
    void guardarInstitucion_nula_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.guardarInstitucion(null));
    }

    @Test
    void guardarInstitucion_nombreNulo_lanzaIllegalArgument() {
        Institucion inst = new Institucion();
        assertThrows(IllegalArgumentException.class,
            () -> service.guardarInstitucion(inst));
    }

    @Test
    void guardarInstitucion_nombreVacio_lanzaIllegalArgument() {
        Institucion inst = new Institucion();
        inst.setNombre("   ");
        assertThrows(IllegalArgumentException.class,
            () -> service.guardarInstitucion(inst));
    }

    @Test
    void guardarInstitucion_valida_delegaADAO() {
        Institucion inst = new Institucion();
        inst.setNombre("Hospital Test");
        when(dao.guardar(inst)).thenReturn(true);

        assertTrue(service.guardarInstitucion(inst));
        verify(dao).guardar(inst);
    }

    // ── buscarInstituciones ───────────────────────────────────────────────────

    @Test
    void buscarInstituciones_nombreNulo_retornaVacio() {
        assertTrue(service.buscarInstituciones(null).isEmpty());
        verifyNoInteractions(dao);
    }

    @Test
    void buscarInstituciones_nombreCorto_retornaVacio() {
        assertTrue(service.buscarInstituciones("ab").isEmpty());
        verifyNoInteractions(dao);
    }

    @Test
    void buscarInstituciones_nombreValido_delegaADAO() {
        List<Institucion> lista = List.of(new Institucion(1, "Hospital A"));
        when(dao.buscarPorNombre("Hosp")).thenReturn(lista);

        List<Institucion> resultado = service.buscarInstituciones("Hosp");

        assertEquals(lista, resultado);
        verify(dao).buscarPorNombre("Hosp");
    }

    // ── obtenerInstitucionPorId ───────────────────────────────────────────────

    @Test
    void obtenerInstitucionPorId_delegaADAO() {
        Institucion inst = new Institucion(1, "Hospital A");
        when(dao.obtenerPorId(1)).thenReturn(inst);

        assertEquals(inst, service.obtenerInstitucionPorId(1));
    }

    // ── obtenerTodasLasInstituciones ──────────────────────────────────────────

    @Test
    void obtenerTodasLasInstituciones_delegaADAO() {
        List<Institucion> lista = List.of(new Institucion(1, "Hospital A"));
        when(dao.obtenerTodos()).thenReturn(lista);

        assertEquals(lista, service.obtenerTodasLasInstituciones());
    }
}
