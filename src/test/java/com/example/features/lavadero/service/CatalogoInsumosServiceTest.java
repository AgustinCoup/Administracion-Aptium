package com.example.features.lavadero.service;

import com.example.features.lavadero.dao.CatalogoInsumosDAO;
import com.example.features.lavadero.model.InsumoCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoInsumosServiceTest {

    @Mock
    private CatalogoInsumosDAO dao;

    private CatalogoInsumosService service;

    @BeforeEach
    void setUp() {
        service = new CatalogoInsumosService(dao);
    }

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogoInsumosService(null));
    }

    @Test
    void obtenerTodos_delegaADAO() {
        List<InsumoCatalogo> insumos = List.of(new InsumoCatalogo(1, "Suavizante", true));
        when(dao.findAll()).thenReturn(insumos);

        assertSame(insumos, service.obtenerTodos());
    }
}
