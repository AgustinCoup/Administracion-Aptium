package com.example.features.lavadero.controller;

import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.controller.helpers.ConsultaHistorial;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.service.HistorialLavaderoService;
import com.example.features.lavadero.view.PantallaHistorialLavadero;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Qué se publica para el lector de fondo en cada disparador de la pantalla.
 *
 * <p>La pantalla y el service son mocks; lo que se verifica es el ciclo
 * <em>cambio → {@code ConsultaHistorial} publicada → pedido de refresco</em>, que es lo que hace
 * que el filtro, la página y el total se comporten como corresponde. No se lanza ninguna lectura:
 * el lector real lo arma {@code UiCoordinator} sobre {@code consultaActual()}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HistorialLavaderoControllerTest {

    @Mock PantallaHistorialLavadero pantalla;
    @Mock HistorialLavaderoService  service;

    /** Real: el controller le instala su {@code MouseListener} al construirse. */
    private final JTable tablaIngresos = new JTable(new DefaultTableModel(3, 1));

    private int refrescosPedidos;

    private Runnable    alCambiarFiltros;
    private IntConsumer alCambiarPagina;
    private Runnable    accionRefrescar;

    private HistorialLavaderoController controller;

    @BeforeEach
    void setUp() {
        when(pantalla.getTablaIngresos()).thenReturn(tablaIngresos);
        filtrosDePantalla("", List.of("PENDIENTE"), null, null, "", null);

        controller = new HistorialLavaderoController(pantalla, service, () -> refrescosPedidos++);

        ArgumentCaptor<Runnable>    filtros  = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<IntConsumer> paginas  = ArgumentCaptor.forClass(IntConsumer.class);
        ArgumentCaptor<Runnable>    refresco = ArgumentCaptor.forClass(Runnable.class);
        verify(pantalla).setOnFiltrosChanged(filtros.capture());
        verify(pantalla).setAlCambiarPagina(paginas.capture());
        verify(pantalla).setAccionRefrescar(refresco.capture());
        alCambiarFiltros = filtros.getValue();
        alCambiarPagina  = paginas.getValue();
        accionRefrescar  = refresco.getValue();
    }

    @Test
    void alConstruirse_noPideNingunaLecturaTodavia() {
        assertEquals(0, refrescosPedidos, "la lectura la dispara componentShown, no el cableado");
        assertEquals(1, controller.consultaActual().criterios().numeroPagina());
    }

    @Test
    void cambiarUnFiltro_pideLaPaginaUnoConElFiltroNuevo() {
        estarEnLaPagina(4, 500);
        filtrosDePantalla("acme", List.of("LAVADO"), LocalDate.of(2025, 1, 1), null, "batas", 5);

        alCambiarFiltros.run();

        ConsultaHistorial consulta = controller.consultaActual();
        assertEquals(1, consulta.criterios().numeroPagina(), "un filtro nuevo vuelve a la página 1");
        assertEquals("acme", consulta.filtro().cliente());
        assertEquals(List.of("LAVADO"), consulta.filtro().estados());
        assertEquals(LocalDate.of(2025, 1, 1), consulta.filtro().desde());
        assertEquals("batas", consulta.filtro().elemento());
        assertEquals(5, consulta.filtro().lavarropas());
        assertNull(consulta.totalConocido(), "con filtro nuevo hay que volver a contar");
    }

    @Test
    void cambiarDePagina_conservaElFiltroYNoVuelveAContar() {
        filtrosDePantalla("acme", List.of("LAVADO"), null, null, "", null);
        alCambiarFiltros.run();
        controller.pintar(pagina(1, 500));

        alCambiarPagina.accept(7);

        ConsultaHistorial consulta = controller.consultaActual();
        assertEquals(7, consulta.criterios().numeroPagina());
        assertEquals("acme", consulta.filtro().cliente(), "cambiar de página no toca el filtro");
        assertEquals(500L, consulta.totalConocido(), "el total se arrastra: no se recuenta");
    }

    @Test
    void cambiarDePagina_noSeVeAfectadoPorLoQueElOperadorTipeoSinConfirmar() {
        filtrosDePantalla("acme", List.of(), null, null, "", null);
        alCambiarFiltros.run();
        controller.pintar(pagina(1, 500));

        // La pantalla ya muestra otro texto, pero todavía no notificó el cambio.
        filtrosDePantalla("otro cliente", List.of(), null, null, "", null);
        alCambiarPagina.accept(3);

        assertEquals("acme", controller.consultaActual().filtro().cliente(),
            "la página se pide con el filtro publicado, no con lo que haya en los campos");
    }

    @Test
    void elRefresco_conservaFiltroYPaginaPeroVuelveAContar() {
        filtrosDePantalla("acme", List.of("LAVADO"), null, null, "", null);
        alCambiarFiltros.run();
        controller.pintar(pagina(1, 500));
        alCambiarPagina.accept(3);
        controller.pintar(pagina(3, 500));

        accionRefrescar.run();

        ConsultaHistorial consulta = controller.consultaActual();
        assertEquals(3, consulta.criterios().numeroPagina(), "F5 no manda a la página 1");
        assertEquals("acme", consulta.filtro().cliente());
        assertNull(consulta.totalConocido(), "el refresco existe para ver datos frescos: recuenta");
    }

    @Test
    void cadaCambioPublicadoPideExactamenteUnRefresco() {
        alCambiarFiltros.run();
        alCambiarPagina.accept(2);
        accionRefrescar.run();

        assertEquals(3, refrescosPedidos, "publicar y pedir la lectura van siempre juntos");
    }

    @Test
    void pintar_vuelcaLaPaginaALaTablaYALaBarra() {
        Pagina<IngresoHistorial> pagina = pagina(2, 120);

        controller.pintar(pagina);

        verify(pantalla).actualizarIngresos(pagina.contenido());
        verify(pantalla).mostrarPaginacion(pagina);
        verify(pantalla).marcarActualizado();
    }

    /**
     * El reset de página cuelga del cambio de filtro, no de {@code pintar}: si viviera en
     * {@code pintar}, cada refresco mandaría a la página 1.
     */
    @Test
    void pintar_noResetealaPagina() {
        filtrosDePantalla("acme", List.of(), null, null, "", null);
        alCambiarFiltros.run();
        alCambiarPagina.accept(5);

        controller.pintar(pagina(5, 500));

        assertEquals(5, controller.consultaActual().criterios().numeroPagina());
    }

    @Test
    void laConsultaQueConsumeElLector_esLaUltimaPublicada() {
        alCambiarFiltros.run();
        ConsultaHistorial primera = controller.consultaActual();

        alCambiarPagina.accept(9);

        assertNotSame(primera, controller.consultaActual());
        assertEquals(9, controller.consultaActual().criterios().numeroPagina());
    }

    /** El lector de fondo pasa por {@code leer}: sin total conocido cuenta, con total no. */
    @Test
    void leerLaConsulta_recuentaSoloCuandoNoHayTotalConocido() {
        alCambiarFiltros.run();
        controller.consultaActual().leer(service);
        verify(service).obtenerPagina(controller.consultaActual().filtro(),
            controller.consultaActual().criterios());

        controller.pintar(pagina(1, 80));
        alCambiarPagina.accept(2);
        ConsultaHistorial conTotal = controller.consultaActual();
        conTotal.leer(service);
        verify(service).obtenerPagina(conTotal.filtro(), conTotal.criterios(), 80L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Deja al controller parado en una página con un total ya leído. */
    private void estarEnLaPagina(int numero, long total) {
        alCambiarFiltros.run();
        controller.pintar(pagina(1, total));
        alCambiarPagina.accept(numero);
        controller.pintar(pagina(numero, total));
    }

    private void filtrosDePantalla(String cliente, List<String> estados, LocalDate desde,
                                   LocalDate hasta, String elemento, Integer lavarropas) {
        when(pantalla.getFiltroCliente()).thenReturn(cliente);
        when(pantalla.getFiltroEstados()).thenReturn(estados);
        when(pantalla.getFiltroDesde()).thenReturn(desde);
        when(pantalla.getFiltroHasta()).thenReturn(hasta);
        when(pantalla.getFiltroElemento()).thenReturn(elemento);
        when(pantalla.getFiltroLavarropas()).thenReturn(lavarropas);
    }

    private static Pagina<IngresoHistorial> pagina(int numero, long total) {
        return new Pagina<>(List.of(ingreso()), numero, 50, total);
    }

    private static IngresoHistorial ingreso() {
        return new IngresoHistorial(1, "Cliente", LocalDateTime.now(), BigDecimal.ONE, 0,
            EstadoIngresoLavadero.PENDIENTE, Set.of(), Set.of());
    }
}
