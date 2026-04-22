package com.example.features.equipos.otros.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipoOtrosCorreccionServiceTest {

    @Mock private EquipoOtrosDAO   equipoOtrosDAO;
    @Mock private AuditoriaDAO     auditoriaDAO;
    @Mock private CatalogoOtrosDAO catalogoOtrosDAO;

    private EquipoOtrosCorreccionService service;

    @BeforeEach
    void setUp() {
        service = new EquipoOtrosCorreccionService(equipoOtrosDAO, auditoriaDAO, catalogoOtrosDAO);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_equipoOtrosDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoOtrosCorreccionService(null, auditoriaDAO, catalogoOtrosDAO));
    }

    @Test
    void constructor_auditoriaDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoOtrosCorreccionService(equipoOtrosDAO, null, catalogoOtrosDAO));
    }

    @Test
    void constructor_catalogoOtrosDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoOtrosCorreccionService(equipoOtrosDAO, auditoriaDAO, null));
    }

    // ── obtenerEquiposOtrosNuevos ────────────────────────────────────────────

    @Test
    void obtenerEquiposNuevos_delegaADAO() {
        List<EquipoOtros> lista = Arrays.asList(new EquipoOtros(), new EquipoOtros());
        when(equipoOtrosDAO.obtenerEquiposNuevos()).thenReturn(lista);
        assertEquals(lista, service.obtenerEquiposOtrosNuevos());
        verify(equipoOtrosDAO).obtenerEquiposNuevos();
    }

    // ── modificarCantidadRemito — validaciones ───────────────────────────────

    @Test
    void modificarCantidadRemito_cantidadCero_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(1, 0, "motivo"));
    }

    @Test
    void modificarCantidadRemito_cantidadNegativa_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(1, -5, "motivo"));
    }

    @Test
    void modificarCantidadRemito_motivoVacio_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(1, 5, "  "));
    }

    @Test
    void modificarCantidadRemito_motivoNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(1, 5, null));
    }

    @Test
    void modificarCantidadRemito_equipoNoExiste_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(null);
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(1, 5, "motivo"));
    }

    @Test
    void modificarCantidadRemito_equipoNoEsNuevo_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.LAVANDO));
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(1, 5, "motivo"));
    }

    @Test
    void modificarCantidadRemito_tipoNoEsRemito_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConTipo(TipoIngresoOtros.DETALLES));
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadRemito(1, 5, "motivo"));
    }

    // ── modificarCantidadMaterial — validaciones ─────────────────────────────

    @Test
    void modificarCantidadMaterial_cantidadCero_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 0, "motivo"));
    }

    @Test
    void modificarCantidadMaterial_cantidadNegativa_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, -3, "motivo"));
    }

    @Test
    void modificarCantidadMaterial_motivoVacio_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, ""));
    }

    @Test
    void modificarCantidadMaterial_motivoNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, null));
    }

    @Test
    void modificarCantidadMaterial_equipoNoExiste_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(null);
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, "motivo"));
    }

    @Test
    void modificarCantidadMaterial_equipoNoEsNuevo_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.EMPAQUETADO));
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, "motivo"));
    }

    // ── agregarMaterial — validaciones ───────────────────────────────────────

    @Test
    void agregarMaterial_descripcionVacia_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterial(1, "  ", 1, "motivo"));
    }

    @Test
    void agregarMaterial_descripcionNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterial(1, null, 1, "motivo"));
    }

    @Test
    void agregarMaterial_cantidadCero_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterial(1, "Guante", 0, "motivo"));
    }

    @Test
    void agregarMaterial_motivoVacio_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterial(1, "Guante", 1, ""));
    }

    @Test
    void agregarMaterial_equipoNoExiste_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(null);
        assertThrows(ValidationException.class,
            () -> service.agregarMaterial(1, "Guante", 1, "motivo"));
    }

    @Test
    void agregarMaterial_equipoNoEsNuevo_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.ESTERILIZANDO));
        assertThrows(ValidationException.class,
            () -> service.agregarMaterial(1, "Guante", 1, "motivo"));
    }

    // ── eliminarMaterial — validaciones ──────────────────────────────────────

    @Test
    void eliminarMaterial_descripcionVacia_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, "  ", "motivo"));
    }

    @Test
    void eliminarMaterial_descripcionNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, null, "motivo"));
    }

    @Test
    void eliminarMaterial_motivoVacio_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, "Guante", ""));
    }

    @Test
    void eliminarMaterial_equipoNoExiste_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(null);
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, "Guante", "motivo"));
    }

    @Test
    void eliminarMaterial_equipoNoEsNuevo_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.LAVADO));
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, "Guante", "motivo"));
    }

    // ── eliminarEquipo — validaciones ─────────────────────────────────────────

    @Test
    void eliminarEquipo_motivoVacio_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarEquipo(1, ""));
    }

    @Test
    void eliminarEquipo_motivoNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarEquipo(1, null));
    }

    @Test
    void eliminarEquipo_equipoNoExiste_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(null);
        assertThrows(ValidationException.class,
            () -> service.eliminarEquipo(1, "motivo"));
    }

    @Test
    void eliminarEquipo_equipoNoEsNuevo_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.ESTERILIZADO));
        assertThrows(ValidationException.class,
            () -> service.eliminarEquipo(1, "motivo"));
    }

    @Test
    void eliminarEquipo_snapshotFalla_lanzaDatabaseException() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(auditoriaDAO.registrarEquipoEliminado(any(), anyInt(), any(),
            isNull(), isNull(), isNull(), isNull(),
            anyString(), anyString(), anyString())).thenReturn(false);
        assertThrows(DatabaseException.class,
            () -> service.eliminarEquipo(1, "motivo"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private EquipoOtros equipoConEstado(EstadoEquipo estado) {
        EquipoOtros e = new EquipoOtros();
        e.setId(1);
        e.setNroCliente(1);
        e.setClienteNombre("Cliente Test");
        e.setEstado(estado);
        return e;
    }

    private EquipoOtros equipoConTipo(TipoIngresoOtros tipo) {
        EquipoOtros e = equipoConEstado(EstadoEquipo.NUEVO);
        e.setTipoIngreso(tipo);
        return e;
    }
}
