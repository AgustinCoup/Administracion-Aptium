package com.example.features.equipos.ortopedias.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.constants.Constantes;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.common.model.FilaAEntregar;
import com.example.common.model.RemitoAEntregar;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.service.EstadoValidatorImpl;
import com.example.features.equipos.ortopedias.view.helpers.MaterialEntregaItem;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgrupadorEntregasTest {

    private static final LocalDateTime INGRESO = LocalDateTime.of(2026, 9, 25, 10, 30);
    private static final String FECHA = "25/09/2026";
    private static final EntregaDestinoKey INSTITUCION_7 = new EntregaDestinoKey(TipoDestino.INSTITUCION, 7);
    private static final EntregaDestinoKey CLIENTE_3 = new EntregaDestinoKey(TipoDestino.CLIENTE, 3);

    private final AgrupadorEntregas agrupador = new AgrupadorEntregas(new EstadoValidatorImpl());

    @Test
    @DisplayName("sin equipos no hay destinos")
    void agrupar_sinEquipos() {
        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of());

        assertTrue(resultado.filas().isEmpty());
        assertTrue(resultado.materialesPorDestino().isEmpty());
        assertTrue(resultado.volumenPorDestino().isEmpty());
    }

    // ── Ortopedias ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("el ítem trae el id y la cantidad de cada fila esterilizada, y el ingreso con paciente")
    void ortopedias_filaTraeIdsYCantidadDeLasFilasEsterilizadas() {
        Equipo equipo = ortopedia(10, 7, "Hospital Central", "Pérez Juan",
            new Material(501, 100, "Placa", 5, EstadoEquipo.ESTERILIZADO));

        MaterialEntregaItem item = unico(agrupador.agrupar(List.of(equipo), List.of()), INSTITUCION_7);

        assertEquals("Placa", item.material());
        assertEquals(5, item.cantidad());
        assertEquals(List.of(new FilaAEntregar(10, 501, 5)), item.filas());
        assertTrue(item.remitos().isEmpty());
        assertEquals("Pérez Juan · " + FECHA, item.ingreso());
    }

    @Test
    @DisplayName("las filas ENTREGADO no participan: ni en la cantidad ni en las filas")
    void ortopedias_filasEntregadasNoParticipan() {
        Equipo equipo = ortopedia(10, 7, "Hospital Central", "Pérez Juan",
            new Material(501, 100, "Placa", 5, EstadoEquipo.ESTERILIZADO),
            new Material(502, 100, "Placa", 2, EstadoEquipo.ENTREGADO));

        MaterialEntregaItem item = unico(agrupador.agrupar(List.of(equipo), List.of()), INSTITUCION_7);

        assertEquals(5, item.cantidad());
        assertEquals(List.of(new FilaAEntregar(10, 501, 5)), item.filas());
        assertEquals("Pérez Juan · " + FECHA, item.ingreso(), "esterilizado + entregado está completo");
    }

    @Test
    @DisplayName("un material entregado por completo no genera fila")
    void agrupar_todoEntregadoNoAparece() {
        Equipo equipo = ortopedia(10, 7, "Hospital Central", null,
            new Material(501, 100, "Placa", 3, EstadoEquipo.ENTREGADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(equipo), List.of());

        assertTrue(resultado.filas().isEmpty(), "sin materiales pendientes no debe haber destino");
    }

    @Test
    @DisplayName("el mismo código en filas de lotes distintos es UN ítem con TODAS sus filas")
    void ortopedias_filasDeLotesDistintos_unItemConVariasFilas() {
        Equipo equipo = ortopedia(10, 7, "Hospital Central", null,
            new Material(501, 100, "Placa", 3, EstadoEquipo.ESTERILIZADO),
            new Material(502, 200, "Tornillo", 8, EstadoEquipo.ESTERILIZADO),
            new Material(503, 100, "Placa", 4, EstadoEquipo.ESTERILIZADO));

        List<MaterialEntregaItem> items =
            agrupador.agrupar(List.of(equipo), List.of()).materialesPorDestino().get(INSTITUCION_7);

        assertEquals(List.of("Placa", "Tornillo"), items.stream().map(MaterialEntregaItem::material).toList());
        assertEquals(7, items.get(0).cantidad());
        assertEquals(List.of(new FilaAEntregar(10, 501, 3), new FilaAEntregar(10, 503, 4)), items.get(0).filas());
    }

    @Test
    @DisplayName("un equipo incompleto aparece: sólo sus filas esterilizadas, con el ingreso marcado")
    void equipoIncompleto_muestraSusFilasEsterilizadasMarcadas() {
        Equipo equipo = ortopedia(10, 7, "Hospital Central", "Pérez Juan",
            new Material(501, 100, "Placa", 5, EstadoEquipo.ESTERILIZADO),
            new Material(502, 200, "Tornillo", 8, EstadoEquipo.LAVADO));

        MaterialEntregaItem item = unico(agrupador.agrupar(List.of(equipo), List.of()), INSTITUCION_7);

        assertEquals("Placa", item.material());
        assertEquals(List.of(new FilaAEntregar(10, 501, 5)), item.filas());
        assertEquals("Pérez Juan · " + FECHA + Constantes.Textos.INGRESO_INCOMPLETO, item.ingreso());
    }

    @Test
    @DisplayName("dos ingresos de la misma institución con el mismo material son dos ítems con ingreso distinto")
    void dosIngresosMismaInstitucion_mismoMaterial_dosItemsConIngresoDistinto() {
        Equipo uno = ortopedia(10, 7, "Hospital Central", "Pérez Juan",
            new Material(501, 100, "Tornillo", 5, EstadoEquipo.ESTERILIZADO));
        Equipo dos = ortopedia(11, 7, "Hospital Central", "Gómez Ana",
            new Material(601, 100, "Tornillo", 5, EstadoEquipo.ESTERILIZADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(uno, dos), List.of());

        List<MaterialEntregaItem> items = resultado.materialesPorDestino().get(INSTITUCION_7);
        assertEquals(2, items.size());
        assertEquals(List.of("Pérez Juan · " + FECHA, "Gómez Ana · " + FECHA),
            items.stream().map(MaterialEntregaItem::ingreso).toList());
        assertEquals(List.of(new FilaAEntregar(11, 601, 5)), items.get(1).filas());
        assertEquals(2, resultado.filas().get(0).getEquiposCount());
    }

    @Test
    @DisplayName("sin paciente el ingreso es sólo la fecha; sin fecha, el marcador de dato faltante")
    void ingresoOrtopediasSinPaciente_soloFecha() {
        Equipo sinPaciente = ortopedia(10, 7, "Hospital Central", "  ",
            new Material(501, 100, "Placa", 1, EstadoEquipo.ESTERILIZADO));
        Equipo sinFecha = ortopedia(11, 8, "Clínica Sur", null,
            new Material(601, 100, "Placa", 1, EstadoEquipo.ESTERILIZADO));
        sinFecha.setFechaIngreso(null);

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(sinPaciente, sinFecha), List.of());

        assertEquals(FECHA, unico(resultado, INSTITUCION_7).ingreso());
        assertEquals(Constantes.Textos.SIN_DATO,
            unico(resultado, new EntregaDestinoKey(TipoDestino.INSTITUCION, 8)).ingreso());
    }

    @Test
    @DisplayName("los equipos sin institución caen en el destino 'sin institución'")
    void agrupar_sinInstitucion() {
        Equipo equipo = ortopedia(10, null, "  ", null,
            new Material(501, 100, "Placa", 1, EstadoEquipo.ESTERILIZADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(equipo), List.of());

        assertEquals(1, resultado.filas().size());
        assertEquals(-1, resultado.filas().get(0).getKey().getId());
        assertEquals(Constantes.Textos.SIN_INSTITUCION, resultado.filas().get(0).getNombre());
    }

    // ── Otros ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("otros DETALLES: un ítem por descripción, ingreso = fecha, incompleto marcado")
    void otrosDetalles_agrupaPorDescripcionYMarcaIncompleto() {
        EquipoOtros equipo = detalles(20, 3, "Clínica Norte", 40,
            new MaterialOtros(701, null, "Sábana", 4, EstadoEquipo.ESTERILIZADO, null),
            new MaterialOtros(702, null, "Sábana", 2, EstadoEquipo.ESTERILIZADO, null),
            new MaterialOtros(703, null, "Toalla", 6, EstadoEquipo.EMPAQUETADO, null));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(equipo));

        MaterialEntregaItem item = unico(resultado, CLIENTE_3);
        assertEquals("Sábana", item.material());
        assertEquals(6, item.cantidad());
        assertEquals(List.of(new FilaAEntregar(20, 701, 4), new FilaAEntregar(20, 702, 2)), item.filas());
        assertEquals(FECHA + Constantes.Textos.INGRESO_INCOMPLETO, item.ingreso());
        assertEquals(40, resultado.volumenPorDestino().get(CLIENTE_3));
    }

    @Test
    @DisplayName("un REMITO sin filas reales es un ítem con el remito, sin filas, y acumula volumen")
    void remitoSinFilas_unItemConRemitoYSinFilas() {
        EquipoOtros equipo = remito(30, 3, "Clínica Norte", "25092026-14", 12, 40);

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(equipo));

        MaterialEntregaItem item = unico(resultado, CLIENTE_3);
        assertEquals(Constantes.Textos.MATERIAL_REMITO, item.material());
        assertEquals(12, item.cantidad());
        assertEquals(List.of(new RemitoAEntregar(30)), item.remitos());
        assertTrue(item.filas().isEmpty());
        assertEquals("Remito 25092026-14", item.ingreso());
        assertEquals(40, resultado.volumenPorDestino().get(CLIENTE_3));
    }

    @Test
    @DisplayName("un REMITO con filas es UN solo ítem con todas sus filas esterilizadas")
    void remitoConFilas_unSoloItemConTodasSusFilas() {
        EquipoOtros equipo = remito(30, 3, "Clínica Norte", "25092026-14", 12, 40,
            new MaterialOtros(801, null, "Elementos", 5, EstadoEquipo.ESTERILIZADO, null),
            new MaterialOtros(802, null, "Elementos", 7, EstadoEquipo.ESTERILIZADO, null));

        MaterialEntregaItem item = unico(agrupador.agrupar(List.of(), List.of(equipo)), CLIENTE_3);

        assertEquals(12, item.cantidad());
        assertEquals(List.of(new FilaAEntregar(30, 801, 5), new FilaAEntregar(30, 802, 7)), item.filas());
        assertTrue(item.remitos().isEmpty());
    }

    @Test
    @DisplayName("un REMITO con alguna fila todavía en proceso no se muestra: se entrega entero")
    void remitoIncompleto_noSeMuestra() {
        EquipoOtros equipo = remito(30, 3, "Clínica Norte", "25092026-14", 12, 40,
            new MaterialOtros(801, null, "Elementos", 5, EstadoEquipo.ESTERILIZADO, null),
            new MaterialOtros(802, null, "Elementos", 7, EstadoEquipo.ESTERILIZANDO, null));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(equipo));

        assertTrue(resultado.filas().isEmpty());
        assertTrue(resultado.volumenPorDestino().isEmpty());
    }

    @Test
    @DisplayName("un REMITO sin filas ya entregado no se muestra: el agrupador lo filtra por sí mismo")
    void remitoEntregadoSinFilas_noSeMuestra() {
        // Antes dependía del WHERE de obtenerActivos(). Ahora la regla "sólo completo" pide
        // ESTERILIZADO exacto: un remito ENTREGADO ofrecería una entrega que la guarda rechaza.
        EquipoOtros equipo = remito(30, 3, "Clínica Norte", "25092026-14", 12, 40);
        equipo.setEstado(EstadoEquipo.ENTREGADO);

        assertTrue(agrupador.agrupar(List.of(), List.of(equipo)).filas().isEmpty());
    }

    @Test
    @DisplayName("un REMITO con todas sus filas ya entregadas no se muestra")
    void agrupar_remitoConFilasEntregadas_noAparece() {
        EquipoOtros equipo = remito(30, 3, "Clínica Norte", "25092026-14", 12, 40,
            new MaterialOtros(801, null, "Elementos", 12, EstadoEquipo.ENTREGADO, null));

        assertTrue(agrupador.agrupar(List.of(), List.of(equipo)).filas().isEmpty());
    }

    @Test
    @DisplayName("dos equipos del mismo cliente suman volumen y comparten destino")
    void agrupar_otrosDelMismoClienteSeSuman() {
        EquipoOtros uno = remito(30, 3, "Clínica Norte", "A", 1, 40);
        EquipoOtros dos = remito(31, 3, "Clínica Norte", "B", 2, 25);

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(), List.of(uno, dos));

        assertEquals(1, resultado.filas().size());
        assertEquals(2, resultado.filas().get(0).getEquiposCount());
        assertEquals(65, resultado.volumenPorDestino().get(CLIENTE_3));
    }

    @Test
    @DisplayName("un cliente sin nombre cae en 'sin cliente'")
    void agrupar_otrosSinNombreDeCliente() {
        EquipoOtros equipo = remito(30, 3, null, "A", 1, 40);

        assertEquals(Constantes.Textos.SIN_CLIENTE,
            agrupador.agrupar(List.of(), List.of(equipo)).filas().get(0).getNombre());
    }

    @Test
    @DisplayName("los destinos salen ordenados por nombre, sin distinguir mayúsculas")
    void agrupar_ordenaPorNombre() {
        Equipo zeta = ortopedia(10, 1, "zeta", null, new Material(501, 100, "Placa", 1, EstadoEquipo.ESTERILIZADO));
        Equipo alfa = ortopedia(11, 2, "Alfa", null, new Material(601, 100, "Placa", 1, EstadoEquipo.ESTERILIZADO));

        AgrupadorEntregas.Resultado resultado = agrupador.agrupar(List.of(zeta, alfa), List.of());

        assertEquals(List.of("Alfa", "zeta"),
            resultado.filas().stream().map(f -> f.getNombre()).toList());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static MaterialEntregaItem unico(AgrupadorEntregas.Resultado resultado, EntregaDestinoKey key) {
        List<MaterialEntregaItem> items = resultado.materialesPorDestino().get(key);
        assertEquals(1, items.size(), "se esperaba un solo ítem en " + key);
        return items.get(0);
    }

    private static Equipo ortopedia(int id, Integer nroInstitucion, String institucion, String paciente,
                                    Material... materiales) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNroInstitucion(nroInstitucion);
        equipo.setInstitucionNombre(institucion);
        equipo.setPacienteNombre(paciente);
        equipo.setFechaIngreso(INGRESO);
        for (Material material : materiales) equipo.agregarMaterial(material);
        return equipo;
    }

    private static EquipoOtros detalles(int id, int nroCliente, String cliente, int volumen,
                                        MaterialOtros... materiales) {
        EquipoOtros equipo = otros(id, nroCliente, cliente, volumen, materiales);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        return equipo;
    }

    private static EquipoOtros remito(int id, int nroCliente, String cliente, String remitoId,
                                      int remitoCantidad, int volumen, MaterialOtros... materiales) {
        EquipoOtros equipo = otros(id, nroCliente, cliente, volumen, materiales);
        equipo.setTipoIngreso(TipoIngresoOtros.REMITO);
        equipo.setRemitoId(remitoId);
        equipo.setRemitoCantidad(remitoCantidad);
        equipo.setEstado(EstadoEquipo.ESTERILIZADO); // el estado de un remito sin filas vive acá
        return equipo;
    }

    private static EquipoOtros otros(int id, int nroCliente, String cliente, int volumen,
                                     MaterialOtros... materiales) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setId(id);
        equipo.setNroCliente(nroCliente);
        equipo.setClienteNombre(cliente);
        equipo.setVolumenEquipo(volumen);
        equipo.setFechaIngreso(INGRESO);
        for (MaterialOtros material : materiales) equipo.agregarMaterial(material);
        return equipo;
    }
}
