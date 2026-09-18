package com.example.features.lavadero.controller;

import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lavadero.view.PantallaVerCiclos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
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
 * Paso 12: paginación en memoria de Ver Ciclos. El cache completo sigue existiendo
 * ({@code AbstractFilterController}); lo único nuevo es que {@code aplicarFiltros()} pagina el
 * resultado filtrado antes de pintarlo.
 *
 * <p>Lo que distingue este paso del 9 y del 11: acá no hay service ni {@code TareaUI} — la única
 * lectura de este controller es {@code solicitarRefresco}, el {@code Runnable} que
 * {@code componentShown} y el botón "Actualizar" disparan. Cambiar de página no debe invocarlo.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerCiclosControllerTest {

    @Mock PantallaVerCiclos pantalla;

    private int refrescosPedidos;

    private Runnable    alCambiarFiltros;
    private IntConsumer alCambiarPagina;

    private VerCiclosController controller;

    @BeforeEach
    void setUp() {
        filtrosDePantalla(null, List.of(), null, null);

        controller = new VerCiclosController(pantalla, () -> refrescosPedidos++);

        ArgumentCaptor<Runnable>    filtros = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<IntConsumer> paginas = ArgumentCaptor.forClass(IntConsumer.class);
        verify(pantalla).setOnFiltrosChanged(filtros.capture());
        verify(pantalla).setAlCambiarPagina(paginas.capture());
        alCambiarFiltros = filtros.getValue();
        alCambiarPagina  = paginas.getValue();

        refrescosPedidos = 0; // descartar lo que haya disparado el cableado del constructor, si algo
    }

    @Test
    void pintar_conMasDe50Ciclos_pintaSoloLaPrimeraPaginaDe50() {
        controller.pintar(ciclos(120));

        ArgumentCaptor<List<CicloLavadero>> filas = capturadorDeFilas();
        verify(pantalla).actualizarCiclos(filas.capture());
        assertEquals(50, filas.getValue().size());

        ArgumentCaptor<Pagina<CicloLavadero>> pagina = capturadorDePagina();
        verify(pantalla).mostrarPaginacion(pagina.capture());
        assertEquals(1, pagina.getValue().numeroPagina());
        assertEquals(120L, pagina.getValue().totalFilas());
    }

    @Test
    void cambiarDePagina_pintaLaPaginaPedida() {
        controller.pintar(ciclos(120));

        alCambiarPagina.accept(3);

        ArgumentCaptor<List<CicloLavadero>> filas = capturadorDeFilas();
        verify(pantalla, times(2)).actualizarCiclos(filas.capture());
        assertEquals(20, filas.getValue().size(), "página 3 de 120 con 50 por página trae 20");

        ArgumentCaptor<Pagina<CicloLavadero>> pagina = capturadorDePagina();
        verify(pantalla, times(2)).mostrarPaginacion(pagina.capture());
        assertEquals(3, pagina.getValue().numeroPagina());
    }

    /**
     * Es el test que distingue este paso del 9 y del 11: cambiar de página no dispara ninguna
     * lectura. {@code solicitarRefresco} es la única vía de lectura de este controller (lo llaman
     * {@code componentShown} y el botón "Actualizar"), y {@code alCambiarPagina} no lo toca.
     */
    @Test
    void cambiarDePagina_noDisparaUnaLectura() {
        controller.pintar(ciclos(120));

        alCambiarPagina.accept(2);

        assertEquals(0, refrescosPedidos, "cambiar de página no debe pedir un refresco");
    }

    @Test
    void cambiarUnFiltro_vuelveALaPagina1() {
        controller.pintar(ciclos(120));
        alCambiarPagina.accept(3);

        filtrosDePantalla(5, List.of(), null, null);
        alCambiarFiltros.run();

        ArgumentCaptor<Pagina<CicloLavadero>> pagina = capturadorDePagina();
        verify(pantalla, atLeastOnce()).mostrarPaginacion(pagina.capture());
        assertEquals(1, pagina.getValue().numeroPagina());
    }

    /**
     * Anti-patrón A15: el reset de página no puede vivir en {@code aplicarFiltros()}, porque
     * {@code recargarCache} (que corre en cada refresco, vía {@code pintar}) lo llama siempre. Si
     * el reset estuviera ahí, un F5 en la página 3 volvería a la página 1.
     */
    @Test
    void refrescarEnUnaPaginaDistintaDeLaUno_conservaLaPagina() {
        controller.pintar(ciclos(120));
        alCambiarPagina.accept(3);

        controller.pintar(ciclos(120)); // refresco: misma "foto"

        ArgumentCaptor<Pagina<CicloLavadero>> pagina = capturadorDePagina();
        verify(pantalla, atLeastOnce()).mostrarPaginacion(pagina.capture());
        assertEquals(3, pagina.getValue().numeroPagina(), "el refresco no resetea la página");
    }

    @Test
    void laUltimaPaginaIncompleta_sePintaBien() {
        controller.pintar(ciclos(120)); // 3 páginas: 50, 50, 20

        alCambiarPagina.accept(3);

        ArgumentCaptor<List<CicloLavadero>> filas = capturadorDeFilas();
        verify(pantalla, times(2)).actualizarCiclos(filas.capture());
        assertEquals(20, filas.getValue().size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void filtrosDePantalla(Integer numero, List<String> estados,
                                   LocalDate desde, LocalDate hasta) {
        when(pantalla.getFiltroNumero()).thenReturn(numero);
        when(pantalla.getFiltroEstados()).thenReturn(estados);
        when(pantalla.getFiltroFechaDesde()).thenReturn(desde);
        when(pantalla.getFiltroFechaHasta()).thenReturn(hasta);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<CicloLavadero>> capturadorDeFilas() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Pagina<CicloLavadero>> capturadorDePagina() {
        return ArgumentCaptor.forClass(Pagina.class);
    }

    private static List<CicloLavadero> ciclos(int cantidad) {
        List<CicloLavadero> lista = new ArrayList<>();
        JabonCatalogo jabon = new JabonCatalogo(1, "Jabón Neutro");
        for (int i = 0; i < cantidad; i++) {
            lista.add(new CicloLavadero(i, i % 5, TipoLavado.LIMPIO, jabon,
                BigDecimal.ONE, false, false, BigDecimal.TEN,
                LocalDateTime.now().minusHours(1), LocalDateTime.now()));
        }
        return lista;
    }
}
