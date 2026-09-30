package com.example.features.eliminaciones.service;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.eliminacion.IngresoAEliminar;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.PuestoDeTrabajo;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.eliminacion.ResumenIngresoLavadero;
import com.example.common.exception.PasswordIncorrectaException;
import com.example.common.exception.ValidationException;
import com.example.features.equipos.ortopedias.dao.EliminadorEquipoOrtopedia;
import com.example.features.equipos.otros.dao.EliminadorEquipoOtros;
import com.example.features.lavadero.dao.EliminadorIngresoLavadero;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.seguridad.service.PasswordEliminacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EliminacionIngresosServiceTest {

    private static final String PUESTO = PuestoDeTrabajo.actual();

    private EliminadorEquipoOrtopedia ortopedia;
    private EliminadorEquipoOtros otros;
    private EliminadorIngresoLavadero lavadero;
    private PasswordEliminacionService password;
    private EliminacionIngresosService service;

    @BeforeEach
    void setUp() {
        ortopedia = mock(EliminadorEquipoOrtopedia.class);
        otros = mock(EliminadorEquipoOtros.class);
        lavadero = mock(EliminadorIngresoLavadero.class);
        password = mock(PasswordEliminacionService.class);
        service = new EliminacionIngresosService(ortopedia, otros, lavadero, password);
    }

    private static ResumenEquipo resumenEquipo(ModuloIngreso modulo, int id, int version) {
        return new ResumenEquipo(new IngresoAEliminar(modulo, id), "Cliente", null, null,
            LocalDateTime.of(2026, 9, 1, 10, 0), "NUEVO", version, List.of(), List.of(), List.of());
    }

    private static ResumenIngresoLavadero resumenLavadero(int id, EstadoIngresoLavadero estado,
                                                          int... derivados) {
        List<ResumenIngresoLavadero.DerivadoCde> lista = java.util.Arrays.stream(derivados)
            .mapToObj(d -> new ResumenIngresoLavadero.DerivadoCde(d, "NUEVO", 1)).toList();
        return new ResumenIngresoLavadero(new IngresoAEliminar(ModuloIngreso.LAVADERO, id), "Cliente",
            LocalDateTime.of(2026, 9, 1, 10, 0), estado, BigDecimal.TEN, List.of(), lista, List.of());
    }

    @Test
    void constructor_dependenciaNula_lanza() {
        assertThrows(IllegalArgumentException.class,
            () -> new EliminacionIngresosService(null, otros, lavadero, password));
        assertThrows(IllegalArgumentException.class,
            () -> new EliminacionIngresosService(ortopedia, null, lavadero, password));
        assertThrows(IllegalArgumentException.class,
            () -> new EliminacionIngresosService(ortopedia, otros, null, password));
        assertThrows(IllegalArgumentException.class,
            () -> new EliminacionIngresosService(ortopedia, otros, lavadero, null));
    }

    @Test
    void resumir_despachaPorModulo() {
        ResumenEquipo rOrto = resumenEquipo(ModuloIngreso.ORTOPEDIA, 1, 3);
        ResumenEquipo rOtros = resumenEquipo(ModuloIngreso.OTROS, 2, 4);
        ResumenIngresoLavadero rLav = resumenLavadero(3, EstadoIngresoLavadero.LAVADO);
        when(ortopedia.resumir(1)).thenReturn(rOrto);
        when(otros.resumir(2)).thenReturn(rOtros);
        when(lavadero.resumir(3)).thenReturn(rLav);

        assertSame(rOrto, service.resumir(new IngresoAEliminar(ModuloIngreso.ORTOPEDIA, 1)));
        assertSame(rOtros, service.resumir(new IngresoAEliminar(ModuloIngreso.OTROS, 2)));
        assertSame(rLav, service.resumir(new IngresoAEliminar(ModuloIngreso.LAVADERO, 3)));
    }

    @Test
    void passwordEsInicial_delegaEnElServiceDeLaPassword() {
        when(password.esInicial()).thenReturn(true);

        assertTrue(service.passwordEsInicial());
    }

    @Test
    void eliminar_motivoVacio_validationYNoVerificaNiBorra() {
        SolicitudEliminacion s = new SolicitudEliminacion(resumenEquipo(ModuloIngreso.ORTOPEDIA, 1, 3), "   ");

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.eliminar(s, "clave".toCharArray()));

        assertTrue(e.getValidationErrors().contains(Mensajes.MOTIVO_ELIMINACION_OBLIGATORIO));
        verifyNoInteractions(password, ortopedia, otros, lavadero);
    }

    @Test
    void eliminar_motivoNulo_esObligatorio() {
        SolicitudEliminacion s = new SolicitudEliminacion(resumenEquipo(ModuloIngreso.OTROS, 1, 3), null);

        assertThrows(ValidationException.class, () -> service.eliminar(s, "clave".toCharArray()));
        verifyNoInteractions(password, ortopedia, otros, lavadero);
    }

    @Test
    void eliminar_motivoDemasiadoLargo_validationYNoVerificaNiBorra() {
        String largo = "x".repeat(501);
        SolicitudEliminacion s = new SolicitudEliminacion(resumenEquipo(ModuloIngreso.ORTOPEDIA, 1, 3), largo);

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.eliminar(s, "clave".toCharArray()));

        assertTrue(e.getValidationErrors().contains(String.format(Mensajes.MOTIVO_ELIMINACION_LARGO, 500)));
        verifyNoInteractions(password, ortopedia, otros, lavadero);
    }

    @Test
    void eliminar_motivoDeExactamenteElMaximo_pasa() {
        SolicitudEliminacion s = new SolicitudEliminacion(
            resumenEquipo(ModuloIngreso.ORTOPEDIA, 1, 3), "x".repeat(500));

        service.eliminar(s, "clave".toCharArray());

        verify(ortopedia).eliminar(eq(1), eq(3), eq("x".repeat(500)), anyString());
    }

    @Test
    void eliminar_passwordIncorrecta_noLlamaAlEliminador() {
        char[] clave = "mala".toCharArray();
        doThrow(new PasswordIncorrectaException(Mensajes.PASSWORD_INCORRECTA)).when(password).verificar(clave);
        SolicitudEliminacion s = new SolicitudEliminacion(resumenEquipo(ModuloIngreso.ORTOPEDIA, 1, 3), "error");

        assertThrows(PasswordIncorrectaException.class, () -> service.eliminar(s, clave));

        verify(ortopedia, never()).eliminar(anyInt(), anyInt(), anyString(), anyString());
        verifyNoInteractions(otros, lavadero);
    }

    @Test
    void eliminar_ortopedia_despachaConLaVersionDelResumen() {
        SolicitudEliminacion s = new SolicitudEliminacion(
            resumenEquipo(ModuloIngreso.ORTOPEDIA, 11, 7), "  cargado dos veces ");

        service.eliminar(s, "clave".toCharArray());

        verify(ortopedia).eliminar(11, 7, "cargado dos veces", PUESTO);
        verifyNoInteractions(otros, lavadero);
    }

    @Test
    void eliminar_otros_despachaConLaVersionDelResumen() {
        SolicitudEliminacion s = new SolicitudEliminacion(resumenEquipo(ModuloIngreso.OTROS, 12, 9), "duplicado");

        service.eliminar(s, "clave".toCharArray());

        verify(otros).eliminar(12, 9, "duplicado", PUESTO);
        verifyNoInteractions(ortopedia, lavadero);
    }

    @Test
    void eliminar_lavadero_despachaConEstadoYDerivadosDelResumen() {
        SolicitudEliminacion s = new SolicitudEliminacion(
            resumenLavadero(21, EstadoIngresoLavadero.LAVADO, 5, 6), "duplicado");

        service.eliminar(s, "clave".toCharArray());

        verify(lavadero).eliminar(21, EstadoIngresoLavadero.LAVADO, Set.of(5, 6), "duplicado", PUESTO);
        verifyNoInteractions(ortopedia, otros);
    }

    @Test
    void eliminar_verificaLaPasswordAntesDeDespachar() {
        char[] clave = "clave".toCharArray();
        SolicitudEliminacion s = new SolicitudEliminacion(resumenEquipo(ModuloIngreso.OTROS, 1, 1), "motivo");

        service.eliminar(s, clave);

        var orden = org.mockito.Mockito.inOrder(password, otros);
        orden.verify(password).verificar(clave);
        orden.verify(otros).eliminar(anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void eliminar_noModificaElCharArrayDeLaPassword() {
        char[] clave = "clave".toCharArray();
        char[] copia = clave.clone();
        SolicitudEliminacion s = new SolicitudEliminacion(resumenEquipo(ModuloIngreso.ORTOPEDIA, 1, 1), "motivo");

        service.eliminar(s, clave);

        assertArrayEquals(copia, clave);
        verify(password).verificar(any(char[].class));
    }

    @Test
    void eliminar_solicitudNula_lanza() {
        assertThrows(NullPointerException.class, () -> service.eliminar(null, "clave".toCharArray()));
    }

    @Test
    void solicitud_noTieneCampoDePassword() {
        // La password NUNCA es parte de un record: el toString automático la expondría.
        for (var componente : SolicitudEliminacion.class.getRecordComponents()) {
            assertNotEquals(char[].class, componente.getType());
            assertNotEquals("password", componente.getName());
        }
    }
}
