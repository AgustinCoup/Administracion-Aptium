package com.example.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.features.autoclaves.model.Autoclave;
import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.service.LoteService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LectorDatosOperativosTest {

    @Mock private EquipoService      equipoService;
    @Mock private EquipoOtrosService equipoOtrosService;
    @Mock private AutoclaveService   autoclaveService;
    @Mock private CatalogoService    catalogoService;
    @Mock private LoteService        loteService;

    private LectorDatosOperativos lector;

    private final Equipo      equipo      = mock(Equipo.class);
    private final EquipoOtros equipoOtros = mock(EquipoOtros.class);
    private final Autoclave   autoclave   = mock(Autoclave.class);
    private final Lote        loteActivo  = mock(Lote.class);

    @BeforeEach
    void setUp() {
        lector = new LectorDatosOperativos(
            equipoService, equipoOtrosService, autoclaveService, catalogoService, loteService);
    }

    @Test
    @DisplayName("arma el snapshot completo con lo que devuelve cada service")
    void get_armaElSnapshotCompleto() {
        when(equipoService.obtenerActivos()).thenReturn(List.of(equipo));
        when(equipoOtrosService.obtenerActivos()).thenReturn(List.of(equipoOtros));
        when(autoclaveService.obtenerTodos()).thenReturn(List.of(autoclave));
        when(catalogoService.obtenerVolumenes()).thenReturn(Map.of(100, 5));
        when(loteService.obtenerLotesActivosPorAutoclave()).thenReturn(Map.of("A1", loteActivo));

        DatosOperativos datos = lector.get();

        assertEquals(List.of(equipo), datos.equipos());
        assertEquals(List.of(equipoOtros), datos.equiposOtros());
        assertEquals(List.of(autoclave), datos.autoclaves());
        assertEquals(Map.of(100, 5), datos.volumenesCatalogo());
        assertEquals(Map.of("A1", loteActivo), datos.lotesActivos());
    }

    @Test
    @DisplayName("el refresco de cada guardado no toca el histórico")
    void get_noLeeElHistorico() {
        sinDatos();

        lector.get();

        // El punto de la fase: el costo por guardado deja de crecer con el volumen
        // acumulado. Si alguien vuelve a poner obtenerTodos() acá, esto lo agarra.
        verify(equipoService, never()).obtenerTodos();
        verify(equipoOtrosService, never()).obtenerTodos();
        verify(loteService, never()).obtenerTodosLosLotes();
    }

    @Test
    @DisplayName("cada query se ejecuta exactamente una vez por refresco")
    void get_noRepiteQueries() {
        sinDatos();

        lector.get();

        verify(equipoService, times(1)).obtenerActivos();
        verify(equipoOtrosService, times(1)).obtenerActivos();
        verify(autoclaveService, times(1)).obtenerTodos();
        verify(catalogoService, times(1)).obtenerVolumenes();
        verify(loteService, times(1)).obtenerLotesActivosPorAutoclave();
    }

    @Test
    @DisplayName("el snapshot es inmutable: nadie puede mutar lo que ya se repartió")
    void snapshot_esInmutable() {
        DatosOperativos datos = DatosOperativos.vacio();

        assertThrows(UnsupportedOperationException.class, () -> datos.equipos().add(equipo));
        assertThrows(UnsupportedOperationException.class, () -> datos.autoclaves().add(autoclave));
        assertThrows(UnsupportedOperationException.class, () -> datos.lotesActivos().put("A1", loteActivo));
    }

    @Test
    @DisplayName("un service nulo se rechaza al construir, no al primer refresco")
    void constructor_rechazaServiceNulo() {
        assertThrows(NullPointerException.class, () -> new LectorDatosOperativos(
            null, equipoOtrosService, autoclaveService, catalogoService, loteService));
    }

    @Test
    @DisplayName("el snapshot copia las colecciones: mutar el origen después no lo afecta")
    void snapshot_copiaLasColecciones() {
        List<Equipo> origen = new ArrayList<>(List.of(equipo));
        sinDatos();
        when(equipoService.obtenerActivos()).thenReturn(origen);

        DatosOperativos datos = lector.get();
        origen.clear();

        assertEquals(1, datos.equipos().size());
    }

    private void sinDatos() {
        when(equipoService.obtenerActivos()).thenReturn(List.of());
        when(equipoOtrosService.obtenerActivos()).thenReturn(List.of());
        when(autoclaveService.obtenerTodos()).thenReturn(List.of());
        when(catalogoService.obtenerVolumenes()).thenReturn(Map.of());
        when(loteService.obtenerLotesActivosPorAutoclave()).thenReturn(Map.of());
    }
}
