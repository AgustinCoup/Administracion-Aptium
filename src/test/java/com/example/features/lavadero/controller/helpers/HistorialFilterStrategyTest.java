package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.IngresoHistorial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorialFilterStrategyTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2024, 6, 15, 10, 0);

    private HistorialFilterStrategy strategy;
    private List<IngresoHistorial> ingresos;

    @BeforeEach
    void setUp() {
        strategy = new HistorialFilterStrategy();
        ingresos = List.of(
            ingreso(1, "Clinica Norte", BASE.minusDays(5), EstadoIngresoLavadero.PENDIENTE,
                Set.of("Sabanas"), Set.of()),
            ingreso(2, "Sanatorio Sur", BASE.minusDays(2), EstadoIngresoLavadero.LAVADO,
                Set.of("Toallas", "Camisolines"), Set.of(3, 5)),
            ingreso(3, "Clinica Norte", BASE.minusDays(1), EstadoIngresoLavadero.FINALIZADO,
                Set.of("Sabanas"), Set.of(7))
        );
    }

    private IngresoHistorial ingreso(int id, String cliente, LocalDateTime fecha,
                                     EstadoIngresoLavadero estado,
                                     Set<String> elementos, Set<Integer> lavarropas) {
        return new IngresoHistorial(id, cliente, fecha, new BigDecimal("10.0"), 2,
            estado, elementos, lavarropas);
    }

    private List<IngresoHistorial> filtrar(HistorialFilterCriteria c) {
        return strategy.filter(ingresos, c);
    }

    private HistorialFilterCriteria criterio(String cliente, List<String> estados,
                                             LocalDate desde, LocalDate hasta,
                                             String elemento, Integer lavarropas) {
        return new HistorialFilterCriteria(cliente, estados, desde, hasta, elemento, lavarropas);
    }

    @Test
    void filtrosVacios_devuelvenTodo() {
        assertEquals(3, filtrar(criterio("", List.of(), null, null, "", null)).size());
    }

    @Test
    void sourceVacia_devuelveListaVacia() {
        assertTrue(strategy.filter(List.of(), criterio("", List.of(), null, null, "", null)).isEmpty());
        assertTrue(strategy.filter(null, criterio("", List.of(), null, null, "", null)).isEmpty());
    }

    @Test
    void filtroCliente_substringInsensibleAMayusculas() {
        List<IngresoHistorial> r = filtrar(criterio("norte", List.of(), null, null, "", null));
        assertEquals(2, r.size());
    }

    @Test
    void filtroEstado_soloLosDeLaLista() {
        List<IngresoHistorial> r = filtrar(
            criterio("", List.of("lavado"), null, null, "", null));
        assertEquals(1, r.size());
        assertEquals(2, r.get(0).id());
    }

    @Test
    void filtroFechaDesde_excluyeAnteriores() {
        LocalDate desde = BASE.minusDays(3).toLocalDate();
        assertEquals(2, filtrar(criterio("", List.of(), desde, null, "", null)).size());
    }

    @Test
    void filtroFechaHasta_excluyePosteriores() {
        LocalDate hasta = BASE.minusDays(3).toLocalDate();
        assertEquals(1, filtrar(criterio("", List.of(), null, hasta, "", null)).size());
    }

    @Test
    void filtroElemento_algunNombreContieneElTexto() {
        List<IngresoHistorial> r = filtrar(criterio("", List.of(), null, null, "toall", null));
        assertEquals(1, r.size());
        assertEquals(2, r.get(0).id());
    }

    @Test
    void filtroElemento_ingresoSinElementos_noPasa() {
        IngresoHistorial sinElementos = ingreso(9, "X", BASE, EstadoIngresoLavadero.PENDIENTE,
            Set.of(), Set.of());
        List<IngresoHistorial> r = strategy.filter(List.of(sinElementos),
            criterio("", List.of(), null, null, "algo", null));
        assertTrue(r.isEmpty());
    }

    @Test
    void filtroLavarropas_numeroExacto() {
        List<IngresoHistorial> r = filtrar(criterio("", List.of(), null, null, "", 5));
        assertEquals(1, r.size());
        assertEquals(2, r.get(0).id());
    }

    @Test
    void filtrosCombinados_seAplicanEnAND() {
        List<IngresoHistorial> r = filtrar(
            criterio("norte", List.of("FINALIZADO"), null, null, "sabana", 7));
        assertEquals(1, r.size());
        assertEquals(3, r.get(0).id());
    }

    /** El default de la pantalla: FINALIZADO desmarcado. */
    @Test
    void defaultDeLaPantalla_ingresoFinalizadoNoPasa() {
        List<IngresoHistorial> r = filtrar(criterio("", List.of("PENDIENTE", "CLASIFICADO", "LAVADO"),
            null, null, "", null));
        assertEquals(2, r.size());
        assertTrue(r.stream().noneMatch(i -> i.estado() == EstadoIngresoLavadero.FINALIZADO));
    }
}
