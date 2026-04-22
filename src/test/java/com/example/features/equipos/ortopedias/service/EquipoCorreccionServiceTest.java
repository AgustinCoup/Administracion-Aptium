package com.example.features.equipos.ortopedias.service;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.dao.MaterialDAO;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipoCorreccionServiceTest {

    @Mock private EquipoDAO    equipoDAO;
    @Mock private MaterialDAO  materialDAO;
    @Mock private AuditoriaDAO auditoriaDAO;
    @Mock private CatalogoDAO  catalogoDAO;

    private EquipoCorreccionService service;

    @BeforeEach
    void setUp() {
        service = new EquipoCorreccionService(equipoDAO, materialDAO, auditoriaDAO, catalogoDAO);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_equipoDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoCorreccionService(null, materialDAO, auditoriaDAO, catalogoDAO));
    }

    @Test
    void constructor_materialDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoCorreccionService(equipoDAO, null, auditoriaDAO, catalogoDAO));
    }

    @Test
    void constructor_auditoriaDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoCorreccionService(equipoDAO, materialDAO, null, catalogoDAO));
    }

    @Test
    void constructor_catalogoDAONull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new EquipoCorreccionService(equipoDAO, materialDAO, auditoriaDAO, null));
    }

    // ── modificarCantidadMaterial — validaciones ──────────────────────────────

    @Test
    void modificarCantidad_equipoIdNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(null, 1, 5, "motivo"));
    }

    @Test
    void modificarCantidad_materialIdNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, null, 5, "motivo"));
    }

    @Test
    void modificarCantidad_cantidadNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, null, "motivo"));
    }

    @Test
    void modificarCantidad_cantidadCero_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 0, "motivo"));
    }

    @Test
    void modificarCantidad_cantidadNegativa_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, -3, "motivo"));
    }

    @Test
    void modificarCantidad_motivoVacio_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, "  "));
    }

    @Test
    void modificarCantidad_motivoNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, null));
    }

    @Test
    void modificarCantidad_equipoNoEsNuevo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.LAVANDO));
        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, "motivo"));
    }

    @Test
    void modificarCantidad_materialNoExiste_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(materialDAO.obtenerCantidad(1)).thenReturn(null);

        assertThrows(ValidationException.class,
            () -> service.modificarCantidadMaterial(1, 1, 5, "motivo"));
    }

    @Test
    void modificarCantidad_valido_actualizaYAudita() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(materialDAO.obtenerCantidad(2)).thenReturn(3);

        boolean resultado = service.modificarCantidadMaterial(1, 2, 5, "corrección");

        assertTrue(resultado);
        verify(materialDAO).actualizarCantidad(2, 5);
        verify(auditoriaDAO).registrarCambio(eq(1), eq(2), eq("MODIFICACION_CANTIDAD"),
            eq("cantidad"), eq("3"), eq("5"), eq("corrección"), eq("ORTOPEDIA"));
    }

    // ── modificarCodigoMaterial — validaciones ────────────────────────────────

    @Test
    void modificarCodigo_equipoIdNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCodigoMaterial(null, 1, 200, "motivo"));
    }

    @Test
    void modificarCodigo_codigoNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCodigoMaterial(1, 1, null, "motivo"));
    }

    @Test
    void modificarCodigo_codigoCero_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.modificarCodigoMaterial(1, 1, 0, "motivo"));
    }

    @Test
    void modificarCodigo_equipoNoEsNuevo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.EMPAQUETADO));
        assertThrows(ValidationException.class,
            () -> service.modificarCodigoMaterial(1, 1, 200, "motivo"));
    }

    @Test
    void modificarCodigo_materialNoExiste_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(materialDAO.obtenerMaterial(1)).thenReturn(null);

        assertThrows(ValidationException.class,
            () -> service.modificarCodigoMaterial(1, 1, 200, "motivo"));
    }

    @Test
    void modificarCodigo_codigoNuevoNoExisteEnCatalogo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(materialDAO.obtenerMaterial(1)).thenReturn(new Object[]{100, null, "DescVieja"});
        when(catalogoDAO.obtenerDescripcion(200)).thenReturn(null);

        assertThrows(ValidationException.class,
            () -> service.modificarCodigoMaterial(1, 1, 200, "motivo"));
    }

    @Test
    void modificarCodigo_valido_actualizaYAudita() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(materialDAO.obtenerMaterial(1)).thenReturn(new Object[]{100, null, "DescVieja"});
        when(catalogoDAO.obtenerDescripcion(200)).thenReturn("DescNueva");

        boolean resultado = service.modificarCodigoMaterial(1, 1, 200, "motivo");

        assertTrue(resultado);
        verify(materialDAO).actualizarCodigo(1, 200);
        verify(auditoriaDAO).registrarCambio(eq(1), eq(1), eq("MODIFICACION_CODIGO"),
            eq("codigo_catalogo"), anyString(), anyString(), eq("motivo"), eq("ORTOPEDIA"));
    }

    // ── eliminarEquipo ────────────────────────────────────────────────────────

    @Test
    void eliminarEquipo_equipoIdNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarEquipo(null, "motivo"));
    }

    @Test
    void eliminarEquipo_motivoVacio_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarEquipo(1, ""));
    }

    @Test
    void eliminarEquipo_equipoNoEsNuevo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.ESTERILIZANDO));
        assertThrows(ValidationException.class,
            () -> service.eliminarEquipo(1, "motivo"));
    }

    @Test
    void eliminarEquipo_snapshotFalla_lanzaDatabaseException() {
        Equipo e = equipoConEstado(EstadoEquipo.NUEVO);
        when(equipoDAO.obtenerPorId("1")).thenReturn(e);
        when(auditoriaDAO.registrarEquipoEliminado(any(), anyInt(), any(), any(), any(),
            any(), any(), any(), anyString(), anyString())).thenReturn(false);

        assertThrows(DatabaseException.class,
            () -> service.eliminarEquipo(1, "motivo"));
    }

    @Test
    void eliminarEquipo_valido_eliminaYAudita() {
        Equipo e = equipoConEstado(EstadoEquipo.NUEVO);
        e.agregarMaterial(new Material(1, 100, "Mat", 2, EstadoEquipo.NUEVO));
        when(equipoDAO.obtenerPorId("1")).thenReturn(e);
        when(auditoriaDAO.registrarEquipoEliminado(any(), anyInt(), any(), any(), any(),
            any(), any(), any(), anyString(), anyString())).thenReturn(true);
        when(auditoriaDAO.registrarMaterialEliminado(any(), any(), anyInt(), any(), any(),
            any(), anyString(), anyString())).thenReturn(true);

        assertTrue(service.eliminarEquipo(1, "baja por error"));
        verify(equipoDAO).eliminar("1");
    }

    // ── eliminarMaterial ──────────────────────────────────────────────────────

    @Test
    void eliminarMaterial_equipoIdNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(null, 100, "motivo"));
    }

    @Test
    void eliminarMaterial_codigoNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, null, "motivo"));
    }

    @Test
    void eliminarMaterial_equipoNoEsNuevo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.LAVANDO));
        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, 100, "motivo"));
    }

    @Test
    void eliminarMaterial_sinMaterialesConEseCodigo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(materialDAO.obtenerMaterialesPorCodigo(1, 100)).thenReturn(Collections.emptyList());

        assertThrows(ValidationException.class,
            () -> service.eliminarMaterial(1, 100, "motivo"));
    }

    @Test
    void eliminarMaterial_valido_eliminaYAudita() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        List<Object[]> mats = Collections.singletonList(
            new Object[]{5, 100, "MatDesc", 3, "Nuevo"}
        );
        when(materialDAO.obtenerMaterialesPorCodigo(1, 100)).thenReturn(mats);
        when(auditoriaDAO.registrarMaterialEliminado(any(), any(), anyInt(), any(), any(),
            any(), anyString(), anyString())).thenReturn(true);

        assertTrue(service.eliminarMaterial(1, 100, "motivo"));
        verify(materialDAO).eliminarMaterialesPorCodigo(1, 100);
    }

    // ── agregarMaterialAEquipo ────────────────────────────────────────────────

    @Test
    void agregarMaterial_equipoIdNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterialAEquipo(null, 100, 2, "motivo"));
    }

    @Test
    void agregarMaterial_codigoNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterialAEquipo(1, null, 2, "motivo"));
    }

    @Test
    void agregarMaterial_codigoCero_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterialAEquipo(1, 0, 2, "motivo"));
    }

    @Test
    void agregarMaterial_cantidadNull_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterialAEquipo(1, 100, null, "motivo"));
    }

    @Test
    void agregarMaterial_cantidadCero_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.agregarMaterialAEquipo(1, 100, 0, "motivo"));
    }

    @Test
    void agregarMaterial_equipoNoEsNuevo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.LAVADO));
        assertThrows(ValidationException.class,
            () -> service.agregarMaterialAEquipo(1, 100, 2, "motivo"));
    }

    @Test
    void agregarMaterial_codigoNoExisteEnCatalogo_lanzaValidation() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(catalogoDAO.obtenerDescripcion(100)).thenReturn(null);

        assertThrows(ValidationException.class,
            () -> service.agregarMaterialAEquipo(1, 100, 2, "motivo"));
    }

    @Test
    void agregarMaterial_valido_insertaYAudita() {
        when(equipoDAO.obtenerPorId("1")).thenReturn(equipoConEstado(EstadoEquipo.NUEVO));
        when(catalogoDAO.obtenerDescripcion(100)).thenReturn("Guante");
        when(materialDAO.agregarMaterial(1, 100, 2)).thenReturn(99);

        assertTrue(service.agregarMaterialAEquipo(1, 100, 2, "reposición"));
        verify(auditoriaDAO).registrarCambio(eq(1), eq(99), eq("ADICION_MATERIAL"),
            eq("material_nuevo"), isNull(), eq("2"), eq("reposición"), eq("ORTOPEDIA"));
    }

    // ── consultas ─────────────────────────────────────────────────────────────

    @Test
    void obtenerEquiposNuevos_delegaADAO() {
        List<Equipo> lista = Arrays.asList(new Equipo(), new Equipo());
        when(equipoDAO.obtenerEquiposNuevos()).thenReturn(lista);
        assertEquals(lista, service.obtenerEquiposNuevos());
    }

    @Test
    void obtenerAuditoriaEquipo_idNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.obtenerAuditoriaEquipo(null));
    }

    @Test
    void obtenerDescripcionMaterial_delegaADAO() {
        when(catalogoDAO.obtenerDescripcion(100)).thenReturn("Guante");
        assertEquals("Guante", service.obtenerDescripcionMaterial(100));
    }

    @Test
    void obtenerDescripcionMaterial_daoLanzaException_retornaNull() {
        when(catalogoDAO.obtenerDescripcion(100)).thenThrow(new DatabaseException("error BD"));
        assertNull(service.obtenerDescripcionMaterial(100));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Equipo equipoConEstado(EstadoEquipo estado) {
        Equipo e = new Equipo();
        e.setId(1);
        e.setNroCliente(1);
        e.setClienteNombre("Cliente Test");
        e.setEstado(estado);
        return e;
    }
}
