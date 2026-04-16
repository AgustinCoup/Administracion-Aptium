package com.example.features.equipos.ortopedias.service;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EstadoValidatorImplTest {

    private EstadoValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new EstadoValidatorImpl();
    }

    // Helpers

    private Material materialConEstado(EstadoEquipo estado) {
        return new Material(1, 100, "Desc", 1, estado);
    }

    private Equipo equipoConLavadoYEmpaque(boolean lavado, boolean empaque) {
        Equipo e = new Equipo();
        e.setRequiereLavado(lavado);
        e.setRequiereEmpaque(empaque);
        return e;
    }

    // ── puedeAvanzar ─────────────────────────────────────────────────────────

    @Test
    void puedeAvanzar_materialNull_retornaFalse() {
        assertFalse(validator.puedeAvanzar(null, new Equipo()));
    }

    @Test
    void puedeAvanzar_equipoNull_retornaFalse() {
        assertFalse(validator.puedeAvanzar(materialConEstado(EstadoEquipo.NUEVO), null));
    }

    @Test
    void puedeAvanzar_estadoNuevoConLavado_retornaTrue() {
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertTrue(validator.puedeAvanzar(materialConEstado(EstadoEquipo.NUEVO), e));
    }

    @Test
    void puedeAvanzar_estadoEsterilizado_retornaFalse() {
        // getSiguienteEstado de ESTERILIZADO es null
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertFalse(validator.puedeAvanzar(materialConEstado(EstadoEquipo.ESTERILIZADO), e));
    }

    // ── obtenerProximoEstado ──────────────────────────────────────────────────

    @Test
    void obtenerProximoEstado_materialNull_retornaNull() {
        assertNull(validator.obtenerProximoEstado(null, new Equipo()));
    }

    @Test
    void obtenerProximoEstado_equipoNull_retornaNull() {
        assertNull(validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.NUEVO), null));
    }

    @Test
    void obtenerProximoEstado_nuevo_conLavado_esLavando() {
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertEquals(EstadoEquipo.LAVANDO, validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.NUEVO), e));
    }

    @Test
    void obtenerProximoEstado_nuevo_sinLavadoConEmpaque_esEmpaquetado() {
        // requiereLavado=false, requiereEmpaque=true → empaqueEfectivo=true
        Equipo e = equipoConLavadoYEmpaque(false, true);
        assertEquals(EstadoEquipo.EMPAQUETADO, validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.NUEVO), e));
    }

    @Test
    void obtenerProximoEstado_nuevo_sinLavadoSinEmpaque_esEsterilizando() {
        // requiereLavado=false, requiereEmpaque=false → empaqueEfectivo=false
        Equipo e = equipoConLavadoYEmpaque(false, false);
        assertEquals(EstadoEquipo.ESTERILIZANDO, validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.NUEVO), e));
    }

    @Test
    void obtenerProximoEstado_lavando_esLavado() {
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertEquals(EstadoEquipo.LAVADO, validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.LAVANDO), e));
    }

    @Test
    void obtenerProximoEstado_empaquetado_esEsterilizando() {
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertEquals(EstadoEquipo.ESTERILIZANDO, validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.EMPAQUETADO), e));
    }

    @Test
    void obtenerProximoEstado_esterilizando_esEsterilizado() {
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertEquals(EstadoEquipo.ESTERILIZADO, validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.ESTERILIZANDO), e));
    }

    @Test
    void obtenerProximoEstado_esterilizado_esNull() {
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertNull(validator.obtenerProximoEstado(materialConEstado(EstadoEquipo.ESTERILIZADO), e));
    }

    // ── esEntregable ─────────────────────────────────────────────────────────

    @Test
    void esEntregable_null_retornaFalse() {
        assertFalse(validator.esEntregable(null));
    }

    @Test
    void esEntregable_nuevo_retornaFalse() {
        assertFalse(validator.esEntregable(EstadoEquipo.NUEVO));
    }

    @Test
    void esEntregable_esterilizando_retornaFalse() {
        assertFalse(validator.esEntregable(EstadoEquipo.ESTERILIZANDO));
    }

    @Test
    void esEntregable_esterilizado_retornaTrue() {
        assertTrue(validator.esEntregable(EstadoEquipo.ESTERILIZADO));
    }

    @Test
    void esEntregable_entregado_retornaTrue() {
        assertTrue(validator.esEntregable(EstadoEquipo.ENTREGADO));
    }

    // ── esFinal ──────────────────────────────────────────────────────────────

    @Test
    void esFinal_null_retornaFalse() {
        assertFalse(validator.esFinal(null));
    }

    @Test
    void esFinal_entregado_retornaTrue() {
        assertTrue(validator.esFinal(EstadoEquipo.ENTREGADO));
    }

    @Test
    void esFinal_esterilizado_retornaFalse() {
        assertFalse(validator.esFinal(EstadoEquipo.ESTERILIZADO));
    }

    @Test
    void esFinal_nuevo_retornaFalse() {
        assertFalse(validator.esFinal(EstadoEquipo.NUEVO));
    }

    // ── necesitaEsterilizado ─────────────────────────────────────────────────

    @Test
    void necesitaEsterilizado_materialNull_retornaFalse() {
        assertFalse(validator.necesitaEsterilizado(null, new Equipo()));
    }

    @Test
    void necesitaEsterilizado_equipoNull_retornaFalse() {
        assertFalse(validator.necesitaEsterilizado(materialConEstado(EstadoEquipo.EMPAQUETADO), null));
    }

    @Test
    void necesitaEsterilizado_empaquetado_retornaTrue() {
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertTrue(validator.necesitaEsterilizado(materialConEstado(EstadoEquipo.EMPAQUETADO), e));
    }

    @Test
    void necesitaEsterilizado_nuevo_sinLavadoSinEmpaque_retornaTrue() {
        // NUEVO → directo a ESTERILIZANDO
        Equipo e = equipoConLavadoYEmpaque(false, false);
        assertTrue(validator.necesitaEsterilizado(materialConEstado(EstadoEquipo.NUEVO), e));
    }

    @Test
    void necesitaEsterilizado_nuevo_conLavado_retornaFalse() {
        // NUEVO → LAVANDO, no ESTERILIZANDO
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertFalse(validator.necesitaEsterilizado(materialConEstado(EstadoEquipo.NUEVO), e));
    }

    @Test
    void necesitaEsterilizado_esterilizado_retornaFalse() {
        // No puede avanzar más
        Equipo e = equipoConLavadoYEmpaque(true, true);
        assertFalse(validator.necesitaEsterilizado(materialConEstado(EstadoEquipo.ESTERILIZADO), e));
    }
}
