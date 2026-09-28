package com.example.features.equipos.otros.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.model.FilaAEntregar;
import com.example.common.model.RemitoAEntregar;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EquipoOtrosDAOTest extends AbstractDAOTest {

    private final EquipoOtrosDAO dao = new EquipoOtrosDAO(new CatalogoOtrosDAO());

    // Fixture de DETALLES inicializado en @BeforeEach
    private EquipoOtros equipoDetalles;
    private int         materialId;

    @BeforeEach
    void crearEquipoDetallesConMaterial() {
        equipoDetalles = new EquipoOtros();
        equipoDetalles.setNroCliente(1);
        equipoDetalles.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipoDetalles.agregarMaterial(new MaterialOtros("TestDescMat Principal", 3));
        dao.guardar(equipoDetalles);
        // reload para obtener el ID del material
        equipoDetalles = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        materialId = equipoDetalles.getMateriales().get(0).getId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestDesc%' OR descripcion = 'Elementos'");
    }

    // ── guardar — DETALLES ────────────────────────────────────────────────────

    @Test
    void guardar_tipoDetalles_asignaIdPositivo() {
        assertTrue(equipoDetalles.getId() > 0);
    }

    @Test
    void guardar_tipoDetalles_materialPersistidoConId() {
        MaterialOtros mat = equipoDetalles.getMateriales().get(0);
        assertNotNull(mat.getId());
        assertTrue(mat.getId() > 0);
        assertEquals("TestDescMat Principal", mat.getDescripcion());
        assertEquals(3, mat.getCantidad());
    }

    @Test
    void guardar_tipoDetalles_noGeneraRemitoId() {
        assertNull(equipoDetalles.getRemitoId());
    }

    /**
     * El guardado completo (wrapper público, sin {@code TransactionalConnection}) tiene que
     * revertirse entero cuando {@code CatalogoOtrosDAO.obtenerOCrear} rechaza una descripción
     * dada de baja: ni el equipo ni el material pueden quedar escritos a medias.
     *
     * <p>{@code guardar(EquipoOtros)} sólo atrapa {@code SQLException}, así que el
     * {@code BusinessException} de {@code obtenerOCrear} se propaga sin pasar por su
     * {@code rollback(conn, e)} explícito. Lo que sostiene la atomicidad acá es que HikariCP
     * revierte la transacción abierta (autoCommit=false, sin commit) al devolver la conexión al
     * pool en el {@code close()} del bloque {@code finally}. Este test fija ese comportamiento.</p>
     */
    @Test
    void guardar_conDescripcionDeBaja_propagaYNoDejaNadaEscrito() throws SQLException {
        ejecutarSQL("INSERT INTO catalogo_otros (descripcion, activo) VALUES ('TestDescMat DeBaja', FALSE)");
        long equiposAntes = contarFilas("equipo_otros");
        long materialesAntes = contarFilas("equipo_otros_materiales");

        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros("TestDescMat DeBaja", 2));

        assertThrows(RuntimeException.class, () -> dao.guardar(equipo));

        assertEquals(equiposAntes, contarFilas("equipo_otros"));
        assertEquals(materialesAntes, contarFilas("equipo_otros_materiales"));
    }

    private long contarFilas(String tabla) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tabla);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * El {@code ORDER BY} de los listados dejó de llevar la clave de material —forzaba un filesort
     * del join entero y anulaba los índices de la V23—, así que el orden de los materiales dentro
     * de cada equipo pasó a resolverse en memoria, igual que en {@code EquipoDAO}. Sin eso quedaban
     * en el orden que devolviera el motor, que no está garantizado: la grilla de materiales de las
     * dos pantallas del CDE los muestra, así que se reacomodarían solos entre refrescos.
     *
     * <p>Fija el contrato del DAO, no el del motor: H2 puede devolverlos ya ordenados por su cuenta
     * y aun así la garantía tiene que ser del DAO, que es lo único igual en H2 y en MySQL.
     */
    @Test
    void obtenerTodos_devuelveLosMaterialesOrdenadosPorId() {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros("TestDescOrden A", 1));
        equipo.agregarMaterial(new MaterialOtros("TestDescOrden B", 1));
        equipo.agregarMaterial(new MaterialOtros("TestDescOrden C", 1));
        equipo.agregarMaterial(new MaterialOtros("TestDescOrden D", 1));
        dao.guardar(equipo);

        List<Integer> ids = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipo.getId()))
            .findFirst().orElseThrow()
            .getMateriales().stream().map(MaterialOtros::getId).toList();

        assertEquals(4, ids.size());
        assertEquals(ids.stream().sorted().toList(), ids, "los materiales salen ordenados por id");
    }

    // ── guardar — REMITO ──────────────────────────────────────────────────────

    @Test
    void guardar_tipoRemito_generaRemitoId() {
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        String esperado = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        assertNotNull(remito.getRemitoId());
        assertTrue(remito.getRemitoId().startsWith(esperado),
            "remitoId debe empezar con " + esperado + " pero fue " + remito.getRemitoId());
    }

    @Test
    void guardar_tipoRemito_noInsertaMaterialesEnTabla() {
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(remito.getId()))
            .findFirst().orElseThrow();
        assertTrue(recargado.getMateriales().isEmpty());
    }

    // ── obtenerTodos ──────────────────────────────────────────────────────────

    @Test
    void obtenerTodos_conEquipo_retornaLista() {
        List<EquipoOtros> lista = dao.obtenerTodos();
        assertEquals(1, lista.size());
        assertEquals(equipoDetalles.getId(), lista.get(0).getId());
    }

    @Test
    void obtenerTodos_ordenaPorFechaIngresoDescendente_masAllaDelOrdenDeInsercion() throws SQLException {
        // equipoDetalles (id menor) ya existe por @BeforeEach; se agrega uno con id mayor.
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        // El de id menor tiene fecha_ingreso más reciente: debe listarse primero.
        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2030-01-01 00:00:00' WHERE id = " + equipoDetalles.getId());
        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2020-01-01 00:00:00' WHERE id = " + remito.getId());

        List<EquipoOtros> todos = dao.obtenerTodos();
        assertEquals(equipoDetalles.getId(), todos.get(0).getId());
        assertEquals(remito.getId(), todos.get(1).getId());
    }

    // ── aplicarMovimientos ────────────────────────────────────────────────────

    @Test
    void aplicarMovimientos_listaVacia_retornaTrue() {
        assertTrue(dao.aplicarMovimientos(equipoDetalles.getId(), List.of()));
    }

    @Test
    void aplicarMovimientos_estadoDestinoExplicito_actualizaEstado() {
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO));
        assertTrue(dao.aplicarMovimientos(equipoDetalles.getId(), movs));

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(EstadoEquipo.LAVANDO, recargado.getMateriales().get(0).getEstado());
    }

    @Test
    void aplicarMovimientos_estadoDestinoNulo_calculaSiguienteEstado() {
        // requiereLavado=true (default) → NUEVO → siguiente = LAVANDO
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, null));
        assertTrue(dao.aplicarMovimientos(equipoDetalles.getId(), movs));

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(EstadoEquipo.LAVANDO, recargado.getMateriales().get(0).getEstado());
    }

    @Test
    void aplicarMovimientos_cantidadParcial_splitaMaterial() {
        // Mueve 1 de 3 → 2 filas: original con 2 + nuevo con 1
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 1, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO));
        dao.aplicarMovimientos(equipoDetalles.getId(), movs);

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(2, recargado.getMateriales().size());
        int total = recargado.getMateriales().stream().mapToInt(MaterialOtros::getCantidad).sum();
        assertEquals(3, total);
    }

    /**
     * Espejo del test de ortopedias: A leyó el material en NUEVO, otra conexión lo avanzó a
     * LAVANDO y commiteó, y recién entonces A intenta. Tiene que fallar con
     * {@link ConflictoConcurrenciaException} y dejar intacto el cambio de la otra conexión.
     */
    @Test
    void aplicarMovimientos_estadoCambiadoPorOtraConexion_lanzaConflictoYNoPisaElCambio() throws SQLException {
        try (Connection otra = ConnectionPool.getConnection()) {
            otra.setAutoCommit(false);
            try (PreparedStatement ps = otra.prepareStatement(
                    "UPDATE equipo_otros_materiales SET estado = 'Lavando' WHERE id = ?")) {
                ps.setInt(1, materialId);
                ps.executeUpdate();
            }
            otra.commit();
        }

        List<MovimientoMaterial> movsA = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO));
        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.aplicarMovimientos(equipoDetalles.getId(), movsA));

        EquipoOtros recargado = dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoDetalles.getId()))
            .findFirst().orElseThrow();
        assertEquals(EstadoEquipo.LAVANDO, recargado.getMateriales().get(0).getEstado(),
            "el cambio de la otra conexión queda intacto: la transacción de A se revirtió entera");
        assertEquals(3, recargado.getMateriales().get(0).getCantidad());
    }

    // Espejo de MaterialDAOTest: el avance múltiple de Registrar Estado usa este mismo método, y
    // estos dos casos fijan que cubre N movimientos en una transacción y que un choque revierte los N.

    @Test
    void aplicarMovimientos_tresMaterialesDelMismoEquipo_escribeTresMovimientosEnUnaTransaccion() {
        EquipoOtros tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsPorDescripcion(tres);
        int movimientosAntes = contarMovimientos(tres.getId());

        assertTrue(dao.aplicarMovimientos(tres.getId(), List.of(
            new MovimientoMaterial(ids.get(0), 2, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO),
            new MovimientoMaterial(ids.get(1), 1, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO),
            new MovimientoMaterial(ids.get(2), 5, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO))));

        EquipoOtros recargado = recargar(tres.getId());
        assertTrue(recargado.getMateriales().stream().allMatch(m -> m.getEstado() == EstadoEquipo.LAVANDO),
            "los tres pasaron");
        assertEquals(3, recargado.getMateriales().size(), "completos: ni splits ni filas nuevas");
        assertEquals(movimientosAntes + 3, contarMovimientos(tres.getId()), "un movimiento por material");
    }

    @Test
    void aplicarMovimientos_variosMateriales_unoEnConflicto_noEscribeNinguno() {
        EquipoOtros tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsPorDescripcion(tres);
        int movimientosAntes = contarMovimientos(tres.getId());

        // El segundo lleva un estadoOrigenEsperado viejo; el primero sí matchea y se revierte igual.
        assertThrows(ConflictoConcurrenciaException.class, () -> dao.aplicarMovimientos(tres.getId(), List.of(
            new MovimientoMaterial(ids.get(0), 2, EstadoEquipo.NUEVO,   EstadoEquipo.LAVANDO),
            new MovimientoMaterial(ids.get(1), 1, EstadoEquipo.LAVANDO, EstadoEquipo.LAVADO),
            new MovimientoMaterial(ids.get(2), 5, EstadoEquipo.NUEVO,   EstadoEquipo.LAVANDO))));

        EquipoOtros recargado = recargar(tres.getId());
        assertTrue(recargado.getMateriales().stream().allMatch(m -> m.getEstado() == EstadoEquipo.NUEVO),
            "los tres siguen en su estado original");
        assertEquals(3, recargado.getMateriales().size(), "sin filas nuevas");
        assertEquals(movimientosAntes, contarMovimientos(tres.getId()), "ningún movimiento registrado");
    }

    @Test
    void aplicarMovimientos_snapshotDeEstadoDesactualizado_lanzaConflicto() throws SQLException {
        // A cree que el material sigue en NUEVO, pero ya está en LAVANDO.
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Lavando' WHERE id = " + materialId);

        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVADO));
        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.aplicarMovimientos(equipoDetalles.getId(), movs));
    }

    // ── entregar ──────────────────────────────────────────────────────────────

    @Test
    void entregar_filaEsterilizada_quedaEntregadaConSuMovimiento() throws SQLException {
        esterilizar(materialId);
        int movimientosAntes = contarMovimientos(equipoDetalles.getId());

        dao.entregar(List.of(new FilaAEntregar(equipoDetalles.getId(), materialId, 3)), List.of());

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(), estadoDe(materialId));
        assertEquals(movimientosAntes + 1, contarMovimientos(equipoDetalles.getId()));
        assertEquals("3|Esterilizado|Entregado", ultimoMovimiento(materialId));
    }

    /** El bug que motiva la ruta: la escritura vieja entregaba todo lo esterilizado del cliente. */
    @Test
    void entregar_noTocaUnMaterialEsterilizadoQueNoEstabaEnLaSolicitud() throws SQLException {
        EquipoOtros tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsPorDescripcion(tres);
        esterilizar(ids.get(0));
        esterilizar(ids.get(1));

        dao.entregar(List.of(new FilaAEntregar(tres.getId(), ids.get(0), 2)), List.of());

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(),    estadoDe(ids.get(0)));
        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(ids.get(1)),
            "no estaba en la solicitud: no se entrega aunque esté esterilizado");
    }

    @Test
    void entregar_filaYaEntregada_conflictoYNoEscribeNada() throws SQLException {
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Entregado' WHERE id = " + materialId);
        int movimientosAntes = contarMovimientos(equipoDetalles.getId());

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregar(
            List.of(new FilaAEntregar(equipoDetalles.getId(), materialId, 3)), List.of()));

        assertEquals(movimientosAntes, contarMovimientos(equipoDetalles.getId()), "sin movimiento duplicado");
    }

    /** Defensivo: hoy ninguna ruta de la UI cambia la cantidad de una fila esterilizada. */
    @Test
    void entregar_cantidadDistintaALaVista_conflicto() throws SQLException {
        esterilizar(materialId);
        ejecutarSQL("UPDATE equipo_otros_materiales SET cantidad = 2 WHERE id = " + materialId);

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregar(
            List.of(new FilaAEntregar(equipoDetalles.getId(), materialId, 3)), List.of()));

        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(materialId));
    }

    @Test
    void entregar_segundaFilaEnConflicto_revierteLaPrimera() throws SQLException {
        EquipoOtros tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsPorDescripcion(tres);
        esterilizar(ids.get(0));
        int movimientosAntes = contarMovimientos(tres.getId());

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregar(List.of(
            new FilaAEntregar(tres.getId(), ids.get(1), 1),
            new FilaAEntregar(tres.getId(), ids.get(0), 2)), List.of()));

        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(ids.get(0)),
            "la primera se revirtió con la transacción entera");
        assertEquals(movimientosAntes, contarMovimientos(tres.getId()), "ningún movimiento registrado");
    }

    @Test
    void entregar_equipoIncompleto_entregaSoloLaFilaPedidaYElEquipoQuedaEnProceso() throws SQLException {
        EquipoOtros tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsPorDescripcion(tres);
        esterilizar(ids.get(0));

        dao.entregar(List.of(new FilaAEntregar(tres.getId(), ids.get(0), 2)), List.of());

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(), estadoDe(ids.get(0)));
        assertEquals(EstadoEquipo.NUEVO.getNombre(), estadoDe(ids.get(1)));
        assertEquals(EstadoEquipo.NUEVO, recargar(tres.getId()).getEstado(),
            "el equipo sigue en proceso: su material más atrasado está en NUEVO");
    }

    @Test
    void entregar_recalculaElEstadoYBumpeaLaVersionDelEquipo() throws SQLException {
        esterilizar(materialId);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Esterilizado' WHERE id = " + equipoDetalles.getId());
        int versionAntes = versionDeEquipoOtros(equipoDetalles.getId());

        dao.entregar(List.of(new FilaAEntregar(equipoDetalles.getId(), materialId, 3)), List.of());

        assertEquals(EstadoEquipo.ENTREGADO, recargar(equipoDetalles.getId()).getEstado());
        assertEquals(versionAntes + 1, versionDeEquipoOtros(equipoDetalles.getId()));
    }

    @Test
    void entregar_contencionDeLock_saleComoConflicto() throws SQLException {
        esterilizar(materialId);
        try (Connection otro = ConnectionPool.getConnection()) {
            otro.setAutoCommit(false);
            try (PreparedStatement ps = otro.prepareStatement(
                    "SELECT id FROM equipo_otros_materiales WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, materialId);
                ps.executeQuery().close();
            }

            assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregar(
                List.of(new FilaAEntregar(equipoDetalles.getId(), materialId, 3)), List.of()));

            otro.rollback();
        }
    }

    @Test
    void entregar_remitoSinFilas_entregaElEncabezadoYBumpea() throws SQLException {
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Esterilizado' WHERE id = " + remito.getId());
        int versionAntes = versionDeEquipoOtros(remito.getId());

        dao.entregar(List.of(), List.of(new RemitoAEntregar(remito.getId())));

        assertEquals(EstadoEquipo.ENTREGADO, recargar(remito.getId()).getEstado());
        assertEquals(versionAntes + 1, versionDeEquipoOtros(remito.getId()),
            "el camino REMITO escribe el estado fuera del recálculo: el bump va a mano");
    }

    /** Hoy es un "skip" en la ruta vieja; acá la entrega es de lo que se vio, así que es conflicto. */
    @Test
    void entregar_remitoSinFilasYaEntregado_conflicto() throws SQLException {
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Entregado' WHERE id = " + remito.getId());
        int versionAntes = versionDeEquipoOtros(remito.getId());

        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.entregar(List.of(), List.of(new RemitoAEntregar(remito.getId()))));

        assertEquals(versionAntes, versionDeEquipoOtros(remito.getId()));
    }

    @Test
    void entregar_remitoConFilas_seEntreganSusFilas() throws SQLException {
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);
        int filaId = dao.insertarMaterial(remito.getId(), "Elementos", 3, 0);
        esterilizar(filaId);

        dao.entregar(List.of(new FilaAEntregar(remito.getId(), filaId, 3)), List.of());

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(), estadoDe(filaId));
        assertEquals(EstadoEquipo.ENTREGADO, recargar(remito.getId()).getEstado());
    }

    /** Un remito que ya tiene filas no se puede entregar como encabezado: sus filas tienen su estado. */
    @Test
    void entregar_remitoConFilasPedidoComoSinFilas_conflicto() throws SQLException {
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);
        int filaId = dao.insertarMaterial(remito.getId(), "Elementos", 3, 0);
        esterilizar(filaId);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Esterilizado' WHERE id = " + remito.getId());

        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.entregar(List.of(), List.of(new RemitoAEntregar(remito.getId()))));

        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(filaId));
    }

    /** El conflicto del remito revierte también las filas del mismo destino, escritas antes. */
    @Test
    void entregar_remitoEnConflicto_revierteLasFilasDelMismoDestino() throws SQLException {
        esterilizar(materialId);
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);   // queda en NUEVO: la pantalla lo vio esterilizado
        int movimientosAntes = contarMovimientos(equipoDetalles.getId());

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregar(
            List.of(new FilaAEntregar(equipoDetalles.getId(), materialId, 3)),
            List.of(new RemitoAEntregar(remito.getId()))));

        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(materialId));
        assertEquals(movimientosAntes, contarMovimientos(equipoDetalles.getId()));
    }

    private void esterilizar(int id) throws SQLException {
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Esterilizado' WHERE id = " + id);
    }

    private String estadoDe(int id) throws SQLException {
        return texto("SELECT estado FROM equipo_otros_materiales WHERE id = " + id);
    }

    /** {@code cantidad|origen|destino} del último movimiento del material. */
    private String ultimoMovimiento(int id) throws SQLException {
        return texto("SELECT CONCAT(cantidad, '|', estado_origen, '|', estado_destino) "
            + "FROM otros_material_movimientos WHERE material_id = " + id + " ORDER BY id DESC LIMIT 1");
    }

    private String texto(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private int versionDeEquipoOtros(int id) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT version FROM equipo_otros WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalStateException("no existe equipo_otros " + id);
    }

    // ── obtenerEquiposNuevos ──────────────────────────────────────────────────

    @Test
    void obtenerEquiposNuevos_conEquipoEnEstadoNuevo_retornaEquipo() {
        List<EquipoOtros> lista = dao.obtenerEquiposNuevos();
        assertEquals(1, lista.size());
        assertEquals(equipoDetalles.getId(), lista.get(0).getId());
    }

    @Test
    void obtenerEquiposNuevos_equipoEnOtroEstado_noApareceEnLista() throws SQLException {
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Lavando' WHERE id = " + equipoDetalles.getId());
        assertTrue(dao.obtenerEquiposNuevos().isEmpty());
    }

    @Test
    void obtenerEquiposNuevos_equipoRemito_incluyeRemitoCantidad() {
        EquipoOtros remito = nuevoRemito(7);
        dao.guardar(remito);

        List<EquipoOtros> lista = dao.obtenerEquiposNuevos();
        EquipoOtros encontrado = lista.stream()
            .filter(e -> e.getId().equals(remito.getId()))
            .findFirst()
            .orElse(null);

        assertNotNull(encontrado);
        assertEquals(7, encontrado.getRemitoCantidad());
    }

    @Test
    void obtenerEquiposNuevos_multipleEquipos_retornaAmbos() {
        EquipoOtros segundo = new EquipoOtros();
        segundo.setNroCliente(2);
        segundo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        segundo.agregarMaterial(new MaterialOtros("TestDesc Segundo", 1));
        dao.guardar(segundo);

        assertEquals(2, dao.obtenerEquiposNuevos().size());
    }

    // ── obtenerPorId ──────────────────────────────────────────────────────────

    @Test
    void obtenerPorId_idExistente_retornaEquipo() {
        EquipoOtros encontrado = dao.obtenerPorId(equipoDetalles.getId());
        assertNotNull(encontrado);
        assertEquals(equipoDetalles.getId(), encontrado.getId());
    }

    @Test
    void obtenerPorId_idInexistente_retornaNull() {
        assertNull(dao.obtenerPorId(9999));
    }

    @Test
    void obtenerPorId_incluyeMateriales() {
        EquipoOtros encontrado = dao.obtenerPorId(equipoDetalles.getId());
        assertNotNull(encontrado);
        assertEquals(1, encontrado.getMateriales().size());
        assertEquals("TestDescMat Principal", encontrado.getMateriales().get(0).getDescripcion());
    }

    /**
     * {@code ultimo_movimiento} pasó de un {@code LEFT JOIN} contra una tabla derivada a una
     * subconsulta correlacionada (Paso 6): tiene que seguir dando exactamente el máximo de
     * {@code otros_material_movimientos.fecha} para el material, sin importar cuántas filas
     * tenga ni en qué orden se insertaron.
     */
    @Test
    void obtenerPorId_ultimoMovimientoEsElMaximoDeFecha() throws SQLException {
        ejecutarSQL("INSERT INTO otros_material_movimientos " +
            "(material_id, equipo_otros_id, cantidad, estado_destino, fecha) VALUES (" +
            materialId + ", " + equipoDetalles.getId() + ", 1, 'Lavando', '2020-01-01 00:00:00')");
        ejecutarSQL("INSERT INTO otros_material_movimientos " +
            "(material_id, equipo_otros_id, cantidad, estado_destino, fecha) VALUES (" +
            materialId + ", " + equipoDetalles.getId() + ", 1, 'Lavado', '2030-06-15 12:00:00')");
        ejecutarSQL("INSERT INTO otros_material_movimientos " +
            "(material_id, equipo_otros_id, cantidad, estado_destino, fecha) VALUES (" +
            materialId + ", " + equipoDetalles.getId() + ", 1, 'Empaquetado', '2025-03-03 00:00:00')");

        EquipoOtros encontrado = dao.obtenerPorId(equipoDetalles.getId());
        assertEquals(java.time.LocalDateTime.of(2030, 6, 15, 12, 0, 0),
            encontrado.getMateriales().get(0).getUltimoMovimiento());
    }

    /**
     * El {@code ORDER BY} de {@link EquipoOtrosDAO#listar} ya no lleva la clave de material
     * (Paso 6, para no forzar un filesort del join entero): el orden ascendente por id lo da el
     * ordenamiento en memoria, no el SQL. Con dos materiales, el segundo insertado (id mayor)
     * tiene que seguir saliendo después del primero.
     */
    @Test
    void obtenerPorId_materialesOrdenadosPorId_sinClaveDeMaterialEnOrderBy() {
        int segundoId = dao.insertarMaterial(equipoDetalles.getId(), "TestDesc Segundo", 1, 0);

        EquipoOtros encontrado = dao.obtenerPorId(equipoDetalles.getId());
        List<Integer> idsMateriales = encontrado.getMateriales().stream()
            .map(MaterialOtros::getId).toList();
        assertEquals(List.of(materialId, segundoId), idsMateriales);
    }

    /** Un fallo de SQL propaga, no devuelve una lista a medias (regla dura del repo). */
    @Test
    void obtenerTodos_fallaDeConexion_propagaDatabaseException() {
        javax.sql.DataSource real = ConnectionPool.getDataSource();
        ConnectionPool.setDataSourceForTesting(dataSourceQueFalla());
        try {
            assertThrows(com.example.common.exception.DatabaseException.class, dao::obtenerTodos);
        } finally {
            ConnectionPool.setDataSourceForTesting(real);
        }
    }

    private static javax.sql.DataSource dataSourceQueFalla() {
        return (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
            javax.sql.DataSource.class.getClassLoader(),
            new Class<?>[]{javax.sql.DataSource.class},
            (proxy, metodo, args) -> {
                if ("getConnection".equals(metodo.getName())) {
                    throw new SQLException("Conexión caída (simulada por el test)");
                }
                return null;
            });
    }

    // ── obtenerActivos ────────────────────────────────────────────────────────
    //
    // Cada caso se compara contra la implementación que obtenerActivos() reemplaza
    // (obtenerTodos() filtrado por calcularEstado()), no contra ids escritos a mano:
    // el WHERE tiene que seguir a calcularEstado(), no parecérsele.

    @Test
    void obtenerActivos_equivaleAFiltrarTodosPorCalcularEstado() throws SQLException {
        // equipoDetalles (DETALLES en NUEVO) ya existe por @BeforeEach → activo.
        entregarPorCompleto(conMaterial("TestDesc Entregado"));           // entregado
        int mixto = conMaterial("TestDesc Mixto");                        // mixto → activo
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Entregado' " +
                    "WHERE equipo_otros_id = " + mixto + " AND descripcion = 'TestDesc Mixto'");
        agregarFila(mixto, "TestDesc Mixto2", "Lavando");

        EquipoOtros remito = nuevoRemito(4);                              // REMITO sin filas → activo
        dao.guardar(remito);

        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_detallesTodoEntregado_seExcluye() throws SQLException {
        entregarPorCompleto(equipoDetalles.getId());

        assertTrue(dao.obtenerActivos().isEmpty());
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_conFilas_ignoraLaColumnaEstadoDelEncabezado() throws SQLException {
        // calcularEstado() de un equipo con filas es el mínimo de las filas; la
        // columna eo.estado puede haber quedado desfasada y no debe decidir.
        entregarPorCompleto(equipoDetalles.getId());
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Nuevo' WHERE id = " + equipoDetalles.getId());

        assertTrue(dao.obtenerActivos().isEmpty(), "las filas mandan: todas entregadas → fuera");
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_sinFilas_mandaLaColumnaEstadoDelEncabezado() throws SQLException {
        // Un REMITO todavía sin filas reales: ahí sí el estado vive en el encabezado.
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Entregado' WHERE id = " + remito.getId());

        assertFalse(ids(dao.obtenerActivos()).contains(remito.getId()));
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_remitoSinFilasNoEntregado_seIncluye() {
        EquipoOtros remito = nuevoRemito(3);
        dao.guardar(remito);

        assertTrue(ids(dao.obtenerActivos()).contains(remito.getId()));
        assertEquals(idsEsperados(), ids(dao.obtenerActivos()));
    }

    @Test
    void obtenerActivos_respetaElMismoOrdenQueObtenerTodos() throws SQLException {
        EquipoOtros remito = nuevoRemito(5);
        dao.guardar(remito);

        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2030-01-01 00:00:00' WHERE id = " + equipoDetalles.getId());
        ejecutarSQL("UPDATE equipo_otros SET fecha_ingreso = '2020-01-01 00:00:00' WHERE id = " + remito.getId());

        assertEquals(List.of(equipoDetalles.getId(), remito.getId()), ids(dao.obtenerActivos()));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** La respuesta correcta, calculada con la implementación que obtenerActivos() reemplaza. */
    private List<Integer> idsEsperados() {
        return dao.obtenerTodos().stream()
            .filter(e -> e.calcularEstado() != EstadoEquipo.ENTREGADO)
            .map(EquipoOtros::getId)
            .toList();
    }

    private static List<Integer> ids(List<EquipoOtros> equipos) {
        return equipos.stream().map(EquipoOtros::getId).toList();
    }

    /** Crea un equipo DETALLES con un único material en NUEVO y devuelve su id. */
    private int conMaterial(String descripcion) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros(descripcion, 1));
        dao.guardar(equipo);
        return equipo.getId();
    }

    private void agregarFila(int equipoId, String descripcion, String estado) throws SQLException {
        int materialId = dao.insertarMaterial(equipoId, descripcion, 1, 0);
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = '" + estado + "' WHERE id = " + materialId);
    }

    private void entregarPorCompleto(int equipoId) throws SQLException {
        ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Entregado' WHERE equipo_otros_id = " + equipoId);
        ejecutarSQL("UPDATE equipo_otros SET estado = 'Entregado' WHERE id = " + equipoId);
    }

    /** Descripciones distintas: si coincidieran, la unificación de la base mezclaría las filas. */
    private EquipoOtros equipoConTresMaterialesEnNuevo() {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros("TestDescMat A", 2));
        equipo.agregarMaterial(new MaterialOtros("TestDescMat B", 1));
        equipo.agregarMaterial(new MaterialOtros("TestDescMat C", 5));
        dao.guardar(equipo);
        return recargar(equipo.getId());
    }

    private EquipoOtros recargar(int equipoId) {
        return dao.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipoId))
            .findFirst().orElseThrow();
    }

    /** Ids en el orden A, B, C, que es el de las cantidades de la fixture. */
    private static List<Integer> idsPorDescripcion(EquipoOtros equipo) {
        return equipo.getMateriales().stream()
            .sorted(java.util.Comparator.comparing(MaterialOtros::getDescripcion))
            .map(MaterialOtros::getId)
            .toList();
    }

    private int contarMovimientos(int equipoId) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM otros_material_movimientos WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private EquipoOtros nuevoRemito(int cantidad) {
        EquipoOtros remito = new EquipoOtros();
        remito.setNroCliente(1);
        remito.setTipoIngreso(TipoIngresoOtros.REMITO);
        remito.setRemitoCantidad(cantidad);
        return remito;
    }

    // ── guardar(Connection, EquipoOtros) ────────────────────────────────────────

    @Test
    void guardarConConnection_rollbackDelLlamador_noDejaFilas() throws SQLException {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros("TestDescMat Rollback", 2));

        int equipoId;
        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            equipoId = dao.guardar(conn, equipo);
            conn.rollback();
        }

        assertTrue(equipoId > 0);
        assertTrue(dao.obtenerTodos().stream().noneMatch(e -> e.getId().equals(equipoId)));
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM equipo_otros_materiales WHERE equipo_otros_id = ?")) {
            ps.setInt(1, equipoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
