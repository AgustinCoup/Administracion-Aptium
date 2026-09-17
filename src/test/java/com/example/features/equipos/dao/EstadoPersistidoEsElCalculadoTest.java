package com.example.features.equipos.dao;

import com.example.AbstractDAOTest;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.dao.MaterialDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>La compuerta del Paso 10 de {@code plans/conexiones-y-paginacion.md}</b>: la columna
 * {@code estado} de {@code equipos} y de {@code equipo_otros} vale <b>exactamente</b> lo que
 * {@code calcularEstado()} computa en memoria, para <b>toda</b> fila.
 *
 * <h2>Por qué esta equivalencia decide un diseño entero</h2>
 * La pantalla Estado de Procesos filtra por {@code eq.calcularEstado().getNombre()}
 * ({@code CdeFilterStrategy}) y <b>ordena</b> por {@code calcularEstado().getOrden()}
 * ({@code EquipoTableModel.actualizarDatos}): las dos cosas sobre un valor <em>derivado</em>, no
 * sobre una columna. Para paginar en SQL hay que filtrar y ordenar en SQL, y hay exactamente dos
 * salidas:
 *
 * <ol>
 *   <li>usar la columna {@code estado}, si resulta ser ese mismo valor derivado — que es lo que
 *       este test prueba; o</li>
 *   <li>replicar el {@code MIN(CASE …)} de {@code calcularEstado()} dentro de cada consulta de
 *       listado, o sea duplicar lógica de dominio en SQL.</li>
 * </ol>
 *
 * <p>La (1) se sostiene porque {@code EquipoMaterialHelper.recalcularEstadoEquipo} y
 * {@code EquipoOtrosMaterialHelper.recalcularEstadoEquipo} persisten en la columna el mismo
 * {@code MIN} sobre los materiales que el modelo calcula en memoria. <b>Eso es una hipótesis, no
 * un hecho</b>, y las dos ramas de excepción de {@code calcularEstado()} son el lugar donde podría
 * romperse: {@code Equipo} sin materiales devuelve {@code NUEVO} por constante, mientras que el
 * helper llega a {@code NUEVO} por el fallback del {@code MIN} nulo; y {@code EquipoOtros} sin
 * materiales devuelve <em>la propia columna</em>, que es el caso del REMITO todavía sin mover.
 *
 * <p><b>Este test se queda en la suite para siempre.</b> No es un chequeo de una sola vez sobre
 * una decisión ya tomada: es lo que impide que la divergencia vuelva. El día que alguien agregue
 * una ruta de escritura que toque materiales sin pasar por el recálculo, o que cambie una rama de
 * {@code calcularEstado()}, el filtro y el orden del CDE empezarían a mentir en silencio — una
 * fila en la página equivocada no se parece a un error. Acá salta.
 *
 * <p>Los equipos se siembran por las <b>rutas de escritura reales</b> ({@code guardarEquipo},
 * {@code aplicarMovimientos}), no con {@code UPDATE} a mano: lo que hay que probar es que el
 * mantenimiento de la columna es correcto tal como corre en producción. Sembrar el estado a mano
 * probaría que el test sabe escribir SQL.
 */
@DisplayName("La columna estado equivale a calcularEstado() en las dos tablas")
class EstadoPersistidoEsElCalculadoTest extends AbstractDAOTest {

    private final EquipoDAO       equipoDAO  = new EquipoDAO();
    private final MaterialDAO     materialDAO = new MaterialDAO();
    private final EquipoOtrosDAO  otrosDAO   = new EquipoOtrosDAO(new CatalogoOtrosDAO());

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM material_movimientos");
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestDesc%' OR descripcion = 'Elementos'");
    }

    // ── ortopedias ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("equipos: la columna coincide fila por fila, incluidos los casos de excepción")
    void ortopedias_columnaEstadoEquivaleACalcularEstado() {
        int sinMateriales   = ortopediaSinMateriales();
        int todosNuevo      = ortopediaConMateriales(3);
        int mezclado        = ortopediaConMateriales(3);
        int todosEntregados = ortopediaConMateriales(2);
        int splitParcial    = ortopediaConMateriales(1);

        avanzarPrimerMaterial(mezclado, EstadoEquipo.ESTERILIZADO);
        avanzarTodosLosMateriales(todosEntregados, EstadoEquipo.ENTREGADO);
        avanzarParteDelPrimerMaterial(splitParcial, 2, EstadoEquipo.EMPAQUETADO);

        List<Equipo> todos = equipoDAO.obtenerTodos();
        assertEquals(5, todos.size(), "los cinco equipos sembrados tienen que estar");

        for (Equipo equipo : todos) {
            assertEquals(equipo.calcularEstado(), equipo.getEstado(),
                "equipo id=" + equipo.getId() + ": la columna dice " + equipo.getEstado()
                    + " y calcularEstado() dice " + equipo.calcularEstado()
                    + " (materiales: " + estadosDeMateriales(equipo) + ")");
        }

        // Que el conjunto sembrado sea el que se quería: un test verde sobre cinco equipos todos
        // en NUEVO no probaría nada, y es exactamente en lo que se degrada solo si mañana alguien
        // toca los helpers de arriba sin mirar acá.
        assertEquals(EstadoEquipo.NUEVO,       estadoDe(todos, sinMateriales));
        assertEquals(EstadoEquipo.NUEVO,       estadoDe(todos, todosNuevo));
        assertEquals(EstadoEquipo.NUEVO,       estadoDe(todos, mezclado),
            "mezclado: un material en ESTERILIZADO y dos en NUEVO manda el más atrasado");
        assertEquals(EstadoEquipo.ENTREGADO,   estadoDe(todos, todosEntregados));
        assertEquals(EstadoEquipo.NUEVO,       estadoDe(todos, splitParcial),
            "split parcial: la fila que quedó atrás sigue mandando");
    }

    // ── otros ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("equipo_otros: la columna coincide fila por fila, REMITO sin materiales incluido")
    void otros_columnaEstadoEquivaleACalcularEstado() {
        int detallesSinMateriales = otrosDetalles();                       // degenerado: sin filas
        int detallesTodosNuevo    = otrosDetalles("TestDescA", "TestDescB");
        int detallesMezclado      = otrosDetalles("TestDescC", "TestDescD");
        int remitoSinMover        = otrosRemito(5);                        // la rama de excepción
        int remitoSplitParcial    = otrosRemito(5);
        int remitoAvanzadoEntero  = otrosRemito(4);

        avanzarPrimerMaterialOtros(detallesMezclado, EstadoEquipo.ESTERILIZADO);
        avanzarRemito(remitoSplitParcial,   2, EstadoEquipo.LAVANDO);
        avanzarRemito(remitoAvanzadoEntero, 4, EstadoEquipo.EMPAQUETADO);

        List<EquipoOtros> todos = otrosDAO.obtenerTodos();
        assertEquals(6, todos.size(), "los seis equipos 'otros' sembrados tienen que estar");

        for (EquipoOtros equipo : todos) {
            assertEquals(equipo.calcularEstado(), equipo.getEstado(),
                "equipo_otros id=" + equipo.getId() + ": la columna dice " + equipo.getEstado()
                    + " y calcularEstado() dice " + equipo.calcularEstado()
                    + " (tipo=" + equipo.getTipoIngreso()
                    + ", materiales: " + estadosDeMaterialesOtros(equipo) + ")");
        }

        assertEquals(EstadoEquipo.NUEVO,       estadoDeOtros(todos, detallesSinMateriales));
        assertEquals(EstadoEquipo.NUEVO,       estadoDeOtros(todos, detallesTodosNuevo));
        assertEquals(EstadoEquipo.NUEVO,       estadoDeOtros(todos, detallesMezclado));
        assertEquals(EstadoEquipo.NUEVO,       estadoDeOtros(todos, remitoSinMover));
        assertEquals(EstadoEquipo.NUEVO,       estadoDeOtros(todos, remitoSplitParcial),
            "split parcial del remito: las 3 unidades que no avanzaron siguen mandando");
        assertEquals(EstadoEquipo.EMPAQUETADO, estadoDeOtros(todos, remitoAvanzadoEntero),
            "remito avanzado entero: no queda fila atrasada");

        assertTrue(todos.stream().anyMatch(e -> e.getId() == remitoSinMover && e.getMateriales().isEmpty()),
            "el REMITO sin mover tiene que seguir sin filas de material: es la rama de excepción "
                + "que este test existe para cubrir");
    }

    // ── siembra: ortopedias ──────────────────────────────────────────────────

    private int ortopediaSinMateriales() {
        Equipo equipo = equipoBase();
        equipoDAO.guardarEquipo(equipo);
        return equipo.getId();
    }

    private int ortopediaConMateriales(int cuantos) {
        Equipo equipo = equipoBase();
        for (int i = 0; i < cuantos; i++) {
            equipo.agregarMaterial(new Material(400 + i, "Tornillera", 3));
        }
        equipoDAO.guardarEquipo(equipo);
        return equipo.getId();
    }

    private Equipo equipoBase() {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);        // primer cliente del seed
        equipo.setNroInstitucion(1);    // primera institución del seed
        equipo.setClienteNombre("Cliente Seed");
        equipo.setEstado(EstadoEquipo.NUEVO);
        return equipo;
    }

    private void avanzarPrimerMaterial(int equipoId, EstadoEquipo destino) {
        Material material = recargarOrtopedia(equipoId).getMateriales().get(0);
        materialDAO.aplicarMovimientos(equipoId, List.of(new MovimientoMaterial(
            material.getId(), material.getCantidad(), material.getEstado(), destino)));
    }

    private void avanzarParteDelPrimerMaterial(int equipoId, int cantidad, EstadoEquipo destino) {
        Material material = recargarOrtopedia(equipoId).getMateriales().get(0);
        materialDAO.aplicarMovimientos(equipoId, List.of(new MovimientoMaterial(
            material.getId(), cantidad, material.getEstado(), destino)));
    }

    private void avanzarTodosLosMateriales(int equipoId, EstadoEquipo destino) {
        List<MovimientoMaterial> movimientos = recargarOrtopedia(equipoId).getMateriales().stream()
            .map(m -> new MovimientoMaterial(m.getId(), m.getCantidad(), m.getEstado(), destino))
            .toList();
        materialDAO.aplicarMovimientos(equipoId, movimientos);
    }

    private Equipo recargarOrtopedia(int equipoId) {
        return equipoDAO.obtenerPorId(String.valueOf(equipoId));
    }

    // ── siembra: otros ───────────────────────────────────────────────────────

    private int otrosDetalles(String... descripciones) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        for (String descripcion : descripciones) {
            equipo.agregarMaterial(new MaterialOtros(descripcion, 3));
        }
        otrosDAO.guardar(equipo);
        return equipo.getId();
    }

    private int otrosRemito(int cantidad) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.REMITO);
        equipo.setRemitoCantidad(cantidad);
        otrosDAO.guardar(equipo);
        return equipo.getId();
    }

    private void avanzarPrimerMaterialOtros(int equipoId, EstadoEquipo destino) {
        MaterialOtros material = otrosDAO.obtenerPorId(equipoId).getMateriales().get(0);
        otrosDAO.aplicarMovimientos(equipoId, List.of(new MovimientoMaterial(
            material.getId(), material.getCantidad(), material.getEstado(), destino)));
    }

    /** Un movimiento sobre un REMITO todavía sin filas viaja con {@code materialId = 0}. */
    private void avanzarRemito(int equipoId, int cantidad, EstadoEquipo destino) {
        EquipoOtros equipo = otrosDAO.obtenerPorId(equipoId);
        otrosDAO.aplicarMovimientos(equipoId, List.of(new MovimientoMaterial(
            0, cantidad, equipo.getEstado(), destino)));
    }

    // ── lectura de apoyo ─────────────────────────────────────────────────────

    private static EstadoEquipo estadoDe(List<Equipo> todos, int equipoId) {
        return todos.stream()
            .filter(e -> e.getId() == equipoId)
            .findFirst().orElseThrow()
            .getEstado();
    }

    private static EstadoEquipo estadoDeOtros(List<EquipoOtros> todos, int equipoId) {
        return todos.stream()
            .filter(e -> e.getId() == equipoId)
            .findFirst().orElseThrow()
            .getEstado();
    }

    private static String estadosDeMateriales(Equipo equipo) {
        return equipo.getMateriales().stream()
            .map(m -> m.getEstado() + "×" + m.getCantidad())
            .toList().toString();
    }

    private static String estadosDeMaterialesOtros(EquipoOtros equipo) {
        return equipo.getMateriales().stream()
            .map(m -> m.getEstado() + "×" + m.getCantidad())
            .toList().toString();
    }
}
