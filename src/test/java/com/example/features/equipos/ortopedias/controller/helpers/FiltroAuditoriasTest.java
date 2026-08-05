package com.example.features.equipos.ortopedias.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.features.equipos.ortopedias.model.EquipoAuditoria;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FiltroAuditoriasTest {

    private final EquipoAuditoria enero =
        auditoria(LocalDate.of(2026, 1, 15), "MODIFICACION_CANTIDAD", "ORTOPEDIA");
    private final EquipoAuditoria marzo =
        auditoria(LocalDate.of(2026, 3, 10), "ELIMINACION_EQUIPO", "OTROS");
    private final EquipoAuditoria sinFecha =
        auditoria(null, "ADICION_MATERIAL", "ORTOPEDIA");

    private final List<EquipoAuditoria> todas = List.of(enero, marzo, sinFecha);

    @Test
    @DisplayName("sin filtros devuelve todo")
    void filtrar_sinFiltros() {
        assertEquals(todas, FiltroAuditorias.filtrar(todas, FiltroAuditorias.Criterio.sinFiltros()));
    }

    @Test
    @DisplayName("el rango de fechas es inclusivo en ambos extremos")
    void filtrar_rangoInclusivo() {
        List<EquipoAuditoria> resultado = FiltroAuditorias.filtrar(todas, new FiltroAuditorias.Criterio(
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10), List.of(), List.of()));

        assertEquals(List.of(enero, marzo, sinFecha), resultado);
    }

    @Test
    @DisplayName("un registro sin fecha nunca se descarta por fecha")
    void filtrar_sinFechaSiempreEntra() {
        List<EquipoAuditoria> resultado = FiltroAuditorias.filtrar(todas, new FiltroAuditorias.Criterio(
            LocalDate.of(2030, 1, 1), null, List.of(), List.of()));

        assertEquals(List.of(sinFecha), resultado);
    }

    @Test
    @DisplayName("filtra por tipo de cambio usando la etiqueta legible")
    void filtrar_porTipoCambio() {
        List<EquipoAuditoria> resultado = FiltroAuditorias.filtrar(todas, new FiltroAuditorias.Criterio(
            null, null, List.of("Eliminación de Equipo"), List.of()));

        assertEquals(List.of(marzo), resultado);
    }

    @Test
    @DisplayName("filtra por tipo de equipo: todo lo que no es OTROS cuenta como Ortopedia")
    void filtrar_porTipoEquipo() {
        assertEquals(List.of(marzo), FiltroAuditorias.filtrar(todas, new FiltroAuditorias.Criterio(
            null, null, List.of(), List.of("Otros"))));

        assertEquals(List.of(enero, sinFecha), FiltroAuditorias.filtrar(todas, new FiltroAuditorias.Criterio(
            null, null, List.of(), List.of("Ortopedia"))));
    }

    @Test
    @DisplayName("los filtros se combinan con AND")
    void filtrar_combinaFiltros() {
        List<EquipoAuditoria> resultado = FiltroAuditorias.filtrar(todas, new FiltroAuditorias.Criterio(
            LocalDate.of(2026, 2, 1), null, List.of("Eliminación de Equipo"), List.of("Ortopedia")));

        assertTrue(resultado.isEmpty(), "marzo es OTROS, así que el filtro de equipo lo saca");
    }

    @Test
    @DisplayName("un tipo de cambio desconocido se muestra tal cual y un null como Desconocido")
    void traducirTipoCambio_casosBorde() {
        assertEquals("Desconocido", FiltroAuditorias.traducirTipoCambio(null));
        assertEquals("TIPO_NUEVO_SIN_TRADUCIR", FiltroAuditorias.traducirTipoCambio("TIPO_NUEVO_SIN_TRADUCIR"));
        assertEquals("Adición de Material", FiltroAuditorias.traducirTipoCambio("ADICION_MATERIAL"));
    }

    private static EquipoAuditoria auditoria(LocalDate fecha, String tipoCambio, String tipoEquipo) {
        EquipoAuditoria auditoria = new EquipoAuditoria();
        auditoria.setFechaCambio(fecha == null ? null : LocalDateTime.of(fecha, java.time.LocalTime.NOON));
        auditoria.setTipoCambio(tipoCambio);
        auditoria.setTipoEquipo(tipoEquipo);
        return auditoria;
    }
}
