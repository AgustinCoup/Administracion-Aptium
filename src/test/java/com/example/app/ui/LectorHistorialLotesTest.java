package com.example.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.features.autoclaves.model.Autoclave;
import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.service.LoteService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El único lector de consulta que sigue trayendo un snapshot completo.
 *
 * <p>Era {@code LectoresHistorialTest} y cubría también al de equipos. Ése desapareció con la
 * paginación: las dos pantallas del CDE ya no leen listas completas sino páginas, y qué leen lo
 * deciden {@code ConsultaEquipos} y {@code ConsultaCde}, testeadas con sus controllers. Ver Lotes
 * sigue como estaba porque su volumen tiene techo.
 */
@ExtendWith(MockitoExtension.class)
class LectorHistorialLotesTest {

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
        // en su propio grupo y no mezclado con el de las pantallas del CDE.
        verify(loteService, never()).obtenerLotesActivosPorAutoclave();
    }

    @Test
    @DisplayName("el snapshot es inmutable")
    void snapshot_esInmutable() {
        HistorialLotes datos = HistorialLotes.vacio();
        assertThrows(UnsupportedOperationException.class, () -> datos.todosLosLotes().add(lote));
    }
}
