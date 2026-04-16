package com.example.features.equipos.otros.service;

import com.example.common.exception.ValidationException;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
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
class EquipoOtrosServiceTest {

    @Mock
    private EquipoOtrosDAO dao;

    private EquipoOtrosService service;

    @BeforeEach
    void setUp() {
        service = new EquipoOtrosService(dao);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new EquipoOtrosService(null));
    }

    // ── guardarEquipo — validaciones comunes ─────────────────────────────────

    @Test
    void guardarEquipo_clienteCero_lanzaValidationException() {
        EquipoOtros e = equipoDetalles(0);
        e.agregarMaterial(materialOtros());
        ValidationException ex = assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
        assertTrue(ex.getValidationErrors().stream().anyMatch(s -> s.contains("Cliente")));
    }

    @Test
    void guardarEquipo_clienteNegativo_lanzaValidationException() {
        EquipoOtros e = equipoDetalles(-1);
        e.agregarMaterial(materialOtros());
        assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
    }

    // ── guardarEquipo — modalidad DETALLES ───────────────────────────────────

    @Test
    void guardarEquipo_detalles_sinMateriales_lanzaValidationException() {
        EquipoOtros e = equipoDetalles(1);
        // sin materiales
        ValidationException ex = assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
        assertTrue(ex.getValidationErrors().stream().anyMatch(s -> s.contains("material")));
    }

    @Test
    void guardarEquipo_detalles_valido_delegaADAO() {
        EquipoOtros e = equipoDetalles(1);
        e.agregarMaterial(materialOtros());
        when(dao.guardar(e)).thenReturn(true);

        assertTrue(service.guardarEquipo(e));
        verify(dao).guardar(e);
    }

    // ── guardarEquipo — modalidad REMITO ─────────────────────────────────────

    @Test
    void guardarEquipo_remito_cantidadNull_lanzaValidationException() {
        EquipoOtros e = equipoRemito(1, null);
        assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
    }

    @Test
    void guardarEquipo_remito_cantidadCero_lanzaValidationException() {
        EquipoOtros e = equipoRemito(1, 0);
        assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
    }

    @Test
    void guardarEquipo_remito_cantidadNegativa_lanzaValidationException() {
        EquipoOtros e = equipoRemito(1, -5);
        assertThrows(ValidationException.class, () -> service.guardarEquipo(e));
    }

    @Test
    void guardarEquipo_remito_valido_delegaADAO() {
        EquipoOtros e = equipoRemito(1, 10);
        when(dao.guardar(e)).thenReturn(true);

        assertTrue(service.guardarEquipo(e));
        verify(dao).guardar(e);
    }

    // ── remito no exige materiales detallados ─────────────────────────────────

    @Test
    void guardarEquipo_remito_sinMateriales_noLanzaExcepcion() {
        // REMITO sin materiales es válido si tiene cantidadRemito > 0
        EquipoOtros e = equipoRemito(1, 5);
        when(dao.guardar(e)).thenReturn(true);
        assertDoesNotThrow(() -> service.guardarEquipo(e));
    }

    // ── obtenerTodos ─────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_delegaADAO() {
        List<EquipoOtros> lista = Arrays.asList(new EquipoOtros(), new EquipoOtros());
        when(dao.obtenerTodos()).thenReturn(lista);
        assertEquals(lista, service.obtenerTodos());
    }

    // ── entregarClienteCompleto ───────────────────────────────────────────────

    @Test
    void entregarClienteCompleto_delegaADAO() {
        when(dao.entregarClienteCompleto(3)).thenReturn(true);
        assertTrue(service.entregarClienteCompleto(3));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private EquipoOtros equipoDetalles(int nroCliente) {
        EquipoOtros e = new EquipoOtros();
        e.setNroCliente(nroCliente);
        e.setTipoIngreso(TipoIngresoOtros.DETALLES);
        return e;
    }

    private EquipoOtros equipoRemito(int nroCliente, Integer cantidad) {
        EquipoOtros e = new EquipoOtros();
        e.setNroCliente(nroCliente);
        e.setTipoIngreso(TipoIngresoOtros.REMITO);
        e.setRemitoCantidad(cantidad);
        return e;
    }

    private MaterialOtros materialOtros() {
        return new MaterialOtros(null, "Descripcion", 1, EstadoEquipo.NUEVO);
    }
}
