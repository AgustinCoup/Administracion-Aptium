package com.example.features.equipos.service;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.paginacion.CriteriosPagina;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.dao.CdeConsultaDAO;
import com.example.features.equipos.model.FiltroEquipos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CdeConsultaServiceTest {

    @Mock
    private CdeConsultaDAO dao;

    private CdeConsultaService service;

    @BeforeEach
    void setUp() {
        service = new CdeConsultaService(dao);
    }

    @Test
    void constructor_daoNull_lanza() {
        assertThrows(NullPointerException.class, () -> new CdeConsultaService(null));
    }

    @Test
    void obtenerPagina_delegaEnElDao() {
        FiltroEquipos filtro = FiltroEquipos.sinFiltros();
        CriteriosPagina criterios = CriteriosPagina.primera();
        Pagina<EquipoRegistrableInterface> esperado = new Pagina<>(List.of(), 1, 50, 0);
        when(dao.obtenerPagina(filtro, criterios)).thenReturn(esperado);

        assertEquals(esperado, service.obtenerPagina(filtro, criterios));
    }

    /**
     * El total sólo cambia cuando cambian los <em>filtros</em>. Si pasar de la página 3 a la 4
     * volviera a contar, la paginación duplicaría las consultas que vino a ahorrar — y el síntoma
     * sería "anda un poco lento", que nadie asocia con esto.
     */
    @Test
    @DisplayName("con el total conocido no se vuelve a contar")
    void obtenerPaginaConTotalConocido_noVuelveAContar() {
        FiltroEquipos filtro = FiltroEquipos.sinFiltros();
        CriteriosPagina criterios = CriteriosPagina.pagina(4);
        Pagina<EquipoRegistrableInterface> esperado = new Pagina<>(List.of(), 4, 50, 200);
        when(dao.obtenerPagina(filtro, criterios, 200L)).thenReturn(esperado);

        assertEquals(esperado, service.obtenerPagina(filtro, criterios, 200L));
        verify(dao, never()).contar(any());
    }

    @Test
    void contar_delegaEnElDao() {
        FiltroEquipos filtro = FiltroEquipos.sinFiltros();
        when(dao.contar(filtro)).thenReturn(17L);

        assertEquals(17L, service.contar(filtro));
    }

    @Test
    void obtenerPagina_filtroNull_lanza() {
        assertThrows(NullPointerException.class,
            () -> service.obtenerPagina(null, CriteriosPagina.primera()));
    }

    @Test
    void obtenerPagina_criteriosNull_lanza() {
        assertThrows(NullPointerException.class,
            () -> service.obtenerPagina(FiltroEquipos.sinFiltros(), null));
    }
}
