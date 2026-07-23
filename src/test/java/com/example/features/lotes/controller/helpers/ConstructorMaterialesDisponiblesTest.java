package com.example.features.lotes.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConstructorMaterialesDisponiblesTest {

    private final ConstructorMaterialesDisponibles constructor = new ConstructorMaterialesDisponibles();

    @Test
    @DisplayName("solo entran los materiales cuya próxima transición es ESTERILIZANDO")
    void construir_filtraPorSiguienteEstado() {
        Material listo    = material(1, 100, "Placa", 4, EstadoEquipo.EMPAQUETADO);
        Material temprano = material(2, 101, "Clavo", 9, EstadoEquipo.NUEVO);
        Equipo equipo = ortopedia(7, "Cliente A", List.of(listo, temprano),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO,
                   EstadoEquipo.NUEVO,       EstadoEquipo.LAVANDO));

        List<MaterialLoteItem> disponibles = constructor.construir(
            List.of(equipo), List.of(), Map.of(100, 5), Map.of());

        assertEquals(1, disponibles.size());
        assertEquals("Placa", disponibles.get(0).getDescripcion());
        assertEquals(4, disponibles.get(0).getCantidad());
        assertEquals(5, disponibles.get(0).getVolumen(), "el volumen sale del catálogo");
        assertEquals("Cliente A", disponibles.get(0).getClienteNombre());
    }

    @Test
    @DisplayName("un código sin volumen en el catálogo usa el volumen por defecto")
    void construir_volumenPorDefecto() {
        Equipo equipo = ortopedia(7, "Cliente A",
            List.of(material(1, 999, "Placa", 1, EstadoEquipo.EMPAQUETADO)),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO));

        List<MaterialLoteItem> disponibles = constructor.construir(
            List.of(equipo), List.of(), Map.of(), Map.of());

        assertEquals(1, disponibles.get(0).getVolumen());
    }

    @Test
    @DisplayName("lo ya cargado en un autoclave se descuenta de disponibles")
    void construir_descuentaPendientes() {
        Equipo equipo = ortopedia(7, "Cliente A",
            List.of(material(1, 100, "Placa", 10, EstadoEquipo.EMPAQUETADO)),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO));
        MaterialLoteItem pendiente = new MaterialLoteItem(1, 7, "Placa", 4, 5);

        List<MaterialLoteItem> disponibles = constructor.construir(
            List.of(equipo), List.of(), Map.of(100, 5), Map.of("A1", List.of(pendiente)));

        assertEquals(6, disponibles.get(0).getCantidad());
    }

    @Test
    @DisplayName("si lo pendiente cubre todo el material, la fila desaparece")
    void construir_pendienteCubreTodo() {
        Equipo equipo = ortopedia(7, "Cliente A",
            List.of(material(1, 100, "Placa", 4, EstadoEquipo.EMPAQUETADO)),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO));
        MaterialLoteItem pendiente = new MaterialLoteItem(1, 7, "Placa", 4, 5);

        List<MaterialLoteItem> disponibles = constructor.construir(
            List.of(equipo), List.of(), Map.of(100, 5), Map.of("A1", List.of(pendiente)));

        assertTrue(disponibles.isEmpty());
    }

    @Test
    @DisplayName("un REMITO sin filas rinde una sola fila con materialId negativo")
    void construir_remitoSinFilas() {
        EquipoOtros equipo = otros(3, "Clínica Norte", TipoIngresoOtros.REMITO, 12, List.of(),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO));
        when(equipo.getEstado()).thenReturn(EstadoEquipo.EMPAQUETADO);

        List<MaterialLoteItem> disponibles = constructor.construir(
            List.of(), List.of(equipo), Map.of(), Map.of());

        assertEquals(1, disponibles.size());
        assertEquals(-3, disponibles.get(0).getMaterialId(), "el id negativo marca el REMITO para el DAO");
        assertEquals("Elementos", disponibles.get(0).getDescripcion());
        assertEquals(12, disponibles.get(0).getCantidad());
        assertTrue(disponibles.get(0).isEsOtros());
    }

    @Test
    @DisplayName("un REMITO con filas reales se trata como DETALLES")
    void construir_remitoConFilas() {
        MaterialOtros material = materialOtros(50, "Bandeja", 2, EstadoEquipo.EMPAQUETADO);
        EquipoOtros equipo = otros(3, "Clínica Norte", TipoIngresoOtros.REMITO, 12, List.of(material),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO));

        List<MaterialLoteItem> disponibles = constructor.construir(
            List.of(), List.of(equipo), Map.of(), Map.of());

        assertEquals(1, disponibles.size());
        assertEquals(50, disponibles.get(0).getMaterialId());
        assertEquals("Bandeja", disponibles.get(0).getDescripcion());
    }

    @Test
    @DisplayName("ortopedias y otros con el mismo id no se pisan entre sí")
    void construir_noColisionaIdsEntreTipos() {
        Equipo ortopedia = ortopedia(7, "Cliente A",
            List.of(material(1, 100, "Placa", 3, EstadoEquipo.EMPAQUETADO)),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO));
        MaterialOtros mismoId = materialOtros(1, "Bandeja", 3, EstadoEquipo.EMPAQUETADO);
        EquipoOtros otros = otros(3, "Cliente B", TipoIngresoOtros.DETALLES, 0, List.of(mismoId),
            Map.of(EstadoEquipo.EMPAQUETADO, EstadoEquipo.ESTERILIZANDO));

        // Un pendiente de ortopedia no debe descontarle al "otros" con el mismo materialId.
        MaterialLoteItem pendienteOrtopedia = new MaterialLoteItem(1, 7, "Placa", 3, 1);

        List<MaterialLoteItem> disponibles = constructor.construir(
            List.of(ortopedia), List.of(otros), Map.of(), Map.of("A1", List.of(pendienteOrtopedia)));

        assertEquals(1, disponibles.size());
        assertEquals("Bandeja", disponibles.get(0).getDescripcion());
    }

    @Test
    @DisplayName("un equipo sin materiales no rompe ni aporta filas")
    void construir_equipoSinMateriales() {
        Equipo equipo = mock(Equipo.class);
        when(equipo.getMateriales()).thenReturn(null);

        assertTrue(constructor.construir(List.of(equipo), List.of(), Map.of(), Map.of()).isEmpty());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static Equipo ortopedia(int id, String cliente, List<Material> materiales,
                                    Map<EstadoEquipo, EstadoEquipo> transiciones) {
        Equipo equipo = mock(Equipo.class);
        when(equipo.getId()).thenReturn(id);
        when(equipo.getClienteNombre()).thenReturn(cliente);
        when(equipo.getMateriales()).thenReturn(materiales);
        transiciones.forEach((desde, hasta) -> when(equipo.getSiguienteEstado(desde)).thenReturn(hasta));
        return equipo;
    }

    private static Material material(int id, int codigo, String descripcion, int cantidad, EstadoEquipo estado) {
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(id);
        when(material.getCodigo()).thenReturn(codigo);
        when(material.getDescripcion()).thenReturn(descripcion);
        when(material.getCantidad()).thenReturn(cantidad);
        when(material.getEstado()).thenReturn(estado);
        return material;
    }

    private static EquipoOtros otros(int id, String cliente, TipoIngresoOtros tipo, int remitoCantidad,
                                     List<MaterialOtros> materiales,
                                     Map<EstadoEquipo, EstadoEquipo> transiciones) {
        EquipoOtros equipo = mock(EquipoOtros.class);
        when(equipo.getId()).thenReturn(id);
        when(equipo.getClienteNombre()).thenReturn(cliente);
        when(equipo.getTipoIngreso()).thenReturn(tipo);
        when(equipo.getRemitoCantidad()).thenReturn(remitoCantidad);
        when(equipo.getMateriales()).thenReturn(materiales);
        transiciones.forEach((desde, hasta) -> when(equipo.getSiguienteEstado(desde)).thenReturn(hasta));
        return equipo;
    }

    private static MaterialOtros materialOtros(int id, String descripcion, int cantidad, EstadoEquipo estado) {
        MaterialOtros material = mock(MaterialOtros.class);
        when(material.getId()).thenReturn(id);
        when(material.getDescripcion()).thenReturn(descripcion);
        when(material.getCantidad()).thenReturn(cantidad);
        when(material.getEstado()).thenReturn(estado);
        return material;
    }
}
