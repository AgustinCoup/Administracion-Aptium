package com.example.features.lavadero.controller;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.TipoJabon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VerCiclosControllerFiltrosTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2024, 6, 15, 10, 0);

    private List<CicloLavadero> ciclos;

    @BeforeEach
    public void setUp() {
        ciclos = List.of(
            ciclo(1, 1, BASE.minusDays(5)),
            ciclo(2, 2, BASE.minusDays(2)),
            ciclo(3, 1, BASE.minusDays(1))
        );
    }

    @Test
    public void filtrosVaciosDevuelveTodo() {
        List<CicloLavadero> resultado = VerCiclosController.filtrar(ciclos, null, null, null);
        assertEquals(3, resultado.size());
    }

    @Test
    public void filtroNumeroReduceResultados() {
        List<CicloLavadero> resultado = VerCiclosController.filtrar(ciclos, 1, null, null);
        assertEquals(2, resultado.size());
    }

    @Test
    public void filtroFechaDesdeExcluyeAnteriores() {
        LocalDate desde = BASE.minusDays(3).toLocalDate();
        List<CicloLavadero> resultado = VerCiclosController.filtrar(ciclos, null, desde, null);
        assertEquals(2, resultado.size());
    }

    @Test
    public void filtroFechaHastaExcluyePosteriores() {
        LocalDate hasta = BASE.minusDays(3).toLocalDate();
        List<CicloLavadero> resultado = VerCiclosController.filtrar(ciclos, null, null, hasta);
        assertEquals(1, resultado.size());
    }

    @Test
    public void filtroNumeroYFechaDesde_combinados() {
        LocalDate desde = BASE.minusDays(3).toLocalDate();
        List<CicloLavadero> resultado = VerCiclosController.filtrar(ciclos, 1, desde, null);
        assertEquals(1, resultado.size());
        assertEquals(3, resultado.get(0).getId());
    }

    @Test
    public void cicloActivoSiempreAparece_sinFiltros() {
        List<CicloLavadero> conActivo = List.of(ciclos.get(0), cicloActivo(4, 3));
        assertEquals(2, VerCiclosController.filtrar(conActivo, null, null, null).size());
    }

    @Test
    public void cicloActivoSiempreAparece_conFiltroDesde() {
        CicloLavadero activo = cicloActivo(4, 3);
        LocalDate fechaFutura = BASE.plusDays(10).toLocalDate();
        List<CicloLavadero> resultado = VerCiclosController.filtrar(List.of(activo), null, fechaFutura, null);
        assertEquals(1, resultado.size());
    }

    @Test
    public void cicloActivoSiempreAparece_conFiltroHasta() {
        CicloLavadero activo = cicloActivo(4, 3);
        LocalDate fechaPasada = BASE.minusDays(100).toLocalDate();
        List<CicloLavadero> resultado = VerCiclosController.filtrar(List.of(activo), null, null, fechaPasada);
        assertEquals(1, resultado.size());
    }

    @Test
    public void cicloActivoFiltradoPorNumeroLavarropas() {
        CicloLavadero activo = cicloActivo(4, 3);
        List<CicloLavadero> resultado = VerCiclosController.filtrar(List.of(activo), 1, null, null);
        assertEquals(0, resultado.size());
    }

    private CicloLavadero ciclo(int id, int lavarropas, LocalDateTime fechaFin) {
        return new CicloLavadero(
            id, lavarropas, TipoJabon.JABON_LIQUIDO,
            new BigDecimal("1.5"), false, null,
            BASE.minusDays(7), fechaFin, "FINALIZADO"
        );
    }

    private CicloLavadero cicloActivo(int id, int lavarropas) {
        return new CicloLavadero(
            id, lavarropas, TipoJabon.JABON_LIQUIDO,
            new BigDecimal("1.5"), false, null,
            BASE.minusDays(1), null, "ACTIVO"
        );
    }
}
