package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CatalogoElementosLavaderoDAO;
import com.example.features.lavadero.dao.ClasificacionLavaderoDAO;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.model.ElementoClasificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClasificacionLavaderoServiceTest {

    @Mock private ClasificacionLavaderoDAO     clasificacionDAO;
    @Mock private CatalogoElementosLavaderoDAO catalogoDAO;

    private ClasificacionLavaderoService service;

    @BeforeEach
    void setUp() {
        service = new ClasificacionLavaderoService(clasificacionDAO, catalogoDAO);
    }

    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_daoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClasificacionLavaderoService(null, catalogoDAO));
        assertThrows(IllegalArgumentException.class,
            () -> new ClasificacionLavaderoService(clasificacionDAO, null));
    }

    // ── guardar — validaciones ────────────────────────────────────────────────

    @Test
    void guardar_ingresoIdCero_lanzaValidationException() {
        assertThrows(ValidationException.class,
            () -> service.guardar(0, List.of(new ElementoClasificacion(1, 2))));
        verifyNoInteractions(clasificacionDAO);
    }

    @Test
    void guardar_ingresoIdNegativo_lanzaValidationException() {
        assertThrows(ValidationException.class,
            () -> service.guardar(-1, List.of(new ElementoClasificacion(1, 2))));
        verifyNoInteractions(clasificacionDAO);
    }

    @Test
    void guardar_listaVacia_lanzaValidationException() {
        assertThrows(ValidationException.class,
            () -> service.guardar(1, Collections.emptyList()));
        verifyNoInteractions(clasificacionDAO);
    }

    @Test
    void guardar_cantidadCero_lanzaValidationException() {
        assertThrows(ValidationException.class,
            () -> service.guardar(1, List.of(new ElementoClasificacion(1, 0))));
        verifyNoInteractions(clasificacionDAO);
    }

    // ── guardar — camino feliz ────────────────────────────────────────────────

    @Test
    void guardar_datosValidos_delegaADAOYRetornaTrue() {
        List<ElementoClasificacion> elementos = List.of(new ElementoClasificacion(1, 3));
        when(clasificacionDAO.guardar(5, elementos)).thenReturn(true);

        assertTrue(service.guardar(5, elementos));
        verify(clasificacionDAO).guardar(5, elementos);
    }

    @Test
    void guardar_daoRetornaFalse_retornaFalse() {
        List<ElementoClasificacion> elementos = List.of(new ElementoClasificacion(2, 1));
        when(clasificacionDAO.guardar(3, elementos)).thenReturn(false);

        assertFalse(service.guardar(3, elementos));
    }

    // ── obtenerCatalogo ───────────────────────────────────────────────────────

    @Test
    void obtenerCatalogo_delegaACatalogoDAO() {
        List<ElementoCatalogo> catalogo = List.of(new ElementoCatalogo(1, "Batas"));
        when(catalogoDAO.findAll()).thenReturn(catalogo);

        assertEquals(catalogo, service.obtenerCatalogo());
        verify(catalogoDAO).findAll();
    }
}
