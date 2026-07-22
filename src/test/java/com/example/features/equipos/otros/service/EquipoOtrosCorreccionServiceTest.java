package com.example.features.equipos.otros.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipoOtrosCorreccionServiceTest {

    @Mock private EquipoOtrosDAO equipoOtrosDAO;
    @Mock private AuditoriaDAO   auditoriaDAO;

    private EquipoOtrosCorreccionService service;

    @BeforeEach
    void setUp() {
        service = new EquipoOtrosCorreccionService(equipoOtrosDAO, auditoriaDAO);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_equipoOtrosDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoOtrosCorreccionService(null, auditoriaDAO));
    }

    @Test
    void constructor_auditoriaDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoOtrosCorreccionService(equipoOtrosDAO, null));
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

    // ── Errores de BD: no se tragan ni se disfrazan de ValidationException ────
    //
    // Antes del refactor los tres helpers con SQL tragaban la SQLException y
    // devolvían false / lista vacía / "no existe". Ahora el DAO lanza
    // DatabaseException y el service la propaga sin convertirla.

    @Test
    void modificarCantidadRemito_errorAlVerificarMateriales_propagaDatabaseException() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConTipo(TipoIngresoOtros.REMITO));
        when(equipoOtrosDAO.tieneMateriales(1))
            .thenThrow(new DatabaseException("Error al verificar movimientos del remito"));

        assertThrows(DatabaseException.class,
            () -> service.modificarCantidadRemito(1, 10, "motivo"));

        // El guard falla CERRADO: no se toca la cantidad del remito.
        verify(equipoOtrosDAO, never()).actualizarCantidadRemito(anyInt(), anyInt());
    }

    @Test
    void modificarCantidadMaterial_errorAlLeerCantidad_propagaDatabaseException() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(equipoOtrosDAO.obtenerCantidadMaterial(5, 1))
            .thenThrow(new DatabaseException("Error al obtener la cantidad del material"));

        assertThrows(DatabaseException.class,
            () -> service.modificarCantidadMaterial(1, 5, 7, "motivo"));

        verify(equipoOtrosDAO, never()).actualizarCantidadMaterial(anyInt(), anyInt(), anyInt());
    }

    @Test
    void modificarCantidadMaterial_materialInexistente_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(equipoOtrosDAO.obtenerCantidadMaterial(5, 1)).thenReturn(null);

        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 5, 7, "motivo"));
    }

    @Test
    void eliminarMaterial_errorAlBuscarMateriales_propagaDatabaseException() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(equipoOtrosDAO.obtenerMaterialesPorDescripcion(1, "Guante"))
            .thenThrow(new DatabaseException("Error al obtener los materiales del equipo"));

        assertThrows(DatabaseException.class,
            () -> service.eliminarMaterial(1, "Guante", "motivo"));

        // No se escriben snapshots de algo que no se pudo leer.
        verify(auditoriaDAO, never()).registrarMaterialEliminado(
            anyInt(), anyInt(), any(), any(), anyInt(), any(), anyString(), anyString());
        verify(equipoOtrosDAO, never()).eliminarMaterialesPorDescripcion(anyInt(), anyString());
    }

    @Test
    void eliminarMaterial_sinCoincidencias_lanzaValidation() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(equipoOtrosDAO.obtenerMaterialesPorDescripcion(1, "Guante"))
            .thenReturn(Collections.emptyList());

        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, "Guante", "motivo"));
    }

    // ── Camino feliz: el service orquesta DAO + auditoría ─────────────────────

    @Test
    void eliminarMaterial_conCoincidencias_snapshotAntesDelDelete() {
        when(equipoOtrosDAO.obtenerPorId(1)).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        MaterialOtros m = new MaterialOtros(7, null, "Guante", 3, EstadoEquipo.NUEVO, null);
        when(equipoOtrosDAO.obtenerMaterialesPorDescripcion(1, "Guante"))
            .thenReturn(Collections.singletonList(m));

        assertTrue(service.eliminarMaterial(1, "Guante", "motivo"));

        InOrder orden = inOrder(auditoriaDAO, equipoOtrosDAO);
        orden.verify(auditoriaDAO).registrarMaterialEliminado(
            1, 7, null, "Guante", 3, EstadoEquipo.NUEVO.getNombre(), "motivo", "OTROS");
        orden.verify(equipoOtrosDAO).eliminarMaterialesPorDescripcion(1, "Guante");
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
