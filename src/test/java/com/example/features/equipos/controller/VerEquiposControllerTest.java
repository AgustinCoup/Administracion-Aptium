package com.example.features.equipos.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.paginacion.Pagina;
import com.example.features.clientes.service.ClienteService;
import com.example.features.equipos.controller.helpers.ConsultaEquipos;
import com.example.features.equipos.controller.helpers.PaginasEquipos;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.service.EquipoReporteService;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosReporteService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.equipos.view.PantallaVerEquipos;
import com.example.features.instituciones.service.InstitucionService;
import com.example.ui.common.CheckableComboBox;
import com.toedter.calendar.JDateChooser;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
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
 * Qué se publica para el lector de fondo en cada disparador de Ver Equipos.
 *
 * <p>Lo propio de esta pantalla frente a las otras dos paginadas es que tiene <b>dos</b> grillas
 * con paginación independiente sobre <b>un</b> panel de filtros: mover una página no mueve la otra,
 * pero cambiar un filtro devuelve las dos a la página 1.</p>
 *
 * <p>La pantalla es un mock; los componentes que el controller manipula de verdad —las dos tablas,
 * los campos y los combos— son reales, porque el controller les instala listeners y les lee el
 * contenido al armar el filtro.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerEquiposControllerTest {

    @Mock private PantallaVerEquipos        panel;
    @Mock private EquipoOtrosService        equipoOtrosService;
    @Mock private ClienteService            clienteService;
    @Mock private InstitucionService        institucionService;
    @Mock private EquipoReporteService      equipoReporteService;
    @Mock private EquipoOtrosReporteService equipoOtrosReporteService;

    @Mock private EquipoService      ortopediaServiceParaLeer;
    @Mock private EquipoOtrosService otrosServiceParaLeer;

    private final JTable tablaOrtopedias = new JTable(new DefaultTableModel(3, 1));
    private final JTable tablaOtros      = new JTable(new DefaultTableModel(3, 1));

    private final CheckableComboBox<String> cmbEstados =
        new CheckableComboBox<>(new String[]{EstadoEquipo.NUEVO.getNombre(),
                                             EstadoEquipo.ENTREGADO.getNombre()});
    private final CheckableComboBox<String> cmbTipoIngreso =
        new CheckableComboBox<>(new String[]{TipoIngresoOtros.DETALLES.getNombre(),
                                             TipoIngresoOtros.REMITO.getNombre()});
    private final JTextField   txtCliente     = new JTextField();
    private final JTextField   txtProfesional = new JTextField();
    private final JTextField   txtPaciente    = new JTextField();
    private final JTextField   txtInstitucion = new JTextField();
    private final JDateChooser dateDesde      = new JDateChooser();
    private final JDateChooser dateHasta      = new JDateChooser();

    private int refrescosPedidos;

    private Runnable    alCambiarFiltros;
    private IntConsumer alCambiarPaginaOrtopedias;
    private IntConsumer alCambiarPaginaOtros;
    private Runnable    accionRefrescar;

    private VerEquiposController controller;

    @BeforeEach
    void setUp() {
        when(panel.getTablaOrtopedias()).thenReturn(tablaOrtopedias);
        when(panel.getTablaOtros()).thenReturn(tablaOtros);
        when(panel.getCmbEstados()).thenReturn(cmbEstados);
        when(panel.getCmbTipoIngreso()).thenReturn(cmbTipoIngreso);
        when(panel.getTxtCliente()).thenReturn(txtCliente);
        when(panel.getTxtProfesional()).thenReturn(txtProfesional);
        when(panel.getTxtPaciente()).thenReturn(txtPaciente);
        when(panel.getTxtInstitucion()).thenReturn(txtInstitucion);
        when(panel.getDateDesde()).thenReturn(dateDesde);
        when(panel.getDateHasta()).thenReturn(dateHasta);

        controller = new VerEquiposController(panel, equipoOtrosService, clienteService,
            institucionService, equipoReporteService, equipoOtrosReporteService,
            () -> refrescosPedidos++);

        ArgumentCaptor<Runnable>    filtros    = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<IntConsumer> ortopedias = ArgumentCaptor.forClass(IntConsumer.class);
        ArgumentCaptor<IntConsumer> otros      = ArgumentCaptor.forClass(IntConsumer.class);
        ArgumentCaptor<Runnable>    refresco   = ArgumentCaptor.forClass(Runnable.class);
        verify(panel).configurarFiltros(filtros.capture());
        verify(panel).setAlCambiarPaginaOrtopedias(ortopedias.capture());
        verify(panel).setAlCambiarPaginaOtros(otros.capture());
        verify(panel).setAccionRefrescar(refresco.capture());
        alCambiarFiltros          = filtros.getValue();
        alCambiarPaginaOrtopedias = ortopedias.getValue();
        alCambiarPaginaOtros      = otros.getValue();
        accionRefrescar           = refresco.getValue();
    }

    @Test
    @DisplayName("al construirse no pide ninguna lectura: la dispara componentShown")
    void alConstruirse_noPideNingunaLecturaTodavia() {
        assertEquals(0, refrescosPedidos);
        assertEquals(1, controller.consultaActual().ortopedias().numeroPagina());
        assertEquals(1, controller.consultaActual().otros().numeroPagina());
    }

    @Test
    @DisplayName("el filtro publicado lleva los siete campos de la pantalla")
    void cambiarUnFiltro_publicaLosSieteCampos() {
        txtCliente.setText("  acme  ");
        txtProfesional.setText("perez");
        txtPaciente.setText("gomez");
        txtInstitucion.setText("clinica");
        cmbEstados.setSelectedItems(List.of(EstadoEquipo.NUEVO.getNombre()));
        cmbTipoIngreso.setSelectedItems(List.of(TipoIngresoOtros.REMITO.getNombre()));
        dateDesde.setDate(fecha(2026, 1, 1));
        dateHasta.setDate(fecha(2026, 1, 31));

        alCambiarFiltros.run();

        ConsultaEquipos consulta = controller.consultaActual();
        assertEquals("acme", consulta.filtro().cliente(), "el texto viaja trimmeado");
        assertEquals("perez", consulta.filtro().profesional());
        assertEquals("gomez", consulta.filtro().paciente());
        assertEquals("clinica", consulta.filtro().institucion());
        assertEquals(List.of(EstadoEquipo.NUEVO.getNombre()), consulta.filtro().estados());
        assertEquals(List.of(TipoIngresoOtros.REMITO.getNombre()), consulta.filtro().tiposIngreso());
        assertEquals(LocalDate.of(2026, 1, 1), consulta.filtro().desde());
        assertEquals(LocalDate.of(2026, 1, 31), consulta.filtro().hasta());
    }

    @Test
    @DisplayName("cambiar un filtro devuelve LAS DOS grillas a la página 1 y recuenta las dos")
    void cambiarUnFiltro_devuelveLasDosGrillasALaPaginaUno() {
        estarEnLasPaginas(4, 7, 500, 300);

        txtCliente.setText("acme");
        alCambiarFiltros.run();

        ConsultaEquipos consulta = controller.consultaActual();
        assertEquals(1, consulta.ortopedias().numeroPagina());
        assertEquals(1, consulta.otros().numeroPagina());
        assertNull(consulta.totalOrtopedias(), "con filtro nuevo hay que volver a contar");
        assertNull(consulta.totalOtros());
    }

    @Test
    @DisplayName("las dos grillas paginan independientemente")
    void lasDosGrillasPaginanIndependientemente() {
        alCambiarFiltros.run();
        controller.pintar(paginas(1, 1, 500, 300));

        alCambiarPaginaOrtopedias.accept(6);

        ConsultaEquipos tras = controller.consultaActual();
        assertEquals(6, tras.ortopedias().numeroPagina());
        assertEquals(1, tras.otros().numeroPagina(), "mover ortopedias no mueve 'otros'");

        alCambiarPaginaOtros.accept(3);

        ConsultaEquipos fin = controller.consultaActual();
        assertEquals(6, fin.ortopedias().numeroPagina(), "mover 'otros' no mueve ortopedias");
        assertEquals(3, fin.otros().numeroPagina());
    }

    @Test
    @DisplayName("cambiar de página conserva el filtro y arrastra los dos totales")
    void cambiarDePagina_conservaElFiltroYNoVuelveAContar() {
        txtCliente.setText("acme");
        alCambiarFiltros.run();
        controller.pintar(paginas(1, 1, 500, 300));

        alCambiarPaginaOrtopedias.accept(2);

        ConsultaEquipos consulta = controller.consultaActual();
        assertEquals("acme", consulta.filtro().cliente());
        assertEquals(500L, consulta.totalOrtopedias(), "los totales se arrastran: no se recuentan");
        assertEquals(300L, consulta.totalOtros());
    }

    @Test
    @DisplayName("F5 conserva las dos páginas y el filtro, pero recuenta")
    void elRefresco_conservaPaginasYFiltroPeroVuelveAContar() {
        estarEnLasPaginas(4, 7, 500, 300);

        accionRefrescar.run();

        ConsultaEquipos consulta = controller.consultaActual();
        assertEquals(4, consulta.ortopedias().numeroPagina(), "F5 no manda a la página 1");
        assertEquals(7, consulta.otros().numeroPagina());
        assertNull(consulta.totalOrtopedias(), "el refresco existe para ver datos frescos");
        assertNull(consulta.totalOtros());
    }

    /**
     * El reset de página cuelga del cambio de filtro, no de {@code pintar}: si viviera en
     * {@code pintar}, cada refresco mandaría las dos grillas a la página 1.
     */
    @Test
    @DisplayName("pintar no resetea ninguna de las dos páginas")
    void pintar_noResetealaPagina() {
        estarEnLasPaginas(4, 7, 500, 300);

        assertEquals(4, controller.consultaActual().ortopedias().numeroPagina());
        assertEquals(7, controller.consultaActual().otros().numeroPagina());
    }

    @Test
    @DisplayName("pintar vuelca las dos páginas a sus grillas")
    void pintar_vuelcaLasDosPaginas() {
        PaginasEquipos paginas = paginas(1, 1, 500, 300);

        controller.pintar(paginas);

        verify(panel).setDatosOrtopedia(paginas.ortopedias());
        verify(panel).setDatosOtros(paginas.otros());
        verify(panel).marcarActualizado();
    }

    @Test
    @DisplayName("cada cambio publicado pide exactamente un refresco")
    void cadaCambioPublicadoPideExactamenteUnRefresco() {
        alCambiarFiltros.run();
        alCambiarPaginaOrtopedias.accept(2);
        alCambiarPaginaOtros.accept(3);
        accionRefrescar.run();

        assertEquals(4, refrescosPedidos, "publicar y pedir la lectura van siempre juntos");
    }

    @Test
    @DisplayName("la consulta que consume el lector es la última publicada")
    void laConsultaQueConsumeElLector_esLaUltimaPublicada() {
        alCambiarFiltros.run();
        ConsultaEquipos primera = controller.consultaActual();

        alCambiarPaginaOtros.accept(9);

        assertNotSame(primera, controller.consultaActual());
        assertEquals(9, controller.consultaActual().otros().numeroPagina());
    }

    /** El lector de fondo pasa por {@code leer}: sin total conocido cuenta, con total no. */
    @Test
    @DisplayName("leer recuenta sólo cuando no hay total conocido, y por grilla")
    void leerLaConsulta_recuentaSoloCuandoNoHayTotalConocido() {
        alCambiarFiltros.run();
        ConsultaEquipos sinTotales = controller.consultaActual();
        sinTotales.leer(ortopediaServiceParaLeer, otrosServiceParaLeer);
        verify(ortopediaServiceParaLeer).obtenerPagina(sinTotales.filtro(), sinTotales.ortopedias());
        verify(otrosServiceParaLeer).obtenerPagina(sinTotales.filtro(), sinTotales.otros());

        controller.pintar(paginas(1, 1, 80, 40));
        alCambiarPaginaOrtopedias.accept(2);
        ConsultaEquipos conTotales = controller.consultaActual();
        conTotales.leer(ortopediaServiceParaLeer, otrosServiceParaLeer);
        verify(ortopediaServiceParaLeer)
            .obtenerPagina(conTotales.filtro(), conTotales.ortopedias(), 80L);
        verify(otrosServiceParaLeer).obtenerPagina(conTotales.filtro(), conTotales.otros(), 40L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Deja al controller parado en dos páginas distintas con los totales ya leídos. */
    private void estarEnLasPaginas(int ortopedias, int otros, long totalOrt, long totalOtr) {
        alCambiarFiltros.run();
        controller.pintar(paginas(1, 1, totalOrt, totalOtr));
        alCambiarPaginaOrtopedias.accept(ortopedias);
        controller.pintar(paginas(ortopedias, 1, totalOrt, totalOtr));
        alCambiarPaginaOtros.accept(otros);
        controller.pintar(paginas(ortopedias, otros, totalOrt, totalOtr));
    }

    private static PaginasEquipos paginas(int pagOrt, int pagOtros, long totalOrt, long totalOtr) {
        return new PaginasEquipos(
            new Pagina<>(List.of(mock(Equipo.class)), pagOrt, 50, totalOrt),
            new Pagina<>(List.of(mock(EquipoOtros.class)), pagOtros, 50, totalOtr));
    }

    private static Date fecha(int anio, int mes, int dia) {
        return Date.from(LocalDate.of(anio, mes, dia)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
    }
}
