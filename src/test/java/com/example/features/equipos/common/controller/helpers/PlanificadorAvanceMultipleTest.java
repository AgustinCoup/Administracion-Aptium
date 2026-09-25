package com.example.features.equipos.common.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EntradaAvance;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EvaluacionAvance;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EvaluacionAvance.Avanzable;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EvaluacionAvance.Bloqueado;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EvaluacionAvance.NoAvanzable;
import com.example.features.equipos.common.controller.helpers.PlanificadorAvanceMultiple.EvaluacionAvance.SinSeleccion;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.service.EstadoValidatorImpl;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanificadorAvanceMultipleTest {

    private PlanificadorAvanceMultiple planificador;

    @BeforeEach
    void setUp() {
        planificador = new PlanificadorAvanceMultiple(new EstadoValidatorImpl());
    }

    // ── Sin selección ────────────────────────────────────────────────────────

    @Test
    @DisplayName("sin equipo elegido no hay selección: botón oculto con el texto genérico")
    void sinEquipo_esSinSeleccion() {
        EvaluacionAvance evaluacion = planificador.evaluar(
            new EntradaAvance(null, null, List.of(), Set.of(), false));

        assertInstanceOf(SinSeleccion.class, evaluacion);
        assertFalse(evaluacion.botonVisible());
        assertFalse(evaluacion.botonHabilitado());
        assertEquals(Constantes.Textos.BOTON_SELECCIONE_MATERIAL, evaluacion.textoBoton());
    }

    @Test
    @DisplayName("con equipo pero sin materiales seleccionados tampoco hay selección")
    void seleccionVacia_esSinSeleccion() {
        EquipoRegistrableInterface equipo = equipo(true, material(1, "Placa", 2, EstadoEquipo.NUEVO));

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo));

        assertInstanceOf(SinSeleccion.class, evaluacion);
        assertFalse(evaluacion.botonVisible());
    }

    // ── Bloqueado ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("mientras se guarda, bloqueado con 'Guardando…' aunque la selección tenga otro problema")
    void escrituraEnCurso_bloqueado() {
        MaterialRegistrableInterface noPersistido = material(null, "Placa", 2, EstadoEquipo.NUEVO);
        EquipoRegistrableInterface equipo = equipo(true, noPersistido);

        EvaluacionAvance evaluacion = planificador.evaluar(
            new EntradaAvance(equipo, equipo, List.of(noPersistido), Set.of(), true));

        assertBloqueado(Constantes.Textos.AVANCE_BLOQUEADO_GUARDANDO, evaluacion);
    }

    @Test
    @DisplayName("materiales en estados distintos: visible, deshabilitado y con el motivo")
    void estadosDistintos_bloqueadoConMotivo() {
        MaterialRegistrableInterface nuevo   = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface lavando = material(2, "Tornillo", 4, EstadoEquipo.LAVANDO);
        EquipoRegistrableInterface equipo = equipo(true, nuevo, lavando);

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo, nuevo, lavando));

        assertBloqueado(Constantes.Textos.AVANCE_BLOQUEADO_ESTADOS_DISTINTOS, evaluacion);
    }

    @Test
    @DisplayName("una fila no persistida bloquea y se nombra la primera en el orden de la tabla")
    void unoNoPersistido_bloqueadoNombrandoElPrimero() {
        MaterialRegistrableInterface placa    = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface tornillo = material(null, "Tornillo", 1, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface clavo    = material(null, "Clavo", 1, EstadoEquipo.NUEVO);
        EquipoRegistrableInterface equipo = equipo(true, placa, tornillo, clavo);

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo, placa, tornillo, clavo));

        assertBloqueado(tocado("Tornillo"), evaluacion);
    }

    @Test
    @DisplayName("una fila con un movimiento ya en el buffer bloquea y se nombra la primera")
    void unoEnBuffer_bloqueadoNombrandoElPrimero() {
        MaterialRegistrableInterface placa    = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface tornillo = material(2, "Tornillo", 1, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface clavo    = material(3, "Clavo", 1, EstadoEquipo.NUEVO);
        EquipoRegistrableInterface equipo = equipo(true, placa, tornillo, clavo);

        EvaluacionAvance evaluacion = planificador.evaluar(new EntradaAvance(
            equipo, equipo, List.of(placa, tornillo, clavo), Set.of(3, 2), false));

        assertBloqueado(tocado("Tornillo"), evaluacion);
    }

    @Test
    @DisplayName("una fila persistida que un preview parcial infló, sin estar en el buffer, bloquea")
    void filaQueRecibioCantidadDeUnPreview_bloqueada() {
        // Placa: 5 en NUEVO (id 1) y 2 en LAVANDO (id 2). Pasar 3 a LAVANDO le suma a la fila 2,
        // que queda con 5 sin estar en el buffer. Avanzarla completa pediría 5 a una fila que en
        // la base tiene 2: "Cantidad inválida" y el equipo entero falla al confirmar.
        Equipo original = equipoOrtopedia(
            new Material(1, 100, "Placa", 5, EstadoEquipo.NUEVO),
            new Material(2, 100, "Placa", 2, EstadoEquipo.LAVANDO));
        Equipo visible = equipoOrtopedia(
            new Material(1, 100, "Placa", 5, EstadoEquipo.NUEVO),
            new Material(2, 100, "Placa", 2, EstadoEquipo.LAVANDO));
        visible.aplicarMovimientoPreview(visible.getMateriales().get(0), 3, EstadoEquipo.LAVANDO);
        MaterialRegistrableInterface inflada = porId(visible, 2);
        assertEquals(5, inflada.getCantidad(), "precondición: el preview infló la fila 2");

        EvaluacionAvance evaluacion = planificador.evaluar(
            new EntradaAvance(original, visible, List.of(inflada), Set.of(1), false));

        assertBloqueado(tocado("Placa"), evaluacion);
    }

    @Test
    @DisplayName("la fila que sobrevive a la unificación de un preview completo bloquea aunque no esté en el buffer")
    void filaQueSobrevivioLaUnificacion_bloqueada() {
        // Pasar la fila 1 entera a LAVANDO la junta con la fila 2, y unificarEnMemoria se queda
        // con la del último movimiento más reciente —la 2, que no está en el buffer— y le suma todo.
        LocalDateTime ayer = LocalDateTime.of(2026, 9, 24, 10, 0);
        Equipo original = equipoOrtopedia(
            new Material(1, 100, "Placa", 3, EstadoEquipo.NUEVO),
            new Material(2, 100, "Placa", 2, EstadoEquipo.LAVANDO, ayer));
        Equipo visible = equipoOrtopedia(
            new Material(1, 100, "Placa", 3, EstadoEquipo.NUEVO),
            new Material(2, 100, "Placa", 2, EstadoEquipo.LAVANDO, ayer));
        visible.aplicarMovimientoPreview(visible.getMateriales().get(0), 3, EstadoEquipo.LAVANDO);
        assertEquals(1, visible.getMateriales().size(), "precondición: el preview unificó las dos filas");
        MaterialRegistrableInterface superviviente = porId(visible, 2);

        EvaluacionAvance evaluacion = planificador.evaluar(
            new EntradaAvance(original, visible, List.of(superviviente), Set.of(1), false));

        assertBloqueado(tocado("Placa"), evaluacion);
    }

    @Test
    @DisplayName("una fila con el mismo id y la misma cantidad pero otro estado que en el original bloquea")
    void filaConOtroEstadoQueEnElOriginal_bloqueada() {
        EquipoRegistrableInterface original = equipo(true, material(1, "Placa", 2, EstadoEquipo.NUEVO));
        MaterialRegistrableInterface enVisible = material(1, "Placa", 2, EstadoEquipo.LAVANDO);
        EquipoRegistrableInterface visible = equipo(true, enVisible);

        EvaluacionAvance evaluacion = planificador.evaluar(
            new EntradaAvance(original, visible, List.of(enVisible), Set.of(), false));

        assertBloqueado(tocado("Placa"), evaluacion);
    }

    @Test
    @DisplayName("una fila persistida que no existe en el original bloquea")
    void filaAusenteDelOriginal_bloqueada() {
        EquipoRegistrableInterface original = equipo(true, material(1, "Placa", 2, EstadoEquipo.NUEVO));
        MaterialRegistrableInterface ajena = material(9, "Tornillo", 1, EstadoEquipo.NUEVO);
        EquipoRegistrableInterface visible = equipo(true, ajena);

        EvaluacionAvance evaluacion = planificador.evaluar(
            new EntradaAvance(original, visible, List.of(ajena), Set.of(), false));

        assertBloqueado(tocado("Tornillo"), evaluacion);
    }

    @Test
    @DisplayName("tocada va antes que estados distintos: se nombra la fila, no se habla de estados")
    void noPersistidoYEstadosDistintos_ganaElNoPersistido() {
        MaterialRegistrableInterface persistida = material(1, "Placa", 3, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface partida    = material(null, "Placa", 2, EstadoEquipo.LAVANDO);
        EquipoRegistrableInterface equipo = equipo(true, persistida, partida);

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo, persistida, partida));

        assertBloqueado(tocado("Placa"), evaluacion);
    }

    // ── No avanzable ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("ESTERILIZANDO se avanza desde Lotes: botón oculto, no deshabilitado")
    void estadoEsterilizando_noAvanzable() {
        MaterialRegistrableInterface uno = material(1, "Placa", 2, EstadoEquipo.ESTERILIZANDO);
        MaterialRegistrableInterface dos = material(2, "Tornillo", 1, EstadoEquipo.ESTERILIZANDO);
        EquipoRegistrableInterface equipo = equipo(true, uno, dos);

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo, uno, dos));

        assertInstanceOf(NoAvanzable.class, evaluacion);
        assertFalse(evaluacion.botonVisible());
        assertFalse(evaluacion.botonHabilitado());
        assertEquals(Constantes.Textos.BOTON_SELECCIONE_MATERIAL, evaluacion.textoBoton());
    }

    @Test
    @DisplayName("un material en estado final (sin siguiente) oculta el botón")
    void estadoFinal_noAvanzable() {
        MaterialRegistrableInterface entregado = material(1, "Placa", 2, EstadoEquipo.ENTREGADO);
        EquipoRegistrableInterface equipo = equipo(true, entregado);

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo, entregado));

        assertInstanceOf(NoAvanzable.class, evaluacion);
        assertFalse(evaluacion.botonVisible());
    }

    // ── Avanzable ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un material avanzable: 'Pasar a <siguiente>', visible y habilitado")
    void unMaterialAvanzable_textoPasarA() {
        MaterialRegistrableInterface placa = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        EquipoRegistrableInterface equipo = equipo(true, placa);

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo, placa));

        Avanzable avance = assertInstanceOf(Avanzable.class, evaluacion);
        assertEquals(EstadoEquipo.LAVANDO, avance.siguiente());
        assertTrue(evaluacion.botonVisible());
        assertTrue(evaluacion.botonHabilitado());
        assertEquals(String.format(Constantes.Textos.BOTON_PASAR_A, "Lavando"), evaluacion.textoBoton());
    }

    @Test
    @DisplayName("tres materiales en el mismo estado: 'Pasar 3 a <siguiente>'")
    void tresMaterialesMismoEstado_textoPasarNA() {
        MaterialRegistrableInterface uno  = material(1, "Placa", 2, EstadoEquipo.LAVADO);
        MaterialRegistrableInterface dos  = material(2, "Tornillo", 5, EstadoEquipo.LAVADO);
        MaterialRegistrableInterface tres = material(3, "Clavo", 1, EstadoEquipo.LAVADO);
        EquipoRegistrableInterface equipo = equipo(true, uno, dos, tres);

        EvaluacionAvance evaluacion = planificador.evaluar(entrada(equipo, uno, dos, tres));

        Avanzable avance = assertInstanceOf(Avanzable.class, evaluacion);
        assertEquals(EstadoEquipo.EMPAQUETADO, avance.siguiente());
        assertEquals(String.format(Constantes.Textos.BOTON_PASAR_N_A, 3, "Empaquetado"), evaluacion.textoBoton());
    }

    @Test
    @DisplayName("el REMITO sin filas trae un sintético con id 0, que cuenta como persistido")
    void remitoSintetico_idCero_esAvanzable() {
        EquipoOtros remito = new EquipoOtros();
        remito.setTipoIngreso(TipoIngresoOtros.REMITO);
        remito.setRemitoCantidad(12);
        List<MaterialRegistrableInterface> seleccion = remito.getMaterialesRegistrables();
        assertEquals(0, seleccion.get(0).getId(), "precondición: material sintético del REMITO");
        assertNotSame(seleccion.get(0), remito.getMaterialesRegistrables().get(0),
            "precondición: cada llamada arma un sintético nuevo; se lo encuentra por id, no por identidad");

        EvaluacionAvance evaluacion = planificador.evaluar(
            new EntradaAvance(remito, remito, seleccion, Set.of(), false));

        Avanzable avance = assertInstanceOf(Avanzable.class, evaluacion);
        List<MovimientoMaterial> movimientos = planificador.movimientosCompletos(avance);
        assertEquals(1, movimientos.size());
        assertEquals(0, movimientos.get(0).getMaterialId());
        assertEquals(12, movimientos.get(0).getCantidad());
    }

    // ── Movimientos ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("movimientos completos: uno por material, con su cantidad entera y el estado común de origen")
    void movimientosCompletos_unoPorMaterialConSuCantidadYElEstadoComun() {
        MaterialRegistrableInterface uno = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface dos = material(2, "Tornillo", 7, EstadoEquipo.NUEVO);
        Avanzable avance = avanzable(equipo(true, uno, dos), uno, dos);

        List<MovimientoMaterial> movimientos = planificador.movimientosCompletos(avance);

        assertEquals(2, movimientos.size());
        assertMovimiento(movimientos.get(0), 1, 2, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO);
        assertMovimiento(movimientos.get(1), 2, 7, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO);
    }

    @Test
    @DisplayName("movimientos con cantidades: cada material lleva la cantidad elegida para su id")
    void movimientosConCantidades_respetaLasCantidadesPorId() {
        MaterialRegistrableInterface uno = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface dos = material(2, "Tornillo", 7, EstadoEquipo.NUEVO);
        Avanzable avance = avanzable(equipo(true, uno, dos), uno, dos);

        List<MovimientoMaterial> movimientos =
            planificador.movimientosConCantidades(avance, Map.of(2, 3, 1, 2));

        assertMovimiento(movimientos.get(0), 1, 2, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO);
        assertMovimiento(movimientos.get(1), 2, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO);
    }

    @Test
    @DisplayName("movimientos con cantidades: si falta la cantidad de un material es un bug del llamador")
    void movimientosConCantidades_faltaUnId_lanza() {
        MaterialRegistrableInterface uno = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface dos = material(2, "Tornillo", 7, EstadoEquipo.NUEVO);
        Avanzable avance = avanzable(equipo(true, uno, dos), uno, dos);

        assertThrows(IllegalArgumentException.class,
            () -> planificador.movimientosConCantidades(avance, Map.of(1, 2)));
    }

    @Test
    @DisplayName("movimientos con cantidades: fuera de 1..cantidad lanza, por abajo y por arriba")
    void movimientosConCantidades_cantidadFueraDeRango_lanza() {
        MaterialRegistrableInterface placa = material(1, "Placa", 4, EstadoEquipo.NUEVO);
        Avanzable avance = avanzable(equipo(true, placa), placa);

        assertThrows(IllegalArgumentException.class,
            () -> planificador.movimientosConCantidades(avance, Map.of(1, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> planificador.movimientosConCantidades(avance, Map.of(1, 5)));
    }

    @Test
    @DisplayName("la evaluación y los movimientos conservan el orden de la selección, no el de los ids")
    void movimientos_conservanElOrdenDeLaSeleccion() {
        MaterialRegistrableInterface tres = material(3, "Clavo", 1, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface uno  = material(1, "Placa", 2, EstadoEquipo.NUEVO);
        MaterialRegistrableInterface dos  = material(2, "Tornillo", 7, EstadoEquipo.NUEVO);
        Avanzable avance = avanzable(equipo(true, uno, dos, tres), tres, uno, dos);

        assertEquals(List.of(tres, uno, dos), avance.materiales());
        assertEquals(List.of(3, 1, 2),
            planificador.movimientosCompletos(avance).stream().map(MovimientoMaterial::getMaterialId).toList());
        assertEquals(List.of(3, 1, 2),
            planificador.movimientosConCantidades(avance, Map.of(1, 1, 2, 1, 3, 1)).stream()
                .map(MovimientoMaterial::getMaterialId).toList());
    }

    // ── Contratos de los tipos ───────────────────────────────────────────────

    @Test
    @DisplayName("con un equipo visible, el original es obligatorio: es el mismo objeto si no hay cambios")
    void entradaConVisibleSinOriginal_lanza() {
        EquipoRegistrableInterface equipo = equipo(true, material(1, "Placa", 2, EstadoEquipo.NUEVO));

        assertThrows(NullPointerException.class,
            () -> new EntradaAvance(null, equipo, List.of(), Set.of(), false));
    }

    @Test
    @DisplayName("un avance sin materiales no existe")
    void avanzableSinMateriales_lanza() {
        assertThrows(IllegalArgumentException.class, () -> new Avanzable(EstadoEquipo.LAVANDO, List.of()));
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static EntradaAvance entrada(EquipoRegistrableInterface equipo,
                                         MaterialRegistrableInterface... seleccion) {
        return new EntradaAvance(equipo, equipo, List.of(seleccion), Set.of(), false);
    }

    private Avanzable avanzable(EquipoRegistrableInterface equipo, MaterialRegistrableInterface... seleccion) {
        return assertInstanceOf(Avanzable.class, planificador.evaluar(entrada(equipo, seleccion)));
    }

    private static String tocado(String descripcion) {
        return String.format(Constantes.Textos.AVANCE_BLOQUEADO_TOCADO, descripcion);
    }

    private static void assertBloqueado(String motivo, EvaluacionAvance evaluacion) {
        assertInstanceOf(Bloqueado.class, evaluacion);
        assertTrue(evaluacion.botonVisible(), "bloqueado se ve");
        assertFalse(evaluacion.botonHabilitado(), "bloqueado no se clickea");
        assertEquals(motivo, evaluacion.textoBoton());
    }

    private static void assertMovimiento(MovimientoMaterial movimiento, int materialId, int cantidad,
                                         EstadoEquipo origen, EstadoEquipo destino) {
        assertEquals(materialId, movimiento.getMaterialId());
        assertEquals(cantidad, movimiento.getCantidad());
        assertEquals(origen, movimiento.getEstadoOrigenEsperado());
        assertEquals(destino, movimiento.getEstadoDestino());
    }

    private static MaterialRegistrableInterface porId(Equipo equipo, int id) {
        return equipo.getMaterialesRegistrables().stream()
            .filter(m -> m.getId() != null && m.getId() == id)
            .findFirst().orElseThrow();
    }

    private static Equipo equipoOrtopedia(Material... materiales) {
        Equipo equipo = new Equipo();
        for (Material material : materiales) {
            equipo.agregarMaterial(material);
        }
        return equipo;
    }

    private static EquipoRegistrableInterface equipo(boolean requiereLavado,
                                                     MaterialRegistrableInterface... materiales) {
        EquipoRegistrableInterface equipo = mock(EquipoRegistrableInterface.class);
        when(equipo.getMaterialesRegistrables()).thenReturn(List.of(materiales));
        when(equipo.getSiguienteEstado(any())).thenAnswer(inv ->
            Equipo.calcularSiguienteEstado(inv.getArgument(0), requiereLavado, true));
        return equipo;
    }

    private static MaterialRegistrableInterface material(Integer id, String descripcion, int cantidad,
                                                         EstadoEquipo estado) {
        MaterialRegistrableInterface material = mock(MaterialRegistrableInterface.class);
        when(material.getId()).thenReturn(id);
        when(material.getDescripcion()).thenReturn(descripcion);
        when(material.getCantidad()).thenReturn(cantidad);
        when(material.getEstado()).thenReturn(estado);
        when(material.esPersistido()).thenReturn(id != null);
        return material;
    }
}
