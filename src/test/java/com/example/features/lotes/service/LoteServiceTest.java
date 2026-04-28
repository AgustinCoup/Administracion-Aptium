package com.example.features.lotes.service;

import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoteServiceTest {

    @Mock
    private LoteDAO loteDAO;

    private LoteService service;

    @BeforeEach
    void setUp() {
        service = new LoteService(loteDAO);
    }

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new LoteService(null));
    }

    @Test
    void obtenerLotesActivosPorAutoclave_delegaADAO() {
        Map<String, Lote> mapa = Collections.emptyMap();
        when(loteDAO.obtenerLotesActivosPorAutoclave()).thenReturn(mapa);
        assertSame(mapa, service.obtenerLotesActivosPorAutoclave());
    }

    @Test
    void obtenerLotesFinalizados_delegaADAO() {
        Lote l1 = mock(Lote.class);
        Lote l2 = mock(Lote.class);
        List<Lote> lista = Arrays.asList(l1, l2);
        when(loteDAO.obtenerLotesFinalizados()).thenReturn(lista);
        assertSame(lista, service.obtenerLotesFinalizados());
    }

    @Test
    void obtenerTodosLosLotes_delegaADAO() {
        Lote l = mock(Lote.class);
        List<Lote> lista = Collections.singletonList(l);
        when(loteDAO.obtenerTodosLosLotes()).thenReturn(lista);
        assertSame(lista, service.obtenerTodosLosLotes());
    }

    @Test
    void obtenerLotesEnRango_delegaADAO() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 12, 31);
        List<Lote> lista = Collections.emptyList();
        when(loteDAO.obtenerLotesEnRango(desde, hasta)).thenReturn(lista);
        assertSame(lista, service.obtenerLotesEnRango(desde, hasta));
    }

    @Test
    void obtenerClientesPorLote_delegaADAO() {
        List<String> clientes = Arrays.asList("A", "B");
        when(loteDAO.obtenerClientesPorLote(7)).thenReturn(clientes);
        assertSame(clientes, service.obtenerClientesPorLote(7));
    }

    @Test
    void obtenerMaterialesPorLote_delegaADAO() {
        List<LoteMaterialInfo> mats = Collections.emptyList();
        when(loteDAO.obtenerMaterialesPorLote(3)).thenReturn(mats);
        assertSame(mats, service.obtenerMaterialesPorLote(3));
    }

    @Test
    void lanzarLote_delegaADAO() {
        Lote lote = mock(Lote.class);
        List<LoteMovimiento> movs = List.of(new LoteMovimiento(1, 1, 10));
        when(loteDAO.lanzarLote("AutoA", 500, 300, movs)).thenReturn(lote);
        assertSame(lote, service.lanzarLote("AutoA", 500, 300, movs));
    }

    @Test
    void lanzarLote_movimientosVacios_lanzaValidationException() {
        assertThrows(com.example.common.exception.ValidationException.class,
            () -> service.lanzarLote("AutoA", 500, 300, Collections.emptyList()));
    }

    @Test
    void finalizarLote_delegaADAO() {
        when(loteDAO.finalizarLote(10)).thenReturn(true);
        assertTrue(service.finalizarLote(10));
    }

    @Test
    void marcarLoteFallo_delegaADAO() {
        when(loteDAO.marcarLoteFallo(10)).thenReturn(true);
        assertTrue(service.marcarLoteFallo(10));
    }
}
