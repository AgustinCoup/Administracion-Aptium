package com.example.features.lavadero.service;

import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.JabonPorTipoLavadoDAO;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JabonPorTipoLavadoServiceTest {

    @Mock
    private JabonPorTipoLavadoDAO dao;

    private JabonPorTipoLavadoService service;

    @BeforeEach
    void setUp() {
        service = new JabonPorTipoLavadoService(dao);
    }

    @Test
    void constructor_daoNull_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new JabonPorTipoLavadoService(null));
    }

    @Test
    void obtenerDefaults_delegaADAO() {
        Map<TipoLavado, JabonCatalogo> defaults = Map.of(TipoLavado.SUCIO, new JabonCatalogo(1, "Skip"));
        when(dao.obtenerDefaults()).thenReturn(defaults);

        assertSame(defaults, service.obtenerDefaults());
    }

    @Test
    void guardar_tipoYJabonValidos_delegaADAO() {
        service.guardar(TipoLavado.LIMPIO, 2);
        verify(dao).guardar(TipoLavado.LIMPIO, 2);
    }

    @Test
    void guardar_sinTipo_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.guardar(null, 2));
        verifyNoInteractions(dao);
    }

    @Test
    void guardar_jabonInvalido_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.guardar(TipoLavado.SUCIO, 0));
        verifyNoInteractions(dao);
    }

    @Test
    void borrar_conTipo_delegaADAO() {
        service.borrar(TipoLavado.SUCIO);
        verify(dao).borrar(TipoLavado.SUCIO);
    }

    @Test
    void borrar_sinTipo_noLlegaAlDAO() {
        assertThrows(ValidationException.class, () -> service.borrar(null));
        verifyNoInteractions(dao);
    }
}
