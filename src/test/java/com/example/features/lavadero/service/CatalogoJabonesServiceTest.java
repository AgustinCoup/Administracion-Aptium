package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CatalogoJabonesDAO;
import com.example.features.lavadero.model.JabonCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoJabonesServiceTest {

    @Mock
    private CatalogoJabonesDAO dao;

    private CatalogoJabonesService service;

    @BeforeEach
    void setUp() {
        service = new CatalogoJabonesService(dao);
    }

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogoJabonesService(null));
    }

    @Test
    void obtenerActivos_delegaADAO() {
        List<JabonCatalogo> jabones = List.of(new JabonCatalogo(1, "Skip"));
        when(dao.findActivos()).thenReturn(jabones);

        assertSame(jabones, service.obtenerActivos());
    }

    @Test
    void obtenerTodos_delegaADAO() {
        List<JabonCatalogo> jabones = List.of(new JabonCatalogo(1, "Skip", false));
        when(dao.findAll()).thenReturn(jabones);

        assertSame(jabones, service.obtenerTodos());
    }

    @Test
    void agregar_nombreValido_delegaADAOConElNombreRecortado() {
        service.agregar("  Lider  ");

        verify(dao).agregar("Lider");
    }

    @Test
    void agregar_nombreVacio_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.agregar("   "));
        verifyNoInteractions(dao);
    }

    @Test
    void agregar_nombreNulo_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.agregar(null));
        verifyNoInteractions(dao);
    }

    @Test
    void darDeBaja_idValido_delegaADAO() {
        service.darDeBaja(1);
        verify(dao).darDeBaja(1);
    }

    @Test
    void darDeBaja_idInvalido_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.darDeBaja(0));
        verifyNoInteractions(dao);
    }

    @Test
    void reactivar_idValido_delegaADAO() {
        service.reactivar(2);
        verify(dao).reactivar(2);
    }

    @Test
    void reactivar_idInvalido_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.reactivar(-1));
        verifyNoInteractions(dao);
    }
}
