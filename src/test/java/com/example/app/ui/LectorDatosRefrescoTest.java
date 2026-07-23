package com.example.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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
class LectorDatosRefrescoTest {

    @Mock private EquipoService      equipoService;
    @Mock private EquipoOtrosService equipoOtrosService;
    @Mock private AutoclaveService   autoclaveService;
    @Mock private CatalogoService    catalogoService;
    @Mock private LoteService        loteService;

    private LectorDatosRefresco lector;

    private final Equipo      equipo      = mock(Equipo.class);
    private final EquipoOtros equipoOtros = mock(EquipoOtros.class);
    private final Autoclave   autoclave   = mock(Autoclave.class);
    private final Lote        loteActivo  = mock(Lote.class);
    private final Lote        loteViejo   = mock(Lote.class);

    @BeforeEach
    void setUp() {
        lector = new LectorDatosRefresco(
            equipoService, equipoOtrosService, autoclaveService, catalogoService, loteService);
    }

    @Test
    @DisplayName("arma el snapshot completo con lo que devuelve cada service")
    void leer_armaElSnapshotCompleto() {
        when(equipoService.obtenerTodos()).thenReturn(List.of(equipo));
        when(equipoOtrosService.obtenerTodos()).thenReturn(List.of(equipoOtros));
        when(autoclaveService.obtenerTodos()).thenReturn(List.of(autoclave));
        when(catalogoService.obtenerVolumenes()).thenReturn(Map.of(100, 5));
        when(loteService.obtenerLotesActivosPorAutoclave()).thenReturn(Map.of("A1", loteActivo));
        when(loteService.obtenerTodosLosLotes()).thenReturn(List.of(loteActivo, loteViejo));

        DatosRefresco datos = lector.leer();

        assertEquals(List.of(equipo), datos.equipos());
        assertEquals(List.of(equipoOtros), datos.equiposOtros());
        assertEquals(List.of(autoclave), datos.autoclaves());
        assertEquals(Map.of(100, 5), datos.volumenesCatalogo());
        assertEquals(Map.of("A1", loteActivo), datos.lotesActivos());
        assertEquals(List.of(loteActivo, loteViejo), datos.todosLosLotes());
    }

    @Test
    @DisplayName("cada query se ejecuta exactamente una vez por refresco")
    void leer_noRepiteQueries() {
        when(equipoService.obtenerTodos()).thenReturn(List.of());
        when(equipoOtrosService.obtenerTodos()).thenReturn(List.of());
        when(autoclaveService.obtenerTodos()).thenReturn(List.of());
        when(catalogoService.obtenerVolumenes()).thenReturn(Map.of());
        when(loteService.obtenerLotesActivosPorAutoclave()).thenReturn(Map.of());
        when(loteService.obtenerTodosLosLotes()).thenReturn(List.of());

        lector.leer();

        verify(equipoService, times(1)).obtenerTodos();
        verify(equipoOtrosService, times(1)).obtenerTodos();
        verify(autoclaveService, times(1)).obtenerTodos();
        verify(catalogoService, times(1)).obtenerVolumenes();
        verify(loteService, times(1)).obtenerLotesActivosPorAutoclave();
        verify(loteService, times(1)).obtenerTodosLosLotes();
    }

    @Test
    @DisplayName("el snapshot es inmutable: nadie puede mutar lo que ya se repartió")
    void snapshot_esInmutable() {
        DatosRefresco datos = DatosRefresco.vacio();

        assertThrows(UnsupportedOperationException.class, () -> datos.equipos().add(equipo));
        assertThrows(UnsupportedOperationException.class, () -> datos.todosLosLotes().add(loteActivo));
        assertThrows(UnsupportedOperationException.class, () -> datos.lotesActivos().put("A1", loteActivo));
    }

    @Test
    @DisplayName("un service nulo se rechaza al construir, no al primer refresco")
    void constructor_rechazaServiceNulo() {
        assertThrows(NullPointerException.class, () -> new LectorDatosRefresco(
            null, equipoOtrosService, autoclaveService, catalogoService, loteService));
    }

    @Test
    @DisplayName("el snapshot copia las colecciones: mutar el origen despues no lo afecta")
    void snapshot_copiaLasColecciones() {
        List<Equipo> origen = new ArrayList<>(List.of(equipo));
        when(equipoService.obtenerTodos()).thenReturn(origen);
        when(equipoOtrosService.obtenerTodos()).thenReturn(List.of());
        when(autoclaveService.obtenerTodos()).thenReturn(List.of());
        when(catalogoService.obtenerVolumenes()).thenReturn(Map.of());
        when(loteService.obtenerLotesActivosPorAutoclave()).thenReturn(Map.of());
        when(loteService.obtenerTodosLosLotes()).thenReturn(List.of());

        DatosRefresco datos = lector.leer();
        origen.clear();

        assertEquals(1, datos.equipos().size());
    }
}
