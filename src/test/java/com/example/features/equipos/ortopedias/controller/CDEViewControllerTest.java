package com.example.features.equipos.ortopedias.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.app.ui.HistorialEquipos;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.view.PantallaVerCDEv2;
import com.example.features.equipos.otros.model.EquipoOtros;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code pintar()} es 100% hilo de UI y sin I/O, así que se puede verificar
 * con un {@link HistorialEquipos} fabricado y sin base de datos.
 */
class CDEViewControllerTest {

    private PantallaVerCDEv2  panel;
    private CDEViewController controller;

    @BeforeEach
    void setUp() {
        panel = mock(PantallaVerCDEv2.class);
        when(panel.getFiltroCliente()).thenReturn("");
        when(panel.getFiltroInstitucion()).thenReturn("");
        when(panel.getFiltroEstados()).thenReturn(List.of());

        controller = new CDEViewController(panel, () -> { });
    }

    @Test
    @DisplayName("pinta ortopedias y otros en la misma tabla")
    void pintar_unificaLosDosTipos() {
        Equipo ortopedia = equipo("Cliente A");
        EquipoOtros otros = equipoOtros("Cliente B");

        controller.pintar(datosCon(List.of(ortopedia), List.of(otros)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EquipoRegistrableInterface>> captor = ArgumentCaptor.forClass(List.class);
        verify(panel).actualizarTabla(captor.capture());
        assertEquals(List.of(ortopedia, otros), captor.getValue());
    }

    @Test
    @DisplayName("un snapshot vacío deja la tabla vacía, no la deja con datos viejos")
    void pintar_snapshotVacio() {
        controller.pintar(datosCon(List.of(equipo("A")), List.of()));
        controller.pintar(HistorialEquipos.vacio());

        verify(panel).actualizarTabla(List.of());
    }

    private static HistorialEquipos datosCon(List<Equipo> equipos, List<EquipoOtros> otros) {
        return new HistorialEquipos(equipos, otros);
    }

    private static Equipo equipo(String cliente) {
        Equipo eq = mock(Equipo.class);
        when(eq.getClienteNombre()).thenReturn(cliente);
        when(eq.getDescripcionSecundaria()).thenReturn("");
        when(eq.calcularEstado()).thenReturn(EstadoEquipo.NUEVO);
        return eq;
    }

    private static EquipoOtros equipoOtros(String cliente) {
        EquipoOtros eq = mock(EquipoOtros.class);
        when(eq.getClienteNombre()).thenReturn(cliente);
        when(eq.getDescripcionSecundaria()).thenReturn("");
        when(eq.calcularEstado()).thenReturn(EstadoEquipo.NUEVO);
        return eq;
    }
}
