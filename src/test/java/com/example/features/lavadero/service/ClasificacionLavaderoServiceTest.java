package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CatalogoElementosLavaderoDAO;
import com.example.features.lavadero.dao.ClasificacionLavaderoDAO;
import com.example.features.lavadero.model.CategoriaElementoLavadero;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.model.ElementoClasificacion;

import java.util.Optional;
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

    // ── agregarElementoCatalogo ───────────────────────────────────────────────

    @Test
    void agregarElementoCatalogo_nombreVacio_lanzaValidationException() {
        assertThrows(ValidationException.class,
            () -> service.agregarElementoCatalogo("   ", CategoriaElementoLavadero.REGULAR));
        verifyNoInteractions(catalogoDAO);
    }

    @Test
    void agregarElementoCatalogo_categoriaNula_lanzaValidationException() {
        assertThrows(ValidationException.class,
            () -> service.agregarElementoCatalogo("Sabana nueva", null));
        verifyNoInteractions(catalogoDAO);
    }

    @Test
    void agregarElementoCatalogo_nombreDuplicado_lanzaValidationException() {
        when(catalogoDAO.buscarPorNombre("Batas"))
            .thenReturn(Optional.of(new ElementoCatalogo(1, "Batas")));

        assertThrows(ValidationException.class,
            () -> service.agregarElementoCatalogo("Batas", CategoriaElementoLavadero.REGULAR));
        verify(catalogoDAO, never()).agregar(any(), any());
    }

    @Test
    void agregarElementoCatalogo_valido_recortaNombreYDelegaAlDAO() {
        when(catalogoDAO.buscarPorNombre("Sabana nueva")).thenReturn(Optional.empty());
        ElementoCatalogo creado = new ElementoCatalogo(9, "Sabana nueva");
        when(catalogoDAO.agregar("Sabana nueva", CategoriaElementoLavadero.EQUIPO)).thenReturn(creado);

        assertEquals(creado,
            service.agregarElementoCatalogo("  Sabana nueva  ", CategoriaElementoLavadero.EQUIPO));
        verify(catalogoDAO).agregar("Sabana nueva", CategoriaElementoLavadero.EQUIPO);
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
