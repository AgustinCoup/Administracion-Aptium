package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.HistorialLavaderoDAO;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.LineaHistorial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistorialLavaderoServiceTest {

    @Mock
    private HistorialLavaderoDAO dao;

    private HistorialLavaderoService service;

    @BeforeEach
    void setUp() {
        service = new HistorialLavaderoService(dao);
    }

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new HistorialLavaderoService(null));
    }

    @Test
    void obtenerHistorial_delegaEnElDao() {
        List<IngresoHistorial> esperado = List.of(new IngresoHistorial(
            1, "Cliente", LocalDateTime.now(), new BigDecimal("5.0"), 1,
            EstadoIngresoLavadero.PENDIENTE, Set.of(), Set.of()));
        when(dao.obtenerHistorial()).thenReturn(esperado);

        assertEquals(esperado, service.obtenerHistorial());
        verify(dao).obtenerHistorial();
    }

    @Test
    void obtenerDetalle_idValido_delegaEnElDao() {
        List<LineaHistorial> esperado = List.of(
            new LineaHistorial("Sabanas", 3, null, null, null, null, null));
        when(dao.findDetalle(7)).thenReturn(esperado);

        assertEquals(esperado, service.obtenerDetalle(7));
        verify(dao).findDetalle(7);
    }

    @Test
    void obtenerDetalle_idCero_lanzaValidationSinTocarElDao() {
        assertThrows(ValidationException.class, () -> service.obtenerDetalle(0));
        verifyNoInteractions(dao);
    }

    @Test
    void obtenerDetalle_idNegativo_lanzaValidationSinTocarElDao() {
        assertThrows(ValidationException.class, () -> service.obtenerDetalle(-1));
        verifyNoInteractions(dao);
    }
}
