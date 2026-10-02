package com.example.features.lotes.controller;

import com.example.features.lotes.model.Lote;
import com.example.features.lotes.service.LoteService;
import com.example.features.lotes.view.PantallaLotes;
import com.example.features.lotes.view.helpers.AutoclaveItem;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import com.example.features.lotes.view.helpers.PanelLotesContenido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.swing.SwingUtilities;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lanzamiento de un lote de ortopedias cuyo volumen de catálogo supera la capacidad del
 * autoclave: el operador corrige el campo "Volumen final" y ese valor es el que se lanza.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LotesControllerTest {

    private static final String AUTOCLAVE        = "7";
    private static final int    CAPACIDAD        = 250;
    private static final int    VOLUMEN_AJUSTADO = 240;
    private static final long   ESPERA_MS        = 3000;

    @Mock PantallaLotes       pantalla;
    @Mock PanelLotesContenido panel;
    @Mock LoteService         loteService;
    @Mock Lote                loteLanzado;

    /** Hace de campo "Volumen final": lo escribe el controller y lo lee al lanzar. */
    private final AtomicInteger campoVolumen = new AtomicInteger(-1);

    private LotesController         controller;
    private Consumer<AutoclaveItem> alSeleccionarAutoclave;
    private ActionListener          alLanzar;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(pantalla.getPanelContenido()).thenReturn(panel);
        doAnswer(inv -> { campoVolumen.set(inv.getArgument(0)); return null; })
            .when(panel).setVolumenCalculado(anyInt());
        when(panel.getVolumenManual()).thenAnswer(inv -> campoVolumen.get());
        when(panel.confirmar(anyString(), anyString())).thenReturn(true);

        controller = new LotesController(pantalla, loteService, null, () -> { });

        ArgumentCaptor<Consumer<AutoclaveItem>> seleccion = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<ActionListener>          lanzar    = ArgumentCaptor.forClass(ActionListener.class);
        verify(panel).setOnAutoclaveSeleccionado(seleccion.capture());
        verify(panel).setOnLanzar(lanzar.capture());
        alSeleccionarAutoclave = seleccion.getValue();
        alLanzar               = lanzar.getValue();
    }

    @Test
    void lanzar_conVolumenFinalAjustadoAMano_lanzaConEseVolumenYNoConElDeCatalogo() throws Exception {
        // Arrange: 100 cajas de 25 litros (2500 de catálogo) en un autoclave de 250
        when(loteService.preverIdNegocio()).thenReturn("L-0001");
        when(loteService.lanzarLote(anyString(), anyInt(), anyInt(), any(), any())).thenReturn(loteLanzado);
        when(loteLanzado.getIdNegocio()).thenReturn("L-0001");
        cargarEnStaging(new MaterialLoteItem(1, 1, "Caja mediana", 100, 25, "Cliente"));

        SwingUtilities.invokeAndWait(() -> {
            alSeleccionarAutoclave.accept(new AutoclaveItem(AUTOCLAVE, CAPACIDAD, false, null, 0));
            campoVolumen.set(VOLUMEN_AJUSTADO);   // el operador corrige el campo

            // Act
            alLanzar.actionPerformed(null);
        });

        // Assert
        verify(loteService, timeout(ESPERA_MS))
            .lanzarLote(eq(AUTOCLAVE), eq(CAPACIDAD), eq(VOLUMEN_AJUSTADO), any(), any());
    }

    /**
     * El staging sólo se puede armar por arrastre, que pasa por un diálogo de cantidad
     * ({@code JOptionPane}, inutilizable en headless): se carga directo en el mapa.
     */
    @SuppressWarnings("unchecked")
    private void cargarEnStaging(MaterialLoteItem item) throws ReflectiveOperationException {
        Field campo = LotesController.class.getDeclaredField("pendientesPorAutoclave");
        campo.setAccessible(true);
        ((Map<String, List<MaterialLoteItem>>) campo.get(controller))
            .put(AUTOCLAVE, new ArrayList<>(List.of(item)));
    }
}
