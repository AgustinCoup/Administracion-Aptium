package com.example.features.equipos.ortopedias.service;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialFilterImplTest {

    @Mock
    private IEstadoValidator estadoValidator;

    private MaterialFilterImpl filter;

    @BeforeEach
    void setUp() {
        filter = new MaterialFilterImpl(estadoValidator);
    }

    // Helpers

    private Material mat(int codigo, EstadoEquipo estado) {
        return new Material(codigo, "Desc-" + codigo, 1, estado);
    }

    private Equipo equipoConMateriales(Material... mats) {
        Equipo e = new Equipo();
        for (Material m : mats) e.agregarMaterial(m);
        return e;
    }

    // ── obtenerQueNecesitanEsterilizado ──────────────────────────────────────

    @Test
    void obtenerQueNecesitanEsterilizado_equipoNull_retornaListaVacia() {
        assertTrue(filter.obtenerQueNecesitanEsterilizado(null).isEmpty());
    }

    @Test
    void obtenerQueNecesitanEsterilizado_sinMateriales_retornaListaVacia() {
        assertTrue(filter.obtenerQueNecesitanEsterilizado(new Equipo()).isEmpty());
    }

    @Test
    void obtenerQueNecesitanEsterilizado_ninguno_retornaListaVacia() {
        Equipo e = equipoConMateriales(mat(1, EstadoEquipo.NUEVO), mat(2, EstadoEquipo.LAVANDO));
        when(estadoValidator.necesitaEsterilizado(any(), eq(e))).thenReturn(false);

        assertTrue(filter.obtenerQueNecesitanEsterilizado(e).isEmpty());
    }

    @Test
    void obtenerQueNecesitanEsterilizado_algunos_retornaFiltrados() {
        Material m1 = mat(1, EstadoEquipo.EMPAQUETADO);
        Material m2 = mat(2, EstadoEquipo.NUEVO);
        Equipo e = equipoConMateriales(m1, m2);

        when(estadoValidator.necesitaEsterilizado(m1, e)).thenReturn(true);
        when(estadoValidator.necesitaEsterilizado(m2, e)).thenReturn(false);

        List<Material> resultado = filter.obtenerQueNecesitanEsterilizado(e);
        assertEquals(1, resultado.size());
        assertSame(m1, resultado.get(0));
    }

    // ── obtenerQueNecesitanEsterilizadoMultiple ───────────────────────────────

    @Test
    void obtenerQueNecesitanEsterilizadoMultiple_listaNull_retornaVacia() {
        assertTrue(filter.obtenerQueNecesitanEsterilizadoMultiple(null).isEmpty());
    }

    @Test
    void obtenerQueNecesitanEsterilizadoMultiple_listaVacia_retornaVacia() {
        assertTrue(filter.obtenerQueNecesitanEsterilizadoMultiple(Collections.emptyList()).isEmpty());
    }

    @Test
    void obtenerQueNecesitanEsterilizadoMultiple_agregaTodos() {
        Material m1 = mat(1, EstadoEquipo.EMPAQUETADO);
        Material m2 = mat(2, EstadoEquipo.EMPAQUETADO);
        Equipo e1 = equipoConMateriales(m1);
        Equipo e2 = equipoConMateriales(m2);

        when(estadoValidator.necesitaEsterilizado(any(), any())).thenReturn(true);

        List<Material> resultado = filter.obtenerQueNecesitanEsterilizadoMultiple(Arrays.asList(e1, e2));
        assertEquals(2, resultado.size());
    }

    // ── obtenerPorEstado ─────────────────────────────────────────────────────

    @Test
    void obtenerPorEstado_equipoNull_retornaVacia() {
        assertTrue(filter.obtenerPorEstado(null, EstadoEquipo.NUEVO).isEmpty());
    }

    @Test
    void obtenerPorEstado_estadoNull_retornaVacia() {
        assertTrue(filter.obtenerPorEstado(new Equipo(), null).isEmpty());
    }

    @Test
    void obtenerPorEstado_filtraPorEstadoExacto() {
        Material m1 = mat(1, EstadoEquipo.NUEVO);
        Material m2 = mat(2, EstadoEquipo.LAVANDO);
        Material m3 = mat(3, EstadoEquipo.NUEVO);
        Equipo e = equipoConMateriales(m1, m2, m3);

        List<Material> resultado = filter.obtenerPorEstado(e, EstadoEquipo.NUEVO);
        assertEquals(2, resultado.size());
        assertTrue(resultado.contains(m1));
        assertTrue(resultado.contains(m3));
        assertFalse(resultado.contains(m2));
    }

    @Test
    void obtenerPorEstado_ningunCoincide_retornaVacia() {
        Equipo e = equipoConMateriales(mat(1, EstadoEquipo.LAVANDO));
        assertTrue(filter.obtenerPorEstado(e, EstadoEquipo.ESTERILIZADO).isEmpty());
    }

    // ── obtenerEntregables ───────────────────────────────────────────────────

    @Test
    void obtenerEntregables_equipoNull_retornaVacia() {
        assertTrue(filter.obtenerEntregables(null).isEmpty());
    }

    @Test
    void obtenerEntregables_ninguno_retornaVacia() {
        Material m = mat(1, EstadoEquipo.NUEVO);
        Equipo e = equipoConMateriales(m);
        when(estadoValidator.esEntregable(EstadoEquipo.NUEVO)).thenReturn(false);

        assertTrue(filter.obtenerEntregables(e).isEmpty());
    }

    @Test
    void obtenerEntregables_algunos_retornaEntregables() {
        Material m1 = mat(1, EstadoEquipo.ESTERILIZADO);
        Material m2 = mat(2, EstadoEquipo.NUEVO);
        Equipo e = equipoConMateriales(m1, m2);

        when(estadoValidator.esEntregable(EstadoEquipo.ESTERILIZADO)).thenReturn(true);
        when(estadoValidator.esEntregable(EstadoEquipo.NUEVO)).thenReturn(false);

        List<Material> resultado = filter.obtenerEntregables(e);
        assertEquals(1, resultado.size());
        assertSame(m1, resultado.get(0));
    }

    // ── obtenerEntregablesMultiple ────────────────────────────────────────────

    @Test
    void obtenerEntregablesMultiple_listaNull_retornaVacia() {
        assertTrue(filter.obtenerEntregablesMultiple(null).isEmpty());
    }

    @Test
    void obtenerEntregablesMultiple_combinaDosEquipos() {
        Material m1 = mat(1, EstadoEquipo.ESTERILIZADO);
        Material m2 = mat(2, EstadoEquipo.ESTERILIZADO);
        Equipo e1 = equipoConMateriales(m1);
        Equipo e2 = equipoConMateriales(m2);

        when(estadoValidator.esEntregable(EstadoEquipo.ESTERILIZADO)).thenReturn(true);

        List<Material> resultado = filter.obtenerEntregablesMultiple(Arrays.asList(e1, e2));
        assertEquals(2, resultado.size());
    }
}
