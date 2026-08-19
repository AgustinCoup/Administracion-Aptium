package com.example.features.lavadero.controller;

import com.example.common.exception.BusinessException;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.features.lavadero.service.SalidaLavaderoService;
import com.example.features.lavadero.view.PantallaSalidasLavadero;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La pantalla y el service son mocks; los widgets que el controller cablea son reales, porque
 * lo que se verifica es justamente qué hace cada click.
 *
 * <p>Las operaciones corren dentro de una {@code TareaUI}, así que las verificaciones de lo que
 * pasa después de la escritura usan {@code timeout(...)}: el resultado llega al hilo de la
 * interfaz cuando el {@code SwingWorker} termina, no en el click. Es el mismo patrón de
 * {@code AjustesControllerTest}. Los diálogos ({@code mostrarInfo}, {@code mostrarError}) son
 * métodos de la pantalla mockeada, así que no abren nada en el entorno headless de test.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalidasLavaderoControllerTest {

    private static final int ESPERA_MS = 3000;

    @Mock PantallaSalidasLavadero pantalla;
    @Mock SalidaLavaderoService   service;
    @Mock Runnable                refrescoOperativo;

    private final JButton btnMarcarListo   = new JButton();
    private final JButton btnVolverALavado = new JButton();
    private final JButton btnSaleDelFlujo  = new JButton();
    private final JButton btnIngresarACde  = new JButton();
    private final JSpinner spnCantidad     = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));

    /** Tres filas vacías: sólo existen para poder mover la selección y disparar el listener. */
    private final JTable tablaLavados = new JTable(new DefaultTableModel(3, 1));

    private SalidasLavaderoController controller;

    @BeforeEach
    void setUp() {
        when(pantalla.getBtnMarcarListo()).thenReturn(btnMarcarListo);
        when(pantalla.getBtnVolverALavado()).thenReturn(btnVolverALavado);
        when(pantalla.getBtnSaleDelFlujo()).thenReturn(btnSaleDelFlujo);
        when(pantalla.getBtnIngresarACde()).thenReturn(btnIngresarACde);
        when(pantalla.getSpnCantidad()).thenReturn(spnCantidad);
        when(pantalla.getTablaLavados()).thenReturn(tablaLavados);
        when(pantalla.getSeleccionLavados()).thenReturn(List.of());
        when(pantalla.getSeleccionListos()).thenReturn(List.of());

        controller = new SalidasLavaderoController(pantalla, service, refrescoOperativo);
    }

    // ── Derivación al CDE y refresco ─────────────────────────────────────────

    @Test
    @DisplayName("derivar al CDE dispara el refresco operativo exactamente una vez")
    void ingresarACde_disparaElRefrescoUnaVez() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(salida(1, 7, "Clinica Norte")));
        when(pantalla.elegirAccionCde(1)).thenReturn(AccionSalida.CDE_CLIENTE);

        btnIngresarACde.doClick();

        verify(refrescoOperativo, timeout(ESPERA_MS)).run();
    }

    @Test
    @DisplayName("sacar del flujo no dispara el refresco: en el CDE no cambió nada")
    void saleDelFlujo_noDisparaElRefresco() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(salida(1, 7, "Clinica Norte")));
        when(pantalla.confirmar(anyString())).thenReturn(true);

        btnSaleDelFlujo.doClick();

        verify(service, timeout(ESPERA_MS)).derivar(eq(AccionSalida.FUERA_DE_FLUJO), anyList());
        verify(refrescoOperativo, never()).run();
    }

    @Test
    @DisplayName("el diálogo elige CDE_CLIENTE y esa es la acción que llega al service")
    void ingresarACde_conClienteOriginal_pasaLaAccionElegida() {
        List<SalidaLista> seleccion = List.of(salida(1, 7, "Clinica Norte"));
        when(pantalla.getSeleccionListos()).thenReturn(seleccion);
        when(pantalla.elegirAccionCde(1)).thenReturn(AccionSalida.CDE_CLIENTE);

        btnIngresarACde.doClick();

        verify(service, timeout(ESPERA_MS)).derivar(AccionSalida.CDE_CLIENTE, seleccion);
    }

    @Test
    @DisplayName("el diálogo elige CDE_APTIUM y esa es la acción que llega al service")
    void ingresarACde_comoAptium_pasaLaAccionElegida() {
        List<SalidaLista> seleccion = List.of(salida(1, 7, "Clinica Norte"));
        when(pantalla.getSeleccionListos()).thenReturn(seleccion);
        when(pantalla.elegirAccionCde(1)).thenReturn(AccionSalida.CDE_APTIUM);

        btnIngresarACde.doClick();

        verify(service, timeout(ESPERA_MS)).derivar(AccionSalida.CDE_APTIUM, seleccion);
    }

    @Test
    @DisplayName("cancelar el diálogo no deriva nada ni refresca: ese diálogo es la confirmación")
    void ingresarACde_cancelado_noLlegaNadaAlService() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(salida(1, 7, "Clinica Norte")));
        when(pantalla.elegirAccionCde(anyInt())).thenReturn(null);

        btnIngresarACde.doClick();

        verify(service, never()).derivar(any(), anyList());
        verify(refrescoOperativo, never()).run();
    }

    @Test
    @DisplayName("el resumen dice a nombre de quién entraron los ingresos")
    void ingresarACde_comoAptium_elResumenNombraAAptium() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(
            salida(1, 7, "Clinica Norte"), salida(2, 9, "Sanatorio Sur")));
        when(pantalla.elegirAccionCde(2)).thenReturn(AccionSalida.CDE_APTIUM);

        btnIngresarACde.doClick();

        verify(pantalla, timeout(ESPERA_MS)).mostrarInfo("Se creó 1 ingreso en el CDE a nombre de APTIUM.");
    }

    @Test
    @DisplayName("con dos clientes distintos y su propio cliente, el resumen habla de dos ingresos")
    void ingresarACde_dosClientes_elResumenCuentaDosIngresos() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(
            salida(1, 7, "Clinica Norte"), salida(2, 9, "Sanatorio Sur")));
        when(pantalla.elegirAccionCde(2)).thenReturn(AccionSalida.CDE_CLIENTE);

        btnIngresarACde.doClick();

        verify(pantalla, timeout(ESPERA_MS))
            .mostrarInfo("Se crearon 2 ingresos en el CDE, uno por cliente.");
    }

    // ── Marcar Listo ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("con una fila, el service recibe la cantidad del spinner")
    void marcarListo_unaFila_usaLaCantidadDelSpinner() {
        ElementoLavadoPendiente item = lavado(11, 10, 0);
        when(pantalla.getSeleccionLavados()).thenReturn(List.of(item));

        tablaLavados.setRowSelectionInterval(0, 0);

        // Antes del click: después, la recarga vuelve a sincronizar el spinner.
        verify(pantalla, atLeastOnce()).setMaximoCantidad(10);
        verify(pantalla, atLeastOnce()).setSpinnerHabilitado(true);

        spnCantidad.setValue(4);
        btnMarcarListo.doClick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarcaListo>> captor = ArgumentCaptor.forClass(List.class);
        verify(service, timeout(ESPERA_MS)).marcarListo(captor.capture());
        assertEquals(List.of(new MarcaListo(item, 4)), captor.getValue());
    }

    @Test
    @DisplayName("con tres filas, un solo llamado con el pendiente entero de cada una y sin spinner")
    void marcarListo_variasFilas_marcaElPendienteEnteroDeCadaUna() {
        ElementoLavadoPendiente uno  = lavado(11, 10, 2);
        ElementoLavadoPendiente dos  = lavado(12, 5, 0);
        ElementoLavadoPendiente tres = lavado(13, 8, 8 - 3);
        when(pantalla.getSeleccionLavados()).thenReturn(List.of(uno, dos, tres));

        tablaLavados.setRowSelectionInterval(0, 2);
        verify(pantalla, atLeastOnce()).setSpinnerHabilitado(false);

        btnMarcarListo.doClick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarcaListo>> captor = ArgumentCaptor.forClass(List.class);
        verify(service, timeout(ESPERA_MS)).marcarListo(captor.capture());
        assertEquals(
            List.of(new MarcaListo(uno, 8), new MarcaListo(dos, 5), new MarcaListo(tres, 3)),
            captor.getValue());
    }

    // ── Guardas y errores ────────────────────────────────────────────────────

    @Test
    @DisplayName("sin selección no llega nada al service y se muestra el error")
    void sinSeleccion_ningunaAccionLlegaAlService() {
        btnMarcarListo.doClick();
        btnVolverALavado.doClick();
        btnSaleDelFlujo.doClick();
        btnIngresarACde.doClick();

        verify(service, never()).marcarListo(anyList());
        verify(service, never()).volverALavado(anyInt());
        verify(service, never()).derivar(any(), anyList());
        verify(pantalla, never()).elegirAccionCde(anyInt());
        verify(pantalla).mostrarError("Seleccioná al menos una tanda lavada.");
        verify(pantalla, org.mockito.Mockito.times(3))
            .mostrarError("Seleccioná al menos una salida lista.");
    }

    @Test
    @DisplayName("cancelar la confirmación de Sale del flujo no llama al service")
    void saleDelFlujo_cancelado_noLlamaAlService() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(salida(1, 7, "Clinica Norte")));
        when(pantalla.confirmar(anyString())).thenReturn(false);

        btnSaleDelFlujo.doClick();

        verify(service, never()).derivar(any(), anyList());
    }

    @Test
    @DisplayName("una BusinessException se muestra y además recarga: la pantalla quedó vieja")
    void businessException_muestraElMensajeYRecarga() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(salida(1, 7, "Clinica Norte")));
        when(pantalla.elegirAccionCde(1)).thenReturn(AccionSalida.CDE_CLIENTE);
        doThrow(new BusinessException("Esa salida ya fue derivada."))
            .when(service).derivar(any(), anyList());

        btnIngresarACde.doClick();

        verify(pantalla, timeout(ESPERA_MS)).mostrarError("Esa salida ya fue derivada.");
        verify(service, timeout(ESPERA_MS)).obtenerLavadosPendientesDeListo();
        verify(refrescoOperativo, never()).run();
    }

    // ── Volver a Lavado ──────────────────────────────────────────────────────

    @Test
    @DisplayName("volver a Lavado es de a una salida por vez")
    void volverALavado_variasFilas_avisaYNoLlamaAlService() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(
            salida(1, 7, "Clinica Norte"), salida(2, 7, "Clinica Norte")));

        btnVolverALavado.doClick();

        verify(service, never()).volverALavado(anyInt());
        verify(pantalla).mostrarError("Volvé a Lavado de a una salida por vez.");
    }

    @Test
    @DisplayName("volver a Lavado con una fila le pasa su id al service")
    void volverALavado_unaFila_pasaElId() {
        when(pantalla.getSeleccionListos()).thenReturn(List.of(salida(42, 7, "Clinica Norte")));

        btnVolverALavado.doClick();

        verify(service, timeout(ESPERA_MS)).volverALavado(42);
        verify(refrescoOperativo, never()).run();
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cargarDatos lee las dos tablas y las pinta juntas")
    void cargarDatos_leeYPintaLasDosTablas() {
        List<ElementoLavadoPendiente> lavados = List.of(lavado(11, 10, 0));
        List<SalidaLista> listos = List.of(salida(1, 7, "Clinica Norte"));
        when(service.obtenerLavadosPendientesDeListo()).thenReturn(lavados);
        when(service.obtenerListasSinDestino()).thenReturn(listos);

        controller.cargarDatos();

        verify(pantalla, timeout(ESPERA_MS)).refrescar(lavados, listos);
    }

    // ── Datos de prueba ──────────────────────────────────────────────────────

    private static ElementoLavadoPendiente lavado(int elementoCicloId, int lavada, int yaLista) {
        return new ElementoLavadoPendiente(elementoCicloId, 1, 2, 3, 7, "Clinica Norte",
            "Batas", lavada, yaLista, LocalDateTime.now());
    }

    private static SalidaLista salida(int salidaId, int clienteId, String clienteNombre) {
        return new SalidaLista(salidaId, 1, 2, 3, clienteId, clienteNombre, "Batas", 5,
            LocalDateTime.now(), LocalDateTime.now());
    }
}
