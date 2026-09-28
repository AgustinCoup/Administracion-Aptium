package com.example.features.equipos.ortopedias.service;

import com.example.common.exception.ValidationException;
import com.example.common.model.FilaAEntregar;
import com.example.features.equipos.ortopedias.dao.MaterialDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialDAO dao;

    private MaterialService service;

    @BeforeEach
    void setUp() {
        service = new MaterialService(dao);
    }

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialService(null));
    }

    // ── entregarMateriales ────────────────────────────────────────────────────

    @Test
    void entregarMateriales_sinNada_lanzaValidationException() {
        assertThrows(ValidationException.class, () -> service.entregarMateriales(List.of()));
        assertThrows(ValidationException.class, () -> service.entregarMateriales(null));
        verifyNoInteractions(dao);
    }

    @Test
    void entregarMateriales_conFilas_delegaYDevuelveTrue() {
        List<FilaAEntregar> filas = List.of(new FilaAEntregar(1, 10, 3));

        assertTrue(service.entregarMateriales(filas));

        verify(dao).entregarMateriales(filas);
    }
}
