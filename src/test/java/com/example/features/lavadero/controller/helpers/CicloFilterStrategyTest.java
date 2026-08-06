package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.JabonCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CicloFilterStrategyTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2024, 6, 15, 10, 0);

    private CicloFilterStrategy strategy;
    private List<CicloLavadero> ciclos;

    @BeforeEach
    public void setUp() {
        strategy = new CicloFilterStrategy();
        ciclos = List.of(
            ciclo(1, 1, BASE.minusDays(5)),
            ciclo(2, 2, BASE.minusDays(2)),
            ciclo(3, 1, BASE.minusDays(1))
        );
    }

    @Test
    public void filtrosVaciosDevuelveTodo() {
        List<CicloLavadero> resultado = filtrar(ciclos, null, null, null);
        assertEquals(3, resultado.size());
    }

    @Test
    public void filtroNumeroReduceResultados() {
        List<CicloLavadero> resultado = filtrar(ciclos, 1, null, null);
        assertEquals(2, resultado.size());
    }

    @Test
    public void filtroFechaDesdeExcluyeAnteriores() {
        LocalDate desde = BASE.minusDays(3).toLocalDate();
        List<CicloLavadero> resultado = filtrar(ciclos, null, desde, null);
        assertEquals(2, resultado.size());
    }

    @Test
    public void filtroFechaHastaExcluyePosteriores() {
        LocalDate hasta = BASE.minusDays(3).toLocalDate();
        List<CicloLavadero> resultado = filtrar(ciclos, null, null, hasta);
        assertEquals(1, resultado.size());
    }

    @Test
    public void filtroNumeroYFechaDesde_combinados() {
        LocalDate desde = BASE.minusDays(3).toLocalDate();
        List<CicloLavadero> resultado = filtrar(ciclos, 1, desde, null);
        assertEquals(1, resultado.size());
        assertEquals(3, resultado.get(0).getId());
    }

    @Test
    public void cicloActivoSiempreAparece_sinFiltros() {
        List<CicloLavadero> conActivo = List.of(ciclos.get(0), cicloActivo(4, 3));
        assertEquals(2, filtrar(conActivo, null, null, null).size());
    }

    @Test
    public void cicloActivoSiempreAparece_conFiltroDesde() {
        CicloLavadero activo = cicloActivo(4, 3);
        LocalDate fechaFutura = BASE.plusDays(10).toLocalDate();
        List<CicloLavadero> resultado = filtrar(List.of(activo), null, fechaFutura, null);
        assertEquals(1, resultado.size());
    }

    @Test
    public void cicloActivoSiempreAparece_conFiltroHasta() {
        CicloLavadero activo = cicloActivo(4, 3);
        LocalDate fechaPasada = BASE.minusDays(100).toLocalDate();
        List<CicloLavadero> resultado = filtrar(List.of(activo), null, null, fechaPasada);
        assertEquals(1, resultado.size());
    }

    @Test
    public void cicloActivoFiltradoPorNumeroLavarropas() {
        CicloLavadero activo = cicloActivo(4, 3);
        List<CicloLavadero> resultado = filtrar(List.of(activo), 1, null, null);
        assertEquals(0, resultado.size());
    }

    // ── Filtro de estado ─────────────────────────────────────────────────────

    @Test
    public void filtroEstadoActivoDevuelveSoloActivos() {
        List<CicloLavadero> conActivo = List.of(ciclos.get(0), cicloActivo(4, 3));
        List<CicloLavadero> resultado = strategy.filter(conActivo, new CicloFilterCriteria(
            null, List.of(CicloLavadero.ESTADO_ACTIVO), null, null));
        assertEquals(1, resultado.size());
        assertEquals(4, resultado.get(0).getId());
    }

    @Test
    public void filtroEstadoFinalizadoExcluyeActivos() {
        List<CicloLavadero> conActivo = List.of(ciclos.get(0), cicloActivo(4, 3));
        List<CicloLavadero> resultado = strategy.filter(conActivo, new CicloFilterCriteria(
            null, List.of(CicloLavadero.ESTADO_FINALIZADO), null, null));
        assertEquals(1, resultado.size());
        assertEquals(1, resultado.get(0).getId());
    }

    @Test
    public void filtroEstadoIgnoraMayusculasMinusculas() {
        List<CicloLavadero> conActivo = List.of(ciclos.get(0), cicloActivo(4, 3));
        List<CicloLavadero> resultado = strategy.filter(conActivo, new CicloFilterCriteria(
            null, List.of("activo"), null, null));
        assertEquals(1, resultado.size());
        assertEquals(4, resultado.get(0).getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static final JabonCatalogo SKIP = new JabonCatalogo(1, "Skip");

    private List<CicloLavadero> filtrar(List<CicloLavadero> todos, Integer numeroLavarropas,
                                        LocalDate desde, LocalDate hasta) {
        return strategy.filter(todos, new CicloFilterCriteria(numeroLavarropas, List.of(), desde, hasta));
    }

    private CicloLavadero ciclo(int id, int lavarropas, LocalDateTime fechaFin) {
        return new CicloLavadero(
            id, lavarropas, SKIP,
            new BigDecimal("1.5"), false, false, null,
            BASE.minusDays(7), fechaFin
        );
    }

    private CicloLavadero cicloActivo(int id, int lavarropas) {
        return new CicloLavadero(
            id, lavarropas, SKIP,
            new BigDecimal("1.5"), false, false, null,
            BASE.minusDays(1), null
        );
    }
}
