package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.SalidaLavaderoDAO;
import com.example.features.lavadero.dao.derivadores.DerivadorSalidas;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalidaLavaderoServiceTest {

    @Mock
    private SalidaLavaderoDAO dao;
    @Mock
    private DerivadorSalidas derivadorFueraDeFlujo;
    @Mock
    private DerivadorSalidas derivadorCdeCliente;
    @Mock
    private DerivadorSalidas derivadorCdeAptium;

    private SalidaLavaderoService service;

    @BeforeEach
    void setUp() {
        lenient().when(derivadorFueraDeFlujo.accion()).thenReturn(AccionSalida.FUERA_DE_FLUJO);
        lenient().when(derivadorCdeCliente.accion()).thenReturn(AccionSalida.CDE_CLIENTE);
        lenient().when(derivadorCdeAptium.accion()).thenReturn(AccionSalida.CDE_APTIUM);
        service = new SalidaLavaderoService(dao,
            List.of(derivadorFueraDeFlujo, derivadorCdeCliente, derivadorCdeAptium));
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new SalidaLavaderoService(null, List.of(derivadorFueraDeFlujo)));
    }

    @Test
    void constructor_derivadoresNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new SalidaLavaderoService(dao, null));
    }

    @Test
    void constructor_faltaUnaAccion_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
            new SalidaLavaderoService(dao, List.of(derivadorFueraDeFlujo, derivadorCdeCliente)));
    }

    @Test
    void constructor_dosDerivadoresParaLaMismaAccion_lanzaIllegalArgument() {
        DerivadorSalidas otroFueraDeFlujo = mock(DerivadorSalidas.class);
        when(otroFueraDeFlujo.accion()).thenReturn(AccionSalida.FUERA_DE_FLUJO);
        assertThrows(IllegalArgumentException.class, () -> new SalidaLavaderoService(dao,
            List.of(derivadorFueraDeFlujo, otroFueraDeFlujo, derivadorCdeCliente, derivadorCdeAptium)));
    }

    // ── marcarListo — validaciones ───────────────────────────────────────────

    @Test
    void marcarListo_listaNula_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.marcarListo(null));
        verifyNoInteractions(dao);
    }

    @Test
    void marcarListo_listaVacia_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.marcarListo(List.of()));
        verifyNoInteractions(dao);
    }

    @Test
    void marcarListo_cantidadCero_lanzaValidation() {
        MarcaListo marca = new MarcaListo(pendiente(1, 10, 0), 0);
        assertThrows(ValidationException.class, () -> service.marcarListo(List.of(marca)));
        verifyNoInteractions(dao);
    }

    @Test
    void marcarListo_cantidadSuperaElPendiente_lanzaValidation() {
        MarcaListo marca = new MarcaListo(pendiente(1, 10, 0), 11);
        assertThrows(ValidationException.class, () -> service.marcarListo(List.of(marca)));
        verifyNoInteractions(dao);
    }

    @Test
    void marcarListo_dosMarcasDelMismoElemento_lanzaValidation() {
        MarcaListo m1 = new MarcaListo(pendiente(1, 10, 0), 3);
        MarcaListo m2 = new MarcaListo(pendiente(1, 10, 0), 2);
        assertThrows(ValidationException.class, () -> service.marcarListo(List.of(m1, m2)));
        verifyNoInteractions(dao);
    }

    // ── marcarListo — camino feliz ───────────────────────────────────────────

    @Test
    void marcarListo_datosValidos_delegaADAO() {
        MarcaListo marca = new MarcaListo(pendiente(1, 10, 0), 4);
        service.marcarListo(List.of(marca));
        verify(dao).marcarListo(List.of(marca));
    }

    // ── volverALavado — validaciones ─────────────────────────────────────────

    @Test
    void volverALavado_listaNula_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.volverALavado(null));
        verifyNoInteractions(dao);
    }

    @Test
    void volverALavado_listaVacia_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.volverALavado(List.of()));
        verifyNoInteractions(dao);
    }

    @Test
    void volverALavado_idsRepetidos_lanzaValidation() {
        assertThrows(ValidationException.class,
            () -> service.volverALavado(List.of(lista(7, 3), lista(7, 2))));
        verifyNoInteractions(dao);
    }

    // ── volverALavado — camino feliz ─────────────────────────────────────────

    @Test
    void volverALavado_variasSalidas_delegaConUnSoloLlamadoYSusIds() {
        service.volverALavado(List.of(lista(7, 3), lista(9, 2), lista(11, 1)));

        verify(dao).volverALavado(List.of(7, 9, 11));
        verify(dao, never()).volverALavado(anyInt());
    }

    // ── derivar — validaciones ───────────────────────────────────────────────

    @Test
    void derivar_accionNula_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.derivar(null, List.of(lista(1, 3))));
        verifyNoInteractions(dao);
    }

    @Test
    void derivar_seleccionNula_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.derivar(AccionSalida.FUERA_DE_FLUJO, null));
        verifyNoInteractions(dao);
    }

    @Test
    void derivar_seleccionVacia_lanzaValidation() {
        assertThrows(ValidationException.class, () -> service.derivar(AccionSalida.FUERA_DE_FLUJO, List.of()));
        verifyNoInteractions(dao);
    }

    @Test
    void derivar_cantidadCeroEnAlgunaSalida_lanzaValidation() {
        assertThrows(ValidationException.class, () ->
            service.derivar(AccionSalida.FUERA_DE_FLUJO, List.of(lista(1, 0))));
        verifyNoInteractions(dao);
    }

    // ── derivar — camino feliz ────────────────────────────────────────────────

    @Test
    void derivar_cdeCliente_usaElDerivadorDeCdeCliente() {
        List<SalidaLista> seleccion = List.of(lista(1, 3));
        service.derivar(AccionSalida.CDE_CLIENTE, seleccion);
        verify(dao).derivar(derivadorCdeCliente, seleccion);
    }

    @Test
    void derivar_cdeAptium_usaElDerivadorDeCdeAptium() {
        List<SalidaLista> seleccion = List.of(lista(1, 3));
        service.derivar(AccionSalida.CDE_APTIUM, seleccion);
        verify(dao).derivar(derivadorCdeAptium, seleccion);
    }

    @Test
    void derivar_fueraDeFlujo_usaElDerivadorDeFueraDeFlujo() {
        List<SalidaLista> seleccion = List.of(lista(1, 3));
        service.derivar(AccionSalida.FUERA_DE_FLUJO, seleccion);
        verify(dao).derivar(derivadorFueraDeFlujo, seleccion);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ElementoLavadoPendiente pendiente(int elementoCicloId, int cantidadLavada, int cantidadYaLista) {
        return new ElementoLavadoPendiente(elementoCicloId, 1, 4, 1, 1, "Hosp. A", "Batas",
            cantidadLavada, cantidadYaLista, LocalDateTime.now());
    }

    private SalidaLista lista(int salidaId, int cantidad) {
        return new SalidaLista(salidaId, 1, 4, 1, 1, "Hosp. A", "Batas",
            cantidad, LocalDateTime.now(), LocalDateTime.now());
    }
}
