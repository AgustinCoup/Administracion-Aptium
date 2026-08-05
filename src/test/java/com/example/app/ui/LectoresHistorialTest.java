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
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.service.LoteService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Los dos lectores de consulta comparten forma y contrato — leen todo, y solo lo
 * suyo — así que van juntos en vez de en dos archivos de ceremonia.
 */
@ExtendWith(MockitoExtension.class)
class LectoresHistorialTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class HistorialDeEquipos {

        @Mock private EquipoService      equipoService;
        @Mock private EquipoOtrosService equipoOtrosService;

        private final Equipo      equipo      = mock(Equipo.class);
        private final EquipoOtros equipoOtros = mock(EquipoOtros.class);

        @Test
        @DisplayName("trae el histórico completo, entregados incluidos")
        void get_leeTodo() {
            when(equipoService.obtenerTodos()).thenReturn(List.of(equipo));
            when(equipoOtrosService.obtenerTodos()).thenReturn(List.of(equipoOtros));

            HistorialEquipos datos =
                new LectorHistorialEquipos(equipoService, equipoOtrosService).get();

            assertEquals(List.of(equipo), datos.equipos());
            assertEquals(List.of(equipoOtros), datos.equiposOtros());
            verify(equipoService, times(1)).obtenerTodos();
            verify(equipoOtrosService, times(1)).obtenerTodos();
            // Si leyera solo la cola activa, el filtro por ENTREGADO de esas
            // pantallas quedaría vacío para siempre.
            verify(equipoService, never()).obtenerActivos();
            verify(equipoOtrosService, never()).obtenerActivos();
        }

        @Test
        @DisplayName("el snapshot es inmutable")
        void snapshot_esInmutable() {
            HistorialEquipos datos = HistorialEquipos.vacio();
            assertThrows(UnsupportedOperationException.class, () -> datos.equipos().add(equipo));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class HistorialDeLotes {

        @Mock private AutoclaveService autoclaveService;
        @Mock private LoteService      loteService;

        private final Autoclave autoclave = mock(Autoclave.class);
        private final Lote      lote      = mock(Lote.class);

        @Test
        @DisplayName("trae los lotes y los autoclaves, y ningún equipo")
        void get_leeSoloLoSuyo() {
            when(autoclaveService.obtenerTodos()).thenReturn(List.of(autoclave));
            when(loteService.obtenerTodosLosLotes()).thenReturn(List.of(lote));

            HistorialLotes datos = new LectorHistorialLotes(autoclaveService, loteService).get();

            assertEquals(List.of(autoclave), datos.autoclaves());
            assertEquals(List.of(lote), datos.todosLosLotes());
            // Abrir "Ver Lotes" no debe costar el histórico de equipos: por eso va
            // en su propio grupo y no mezclado con HistorialEquipos.
            verify(loteService, never()).obtenerLotesActivosPorAutoclave();
        }

        @Test
        @DisplayName("el snapshot es inmutable")
        void snapshot_esInmutable() {
            HistorialLotes datos = HistorialLotes.vacio();
            assertThrows(UnsupportedOperationException.class, () -> datos.todosLosLotes().add(lote));
        }
    }
}
