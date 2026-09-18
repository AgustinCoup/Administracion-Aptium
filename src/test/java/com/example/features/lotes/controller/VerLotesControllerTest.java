package com.example.features.lotes.controller;

import com.example.app.ui.HistorialLotes;
import com.example.common.paginacion.Pagina;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.service.LoteReporteService;
import com.example.features.lotes.view.PantallaVerLotes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Paso 12: paginación en memoria de Ver Lotes. El cache completo sigue existiendo
 * ({@code AbstractFilterController}); lo único nuevo es que {@code aplicarFiltros()} pagina el
 * resultado filtrado antes de pintarlo.
 *
 * <p>Lo que distingue este paso del 9 y del 11: acá no hay service ni {@code TareaUI} de lectura
 * de grilla — la única lectura de este controller es {@code solicitarRefresco}, el
 * {@code Runnable} que {@code componentShown} y el botón "Actualizar" disparan. Cambiar de página
 * no debe invocarlo.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerLotesControllerTest {

    @Mock PantallaVerLotes    panel;
    @Mock LoteReporteService  reporteService;

    private int refrescosPedidos;

    private Runnable    alCambiarFiltros;
    private IntConsumer alCambiarPagina;

    private VerLotesController controller;

    @BeforeEach
    void setUp() {
        filtrosDePantalla("", List.of(), List.of(), null, null);

        controller = new VerLotesController(panel, reporteService, () -> refrescosPedidos++);

        ArgumentCaptor<Runnable>    filtros = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<IntConsumer> paginas = ArgumentCaptor.forClass(IntConsumer.class);
        verify(panel).setOnFiltrosChanged(filtros.capture());
        verify(panel).setAlCambiarPagina(paginas.capture());
        alCambiarFiltros = filtros.getValue();
        alCambiarPagina  = paginas.getValue();

        refrescosPedidos = 0;
    }

    @Test
    void pintar_conMasDe50Lotes_pintaSoloLaPrimeraPaginaDe50() {
        controller.pintar(historialCon(120));

        ArgumentCaptor<List<Lote>> filas = capturadorDeFilas();
        verify(panel).actualizarLotes(filas.capture());
        assertEquals(50, filas.getValue().size());

        ArgumentCaptor<Pagina<Lote>> pagina = capturadorDePagina();
        verify(panel).mostrarPaginacion(pagina.capture());
        assertEquals(1, pagina.getValue().numeroPagina());
        assertEquals(120L, pagina.getValue().totalFilas());
    }

    @Test
    void cambiarDePagina_pintaLaPaginaPedida() {
        controller.pintar(historialCon(120));

        alCambiarPagina.accept(3);

        ArgumentCaptor<List<Lote>> filas = capturadorDeFilas();
        verify(panel, times(2)).actualizarLotes(filas.capture());
        assertEquals(20, filas.getValue().size(), "página 3 de 120 con 50 por página trae 20");

        ArgumentCaptor<Pagina<Lote>> pagina = capturadorDePagina();
        verify(panel, times(2)).mostrarPaginacion(pagina.capture());
        assertEquals(3, pagina.getValue().numeroPagina());
    }

    /**
     * Es el test que distingue este paso del 9 y del 11: cambiar de página no dispara ninguna
     * lectura. {@code solicitarRefresco} es la única vía de lectura de este controller (lo llaman
     * {@code componentShown} y el botón "Actualizar"), y {@code alCambiarPagina} no lo toca.
     */
    @Test
    void cambiarDePagina_noDisparaUnaLectura() {
        controller.pintar(historialCon(120));

        alCambiarPagina.accept(2);

        assertEquals(0, refrescosPedidos, "cambiar de página no debe pedir un refresco");
    }

    @Test
    void cambiarUnFiltro_vuelveALaPagina1() {
        controller.pintar(historialCon(120));
        alCambiarPagina.accept(3);

        filtrosDePantalla("acme", List.of(), List.of(), null, null);
        alCambiarFiltros.run();

        ArgumentCaptor<Pagina<Lote>> pagina = capturadorDePagina();
        verify(panel, atLeastOnce()).mostrarPaginacion(pagina.capture());
        assertEquals(1, pagina.getValue().numeroPagina());
    }

    /**
     * Anti-patrón A15: el reset de página no puede vivir en {@code aplicarFiltros()}, porque
     * {@code recargarCache} (que corre en cada refresco, vía {@code pintar}) lo llama siempre. Si
     * el reset estuviera ahí, un F5 en la página 3 volvería a la página 1.
     */
    @Test
    void refrescarEnUnaPaginaDistintaDeLaUno_conservaLaPagina() {
        controller.pintar(historialCon(120));
        alCambiarPagina.accept(3);

        controller.pintar(historialCon(120)); // refresco: misma "foto"

        ArgumentCaptor<Pagina<Lote>> pagina = capturadorDePagina();
        verify(panel, atLeastOnce()).mostrarPaginacion(pagina.capture());
        assertEquals(3, pagina.getValue().numeroPagina(), "el refresco no resetea la página");
    }

    @Test
    void laUltimaPaginaIncompleta_sePintaBien() {
        controller.pintar(historialCon(120)); // 3 páginas: 50, 50, 20

        alCambiarPagina.accept(3);

        ArgumentCaptor<List<Lote>> filas = capturadorDeFilas();
        verify(panel, times(2)).actualizarLotes(filas.capture());
        assertEquals(20, filas.getValue().size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void filtrosDePantalla(String id, List<String> autoclaves, List<String> estados,
                                   LocalDate desde, LocalDate hasta) {
        when(panel.getFiltroId()).thenReturn(id);
        when(panel.getFiltroAutoclaves()).thenReturn(autoclaves);
        when(panel.getFiltroEstados()).thenReturn(estados);
        when(panel.getFiltroFechaDesde()).thenReturn(desde);
        when(panel.getFiltroFechaHasta()).thenReturn(hasta);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Lote>> capturadorDeFilas() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Pagina<Lote>> capturadorDePagina() {
        return ArgumentCaptor.forClass(Pagina.class);
    }

    private static HistorialLotes historialCon(int cantidad) {
        List<Lote> lista = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            lista.add(new Lote(i, "L-" + i, 2026, i, "Autoclave 1", 10, 5,
                LocalDateTime.now().minusHours(1), LocalDateTime.now()));
        }
        return new HistorialLotes(List.of(), lista);
    }
}
