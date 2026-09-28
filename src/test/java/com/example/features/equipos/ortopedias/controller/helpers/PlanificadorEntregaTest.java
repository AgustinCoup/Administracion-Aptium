package com.example.features.equipos.ortopedias.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.constants.Constantes;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.common.model.FilaAEntregar;
import com.example.common.model.RemitoAEntregar;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.Plan;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.Rechazo;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.ResultadoPlan;
import com.example.features.equipos.ortopedias.controller.helpers.PlanificadorEntrega.SolicitudEntrega;
import com.example.features.equipos.ortopedias.view.helpers.InstitucionEntregaItem;
import com.example.features.equipos.ortopedias.view.helpers.MaterialEntregaItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanificadorEntregaTest {

    private static final InstitucionEntregaItem HOSPITAL =
        new InstitucionEntregaItem(new EntregaDestinoKey(TipoDestino.INSTITUCION, 7), "Hospital Central", 2);
    private static final InstitucionEntregaItem CLINICA =
        new InstitucionEntregaItem(new EntregaDestinoKey(TipoDestino.CLIENTE, 3), "Clínica Norte", 1);

    private static final MaterialEntregaItem PLACA    = fila("Pérez Juan · 25/09/2026", "Placa", 10, 501, 5);
    private static final MaterialEntregaItem TORNILLO = fila("Pérez Juan · 25/09/2026", "Tornillo", 10, 502, 8);
    private static final MaterialEntregaItem REMITO   = new MaterialEntregaItem(
        "Remito 25092026-14", "Elementos", 12, List.of(), List.of(new RemitoAEntregar(30)));

    private static final Map<EntregaDestinoKey, List<MaterialEntregaItem>> MATERIALES = Map.of(
        HOSPITAL.getKey(), List.of(PLACA, TORNILLO),
        CLINICA.getKey(),  List.of(REMITO));

    @Test
    @DisplayName("sin destino seleccionado se rechaza")
    void sinDestino_rechaza() {
        ResultadoPlan resultado = PlanificadorEntrega.planificar(List.of(), MATERIALES, List.of());

        assertEquals(new Rechazo(Constantes.Mensajes.ENTREGA_SELECCIONE_DESTINO), resultado);
    }

    @Test
    @DisplayName("varios destinos: todo lo de cada uno, aunque haya materiales seleccionados")
    void variosDestinos_entregaTodoDeCadaUnoEIgnoraLaSeleccion() {
        Plan plan = plan(PlanificadorEntrega.planificar(List.of(HOSPITAL, CLINICA), MATERIALES, List.of(PLACA)));

        assertEquals(List.of(PLACA, TORNILLO), plan.solicitudes().get(HOSPITAL.getKey()).items());
        assertEquals(List.of(REMITO), plan.solicitudes().get(CLINICA.getKey()).items());
    }

    @Test
    @DisplayName("un destino con selección: sólo lo seleccionado")
    void unDestinoConSeleccion_soloLoSeleccionado() {
        Plan plan = plan(PlanificadorEntrega.planificar(List.of(HOSPITAL), MATERIALES, List.of(TORNILLO)));

        SolicitudEntrega solicitud = plan.solicitudes().get(HOSPITAL.getKey());
        assertEquals(List.of(TORNILLO), solicitud.items());
        assertEquals(List.of(new FilaAEntregar(10, 502, 8)), solicitud.filas());
    }

    @Test
    @DisplayName("un destino con selección: lo seleccionado sale en el orden de la tabla")
    void unDestinoConSeleccion_enElOrdenDeLaTabla() {
        Plan plan = plan(PlanificadorEntrega.planificar(List.of(HOSPITAL), MATERIALES, List.of(TORNILLO, PLACA)));

        assertEquals(List.of(PLACA, TORNILLO), plan.solicitudes().get(HOSPITAL.getKey()).items());
    }

    @Test
    @DisplayName("un destino sin selección: todo")
    void unDestinoSinSeleccion_todo() {
        Plan plan = plan(PlanificadorEntrega.planificar(List.of(HOSPITAL), MATERIALES, List.of()));

        assertEquals(List.of(PLACA, TORNILLO), plan.solicitudes().get(HOSPITAL.getKey()).items());
        assertEquals("Hospital Central", plan.solicitudes().get(HOSPITAL.getKey()).nombreDestino());
    }

    @Test
    @DisplayName("un destino sin nada para entregar se excluye")
    void destinoSinPendientes_seExcluye() {
        InstitucionEntregaItem vacio =
            new InstitucionEntregaItem(new EntregaDestinoKey(TipoDestino.INSTITUCION, 99), "Vacío", 0);

        Plan plan = plan(PlanificadorEntrega.planificar(List.of(vacio, HOSPITAL), MATERIALES, List.of()));

        assertEquals(List.of(HOSPITAL.getKey()), List.copyOf(plan.solicitudes().keySet()));
    }

    @Test
    @DisplayName("si ningún destino tiene nada, se rechaza")
    void ningunoConPendientes_rechaza() {
        InstitucionEntregaItem vacio =
            new InstitucionEntregaItem(new EntregaDestinoKey(TipoDestino.INSTITUCION, 99), "Vacío", 0);

        ResultadoPlan resultado = PlanificadorEntrega.planificar(List.of(vacio), MATERIALES, List.of());

        assertEquals(new Rechazo(Constantes.Mensajes.ENTREGA_SIN_PENDIENTES), resultado);
    }

    @Test
    @DisplayName("seleccionar un REMITO lo entrega entero")
    void remitoSeleccionado_seEntregaEntero() {
        Plan plan = plan(PlanificadorEntrega.planificar(List.of(CLINICA), MATERIALES, List.of(REMITO)));

        SolicitudEntrega solicitud = plan.solicitudes().get(CLINICA.getKey());
        assertEquals(List.of(new RemitoAEntregar(30)), solicitud.remitos());
        assertTrue(solicitud.filas().isEmpty());
    }

    @Test
    @DisplayName("la solicitud concatena las filas y los remitos de sus ítems")
    void solicitud_concatenaFilasYRemitosDeSusItems() {
        MaterialEntregaItem dosLotes = new MaterialEntregaItem("x", "Placa", 7,
            List.of(new FilaAEntregar(10, 501, 3), new FilaAEntregar(10, 503, 4)), List.of());
        SolicitudEntrega solicitud =
            new SolicitudEntrega(CLINICA.getKey(), "Clínica Norte", List.of(dosLotes, REMITO, TORNILLO));

        assertEquals(List.of(new FilaAEntregar(10, 501, 3), new FilaAEntregar(10, 503, 4),
            new FilaAEntregar(10, 502, 8)), solicitud.filas());
        assertEquals(List.of(new RemitoAEntregar(30)), solicitud.remitos());
    }

    @Test
    @DisplayName("las solicitudes salen en el orden de los destinos seleccionados")
    void solicitudes_enElOrdenDeLosDestinos() {
        Plan plan = plan(PlanificadorEntrega.planificar(List.of(CLINICA, HOSPITAL), MATERIALES, List.of()));

        assertEquals(List.of(CLINICA.getKey(), HOSPITAL.getKey()), List.copyOf(plan.solicitudes().keySet()));
    }

    @Test
    @DisplayName("la confirmación tiene una línea por ítem, con su ingreso, sin fusionar por nombre")
    void textoConfirmacion_unaLineaPorItemSinFusionarIngresosDistintos() {
        MaterialEntregaItem tornilloJuan = fila("Pérez Juan · 25/09/2026", "Tornillo", 10, 501, 5);
        MaterialEntregaItem tornilloAna  = fila("Gómez Ana · 26/09/2026", "Tornillo", 11, 601, 3);
        Map<EntregaDestinoKey, List<MaterialEntregaItem>> materiales = Map.of(
            HOSPITAL.getKey(), List.of(tornilloJuan, tornilloAna),
            CLINICA.getKey(),  List.of(REMITO));

        Plan plan = plan(PlanificadorEntrega.planificar(List.of(HOSPITAL, CLINICA), materiales, List.of()));

        assertEquals("""
            ¿Confirmar entrega de los siguientes materiales?

            Institución: Hospital Central
              • Pérez Juan · 25/09/2026 — Tornillo × 5
              • Gómez Ana · 26/09/2026 — Tornillo × 3

            Cliente: Clínica Norte
              • Remito 25092026-14 — Elementos × 12

            """, plan.textoConfirmacion());
    }

    @Test
    @DisplayName("un ítem con filas y remito a la vez, o con ninguno, no se puede construir")
    void item_exactamenteUnaListaNoVacia() {
        List<FilaAEntregar> filas = List.of(new FilaAEntregar(10, 501, 1));
        List<RemitoAEntregar> remitos = List.of(new RemitoAEntregar(30));

        assertThrows(IllegalArgumentException.class,
            () -> new MaterialEntregaItem("x", "y", 1, filas, remitos));
        assertThrows(IllegalArgumentException.class,
            () -> new MaterialEntregaItem("x", "y", 1, List.of(), List.of()));
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static Plan plan(ResultadoPlan resultado) {
        return assertInstanceOf(Plan.class, resultado);
    }

    private static MaterialEntregaItem fila(String ingreso, String material, int equipoId, int materialId,
                                            int cantidad) {
        return new MaterialEntregaItem(ingreso, material, cantidad,
            List.of(new FilaAEntregar(equipoId, materialId, cantidad)), List.of());
    }
}
