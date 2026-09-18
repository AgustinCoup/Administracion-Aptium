package com.example.features.equipos.ortopedias.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.ortopedias.controller.helpers.ConsultaCde;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.view.PantallaVerCDEv2;
import com.example.features.equipos.service.CdeConsultaService;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Qué se publica para el lector de fondo en cada disparador de Estado de Procesos.
 *
 * <p>La pantalla es un mock; lo que se verifica es el ciclo <em>cambio → {@link ConsultaCde}
 * publicada → pedido de refresco</em>. No se lanza ninguna lectura: el lector real lo arma
 * {@code UiCoordinator} sobre {@code consultaActual()}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EstadoProcesosControllerTest {

    private static final List<String> SIN_ENTREGADOS = Arrays.stream(EstadoEquipo.values())
        .filter(estado -> estado != EstadoEquipo.ENTREGADO)
        .map(EstadoEquipo::getNombre)
        .toList();

    @Mock private PantallaVerCDEv2   panel;
    @Mock private CdeConsultaService service;

    private int refrescosPedidos;

    private Runnable    alCambiarFiltros;
    private IntConsumer alCambiarPagina;
    private Runnable    accionRefrescar;

    private EstadoProcesosController controller;

    @BeforeEach
    void setUp() {
        filtrosDePantalla("", "", List.of());

        controller = new EstadoProcesosController(panel, () -> refrescosPedidos++);

        ArgumentCaptor<Runnable>    filtros  = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<IntConsumer> paginas  = ArgumentCaptor.forClass(IntConsumer.class);
        ArgumentCaptor<Runnable>    refresco = ArgumentCaptor.forClass(Runnable.class);
        verify(panel).setOnFiltrosChanged(filtros.capture());
        verify(panel).setAlCambiarPagina(paginas.capture());
        verify(panel).setAccionRefrescar(refresco.capture());
        alCambiarFiltros = filtros.getValue();
        alCambiarPagina  = paginas.getValue();
        accionRefrescar  = refresco.getValue();
    }

    @Test
    @DisplayName("pinta la página tal como viene, y la barra con ella")
    void pintar_vuelcaLaPaginaALaTablaYALaBarra() {
        Pagina<EquipoRegistrableInterface> pagina = pagina(1, 120, equipo("Cliente A"));

        controller.pintar(pagina);

        verify(panel).actualizarTabla(pagina.contenido());
        verify(panel).mostrarPaginacion(pagina);
        verify(panel).marcarActualizado();
    }

    @Test
    @DisplayName("una página vacía deja la tabla vacía, no la deja con datos viejos")
    void pintar_paginaVacia() {
        controller.pintar(pagina(1, 1, equipo("A")));
        controller.pintar(new Pagina<>(List.of(), 1, 50, 0));

        verify(panel).actualizarTabla(List.of());
    }

    /**
     * El default que oculta los entregados es ahora un filtro de la <b>consulta</b>: viaja en el
     * {@code FiltroEquipos} publicado. Como filtro de vista sobre una página de 50 mostraría 8
     * filas y el operador creería que hay 8.
     */
    @Test
    @DisplayName("el default de la pantalla viaja en el filtro de la consulta")
    void elDefaultDeLaPantalla_esFiltroDeLaConsulta() {
        filtrosDePantalla("", "", SIN_ENTREGADOS);

        alCambiarFiltros.run();

        List<String> estados = controller.consultaActual().filtro().estados();
        assertEquals(SIN_ENTREGADOS, estados);
        assertTrue(!estados.contains(EstadoEquipo.ENTREGADO.getNombre()));
    }

    @Test
    @DisplayName("cambiar un filtro pide la página 1 y vuelve a contar")
    void cambiarUnFiltro_pideLaPaginaUnoConElFiltroNuevo() {
        estarEnLaPagina(4, 500);
        filtrosDePantalla("acme", "clinica", List.of(EstadoEquipo.LAVANDO.getNombre()));

        alCambiarFiltros.run();

        ConsultaCde consulta = controller.consultaActual();
        assertEquals(1, consulta.criterios().numeroPagina(), "un filtro nuevo vuelve a la página 1");
        assertEquals("acme", consulta.filtro().cliente());
        assertEquals("clinica", consulta.filtro().institucion());
        assertEquals(List.of(EstadoEquipo.LAVANDO.getNombre()), consulta.filtro().estados());
        assertNull(consulta.totalConocido(), "con filtro nuevo hay que volver a contar");
    }

    @Test
    @DisplayName("cambiar de página conserva el filtro y arrastra el total")
    void cambiarDePagina_conservaElFiltroYNoVuelveAContar() {
        filtrosDePantalla("acme", "", List.of());
        alCambiarFiltros.run();
        controller.pintar(pagina(1, 500, equipo("A")));

        alCambiarPagina.accept(7);

        ConsultaCde consulta = controller.consultaActual();
        assertEquals(7, consulta.criterios().numeroPagina());
        assertEquals("acme", consulta.filtro().cliente(), "cambiar de página no toca el filtro");
        assertEquals(500L, consulta.totalConocido(), "el total se arrastra: no se recuenta");
    }

    @Test
    @DisplayName("F5 conserva filtro y página, pero recuenta")
    void elRefresco_conservaFiltroYPaginaPeroVuelveAContar() {
        estarEnLaPagina(3, 500);

        accionRefrescar.run();

        ConsultaCde consulta = controller.consultaActual();
        assertEquals(3, consulta.criterios().numeroPagina(), "F5 no manda a la página 1");
        assertNull(consulta.totalConocido(), "el refresco existe para ver datos frescos: recuenta");
    }

    /**
     * El reset de página cuelga del cambio de filtro, no de {@code pintar}: si viviera en
     * {@code pintar}, cada refresco mandaría a la página 1.
     */
    @Test
    @DisplayName("pintar no resetea la página")
    void pintar_noResetealaPagina() {
        estarEnLaPagina(5, 500);

        assertEquals(5, controller.consultaActual().criterios().numeroPagina());
    }

    @Test
    @DisplayName("cada cambio publicado pide exactamente un refresco")
    void cadaCambioPublicadoPideExactamenteUnRefresco() {
        alCambiarFiltros.run();
        alCambiarPagina.accept(2);
        accionRefrescar.run();

        assertEquals(3, refrescosPedidos, "publicar y pedir la lectura van siempre juntos");
    }

    @Test
    @DisplayName("la consulta que consume el lector es la última publicada")
    void laConsultaQueConsumeElLector_esLaUltimaPublicada() {
        alCambiarFiltros.run();
        ConsultaCde primera = controller.consultaActual();

        alCambiarPagina.accept(9);

        assertNotSame(primera, controller.consultaActual());
        assertEquals(9, controller.consultaActual().criterios().numeroPagina());
    }

    /** El lector de fondo pasa por {@code leer}: sin total conocido cuenta, con total no. */
    @Test
    @DisplayName("leer recuenta sólo cuando no hay total conocido")
    void leerLaConsulta_recuentaSoloCuandoNoHayTotalConocido() {
        alCambiarFiltros.run();
        ConsultaCde sinTotal = controller.consultaActual();
        sinTotal.leer(service);
        verify(service).obtenerPagina(sinTotal.filtro(), sinTotal.criterios());

        controller.pintar(pagina(1, 80, equipo("A")));
        alCambiarPagina.accept(2);
        ConsultaCde conTotal = controller.consultaActual();
        conTotal.leer(service);
        verify(service).obtenerPagina(conTotal.filtro(), conTotal.criterios(), 80L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Deja al controller parado en una página con un total ya leído. */
    private void estarEnLaPagina(int numero, long total) {
        alCambiarFiltros.run();
        controller.pintar(pagina(1, total, equipo("A")));
        alCambiarPagina.accept(numero);
        controller.pintar(pagina(numero, total, equipo("A")));
    }

    private void filtrosDePantalla(String cliente, String institucion, List<String> estados) {
        when(panel.getFiltroCliente()).thenReturn(cliente);
        when(panel.getFiltroInstitucion()).thenReturn(institucion);
        when(panel.getFiltroEstados()).thenReturn(estados);
    }

    private static Pagina<EquipoRegistrableInterface> pagina(int numero, long total,
                                                             EquipoRegistrableInterface... filas) {
        return new Pagina<>(List.of(filas), numero, 50, total);
    }

    private static EquipoRegistrableInterface equipo(String cliente) {
        Equipo eq = mock(Equipo.class);
        when(eq.getClienteNombre()).thenReturn(cliente);
        return eq;
    }
}
