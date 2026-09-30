package com.example.features.seguridad.service;

import com.example.common.constants.Constantes;
import com.example.common.exception.PasswordIncorrectaException;
import com.example.common.exception.ValidationException;
import com.example.features.seguridad.HasherPbkdf2;
import com.example.features.seguridad.HasherPbkdf2ParaTests;
import com.example.features.seguridad.dao.PasswordDAO;
import com.example.features.seguridad.model.HashPassword;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** DAO simulado y hasher real con pocas iteraciones: se prueba la verificación de verdad. */
@ExtendWith(MockitoExtension.class)
class PasswordEliminacionServiceTest {

    private static final String PROPOSITO = PasswordEliminacionService.PROPOSITO;

    @Mock
    private PasswordDAO dao;

    private final HasherPbkdf2 hasher = HasherPbkdf2ParaTests.conIteraciones(1_000);
    private PasswordEliminacionService service;
    private HashPassword guardado;

    @BeforeEach
    void setUp() {
        service = new PasswordEliminacionService(dao, hasher);
        guardado = hasher.hashear("vigente1".toCharArray());
    }

    @Test
    void constructor_dependenciasNull_lanza() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordEliminacionService(null, hasher));
        assertThrows(IllegalArgumentException.class, () -> new PasswordEliminacionService(dao, null));
    }

    @Test
    void verificar_correcta_noLanza() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);

        assertDoesNotThrow(() -> service.verificar("vigente1".toCharArray()));
    }

    @Test
    void verificar_incorrecta_lanzaPasswordIncorrecta() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);

        PasswordIncorrectaException e = assertThrows(PasswordIncorrectaException.class,
            () -> service.verificar("otra1234".toCharArray()));
        assertEquals(Constantes.Mensajes.PASSWORD_INCORRECTA, e.getMessage());
    }

    /** No se dice si falló por vacía o por distinta. */
    @Test
    void verificar_vacia_lanzaElMismoMensaje() {
        PasswordIncorrectaException vacia = assertThrows(PasswordIncorrectaException.class,
            () -> service.verificar(new char[0]));
        PasswordIncorrectaException nula = assertThrows(PasswordIncorrectaException.class,
            () -> service.verificar(null));

        assertEquals(Constantes.Mensajes.PASSWORD_INCORRECTA, vacia.getMessage());
        assertEquals(Constantes.Mensajes.PASSWORD_INCORRECTA, nula.getMessage());
    }

    /** El service no limpia el array: es del diálogo, que lo limpia cuando ya no lo necesita. */
    @Test
    void verificar_noLimpiaElArrayDelLlamador() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);
        char[] password = "vigente1".toCharArray();

        service.verificar(password);

        assertArrayEquals("vigente1".toCharArray(), password);
    }

    @Test
    void esInicial_delegaADAO() {
        when(dao.esInicial(PROPOSITO)).thenReturn(true);

        assertTrue(service.esInicial());
    }

    @Test
    void cambiar_actualIncorrecta_noReemplaza() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);

        PasswordIncorrectaException e = assertThrows(PasswordIncorrectaException.class,
            () -> service.cambiar("mal12345".toCharArray(), "nueva123".toCharArray(), "nueva123".toCharArray()));

        assertEquals(Constantes.Mensajes.PASSWORD_ACTUAL_INCORRECTA, e.getMessage());
        verify(dao, never()).reemplazar(anyString(), any(), any());
    }

    @Test
    void cambiar_nuevaYRepetidaDistintas_validation() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.cambiar("vigente1".toCharArray(), "nueva123".toCharArray(), "nueva124".toCharArray()));

        assertTrue(e.getValidationErrors().contains(Constantes.Mensajes.PASSWORD_NUEVA_NO_COINCIDE));
        verify(dao, never()).reemplazar(anyString(), any(), any());
    }

    @Test
    void cambiar_nuevaCorta_validation() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);
        char[] corta = "x".repeat(Constantes.Eliminacion.PASSWORD_MIN_LARGO - 1).toCharArray();

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.cambiar("vigente1".toCharArray(), corta, corta.clone()));

        assertTrue(e.getValidationErrors().contains(String.format(
            Constantes.Mensajes.PASSWORD_NUEVA_CORTA, Constantes.Eliminacion.PASSWORD_MIN_LARGO)));
        verify(dao, never()).reemplazar(anyString(), any(), any());
    }

    @Test
    void cambiar_nuevaIgualALaActual_validation() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.cambiar("vigente1".toCharArray(), "vigente1".toCharArray(), "vigente1".toCharArray()));

        assertTrue(e.getValidationErrors().contains(Constantes.Mensajes.PASSWORD_NUEVA_IGUAL_A_LA_ACTUAL));
        verify(dao, never()).reemplazar(anyString(), any(), any());
    }

    @Test
    void cambiar_nuevaNull_validationSinNPE() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);

        assertThrows(ValidationException.class,
            () -> service.cambiar("vigente1".toCharArray(), null, null));
    }

    /**
     * El CAS del DAO usa como "anterior" el mismo hash contra el que se verificó la actual: si
     * otro la cambió en el medio, choca en vez de pisarla.
     */
    @Test
    void cambiar_ok_reemplazaConHashNuevo() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);

        service.cambiar("vigente1".toCharArray(), "nueva123".toCharArray(), "nueva123".toCharArray());

        ArgumentCaptor<HashPassword> nuevo = ArgumentCaptor.forClass(HashPassword.class);
        verify(dao).reemplazar(eq(PROPOSITO), eq(guardado), nuevo.capture());
        assertTrue(hasher.verificar("nueva123".toCharArray(), nuevo.getValue()));
        assertFalse(hasher.verificar("vigente1".toCharArray(), nuevo.getValue()));
        verify(dao, times(1)).leer(PROPOSITO);
    }

    @Test
    void cambiar_noLimpiaLosArraysDelLlamador() {
        when(dao.leer(PROPOSITO)).thenReturn(guardado);
        char[] actual = "vigente1".toCharArray();
        char[] nueva = "nueva123".toCharArray();
        char[] repetida = "nueva123".toCharArray();

        service.cambiar(actual, nueva, repetida);

        assertArrayEquals("vigente1".toCharArray(), actual);
        assertArrayEquals("nueva123".toCharArray(), nueva);
        assertArrayEquals("nueva123".toCharArray(), repetida);
    }
}
