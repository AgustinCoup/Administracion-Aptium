package com.example.features.equipos.ortopedias.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.model.FilaAEntregar;
import com.example.features.equipos.ortopedias.model.*;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialDAOTest extends AbstractDAOTest {

    private final MaterialDAO dao     = new MaterialDAO();
    private final EquipoDAO  equipoDAO = new EquipoDAO();

    private Equipo equipo;
    private int    materialId;

    @BeforeEach
    void crearEquipoConMaterial() {
        equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroInstitucion(1);
        equipo.agregarMaterial(new Material(400, "Tornillera", 3));
        equipoDAO.guardarEquipo(equipo);
        equipo = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        materialId = equipo.getMateriales().get(0).getId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM material_movimientos");
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
    }

    // ── obtenerCantidad ───────────────────────────────────────────────────────

    @Test
    void obtenerCantidad_materialExistente_retornaCantidad() {
        assertEquals(3, dao.obtenerCantidad(materialId));
    }

    @Test
    void obtenerCantidad_materialInexistente_retornaNull() {
        assertNull(dao.obtenerCantidad(999999));
    }

    // ── obtenerMaterial ───────────────────────────────────────────────────────

    @Test
    void obtenerMaterial_existente_retornaFilaConDatos() {
        FilaMaterial datos = dao.obtenerMaterial(materialId);
        assertNotNull(datos);
        assertEquals((int) materialId,     datos.id());
        assertEquals((int) equipo.getId(), datos.equipoId());
        assertEquals(400,                  datos.codigo());
        assertEquals("TORNILLERA",         datos.descripcion());  // join con catalogo
        assertEquals(3,                    datos.cantidad());
    }

    @Test
    void obtenerMaterial_inexistente_retornaNull() {
        assertNull(dao.obtenerMaterial(999999));
    }

    // ── actualizarCantidad ────────────────────────────────────────────────────

    @Test
    void actualizarCantidad_existente_retornaTrue() {
        assertTrue(dao.actualizarCantidad(equipo.getId(), materialId, 5, 0));
        assertEquals(5, dao.obtenerCantidad(materialId));
    }

    @Test
    void actualizarCantidad_inexistente_retornaFalse() {
        assertFalse(dao.actualizarCantidad(equipo.getId(), 999999, 5, 0));
    }

    // ── actualizarCodigo ──────────────────────────────────────────────────────

    @Test
    void actualizarCodigo_existente_retornaTrue() {
        assertTrue(dao.actualizarCodigo(equipo.getId(), materialId, 401, 0));
        assertEquals(401, dao.obtenerMaterial(materialId).codigo());
    }

    @Test
    void actualizarCodigo_inexistente_retornaFalse() {
        assertFalse(dao.actualizarCodigo(equipo.getId(), 999999, 400, 0));
    }

    // ── agregarMaterial ───────────────────────────────────────────────────────

    @Test
    void agregarMaterial_nuevo_retornaIdPositivo() {
        Integer nuevoId = dao.agregarMaterial(equipo.getId(), 400, 2, 0);
        assertNotNull(nuevoId);
        assertTrue(nuevoId > 0);
    }

    @Test
    void agregarMaterial_insertaMovimiento() {
        dao.agregarMaterial(equipo.getId(), 400, 2, 0);
        // El equipo ahora debería tener 2 filas para el código 400 (el original + el nuevo)
        List<FilaMaterial> materiales = dao.obtenerMaterialesPorCodigo(equipo.getId(), 400);
        assertTrue(materiales.size() >= 2);
    }

    // ── obtenerMaterialesPorCodigo ────────────────────────────────────────────

    @Test
    void obtenerMaterialesPorCodigo_existente_retornaLista() {
        List<FilaMaterial> materiales = dao.obtenerMaterialesPorCodigo(equipo.getId(), 400);
        assertEquals(1, materiales.size());
        assertEquals(400, materiales.get(0).codigo());
    }

    @Test
    void obtenerMaterialesPorCodigo_sinCoincidencias_retornaVacio() {
        assertTrue(dao.obtenerMaterialesPorCodigo(equipo.getId(), 9999).isEmpty());
    }

    // ── actualizarEstadoMaterial ──────────────────────────────────────────────

    @Test
    void actualizarEstadoMaterial_existente_retornaTrue() {
        assertTrue(dao.actualizarEstadoMaterial(equipo.getId(), 400, EstadoEquipo.LAVANDO));

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado());
    }

    @Test
    void actualizarEstadoMaterial_equipoInexistente_lanzaDatabaseException() {
        assertThrows(DatabaseException.class,
            () -> dao.actualizarEstadoMaterial(999999, 400, EstadoEquipo.LAVANDO));
    }

    // ── actualizarMultiplesMateriales ─────────────────────────────────────────

    @Test
    void actualizarMultiplesMateriales_mapaVacio_retornaTrue() {
        assertTrue(dao.actualizarMultiplesMateriales(equipo.getId(), Map.of()));
    }

    @Test
    void actualizarMultiplesMateriales_conMaterial_actualizaEstado() {
        assertTrue(dao.actualizarMultiplesMateriales(
            equipo.getId(), Map.of(materialId, EstadoEquipo.LAVANDO)));

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado());
    }

    // ── aplicarMovimientos ────────────────────────────────────────────────────

    @Test
    void aplicarMovimientos_listaVacia_retornaTrue() {
        assertTrue(dao.aplicarMovimientos(equipo.getId(), List.of()));
    }

    @Test
    void aplicarMovimientos_estadoDestinoExplicito_actualizaEstado() {
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO));
        dao.aplicarMovimientos(equipo.getId(), movs);

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado());
    }

    @Test
    void aplicarMovimientos_estadoDestinoNulo_lanzaExcepcion() {
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, null));
        assertThrows(DatabaseException.class,
            () -> dao.aplicarMovimientos(equipo.getId(), movs));
    }

    @Test
    void aplicarMovimientos_cantidadParcial_splitaMaterial() {
        // Mueve 1 de 3 → original queda con 2, nuevo material con 1
        List<MovimientoMaterial> movs = List.of(
            new MovimientoMaterial(materialId, 1, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO));
        dao.aplicarMovimientos(equipo.getId(), movs);

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(2, cargado.getMateriales().size());
        int total = cargado.getMateriales().stream().mapToInt(Material::getCantidad).sum();
        assertEquals(3, total);
    }

    /**
     * Guarda de concurrencia con dos conexiones reales: A leyó el material en NUEVO, B lo avanzó a
     * LAVANDO y commiteó, y recién entonces A intenta aplicar su movimiento contra el estado viejo.
     * Tiene que fallar con {@link ConflictoConcurrenciaException} y dejar intacto el cambio de B.
     */
    @Test
    void aplicarMovimientos_estadoCambiadoPorOtraConexion_lanzaConflictoYNoPisaElCambio() throws SQLException {
        // B: otra conexión avanza el material y commitea
        try (Connection otra = ConnectionPool.getConnection()) {
            otra.setAutoCommit(false);
            try (PreparedStatement ps = otra.prepareStatement(
                    "UPDATE equipo_materiales SET estado = 'Lavando' WHERE id = ?")) {
                ps.setInt(1, materialId);
                ps.executeUpdate();
            }
            otra.commit();
        }

        // A: aplica con el snapshot viejo (esperaba NUEVO)
        List<MovimientoMaterial> movsA = List.of(
            new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO));
        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.aplicarMovimientos(equipo.getId(), movsA));

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, cargado.getMateriales().get(0).getEstado(),
            "el cambio de B queda intacto: la transacción de A se revirtió entera");
        assertEquals(3, cargado.getMateriales().get(0).getCantidad(),
            "A no llegó a splitear ni mover cantidades");
    }

    // ── aplicarMovimientos con N materiales (avance múltiple de Registrar Estado) ──
    //
    // El avance múltiple no tiene ruta de escritura propia: Confirmar manda la lista de movimientos
    // de cada equipo a este mismo método. Estos dos casos fijan que ya cubre N movimientos en una
    // sola transacción, y que un choque en uno revierte los N.

    @Test
    void aplicarMovimientos_tresMaterialesDelMismoEquipo_escribeTresMovimientosEnUnaTransaccion() {
        Equipo tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsDeMateriales(tres);
        int movimientosAntes = contarMovimientos(tres.getId());

        assertTrue(dao.aplicarMovimientos(tres.getId(), List.of(
            new MovimientoMaterial(ids.get(0), 2, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO),
            new MovimientoMaterial(ids.get(1), 1, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO),
            new MovimientoMaterial(ids.get(2), 5, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO))));

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(tres.getId()));
        assertTrue(cargado.getMateriales().stream().allMatch(m -> m.getEstado() == EstadoEquipo.LAVANDO),
            "los tres pasaron");
        assertEquals(3, cargado.getMateriales().size(), "completos: ni splits ni filas nuevas");
        assertEquals(movimientosAntes + 3, contarMovimientos(tres.getId()), "un movimiento por material");
    }

    @Test
    void aplicarMovimientos_variosMateriales_unoEnConflicto_noEscribeNinguno() {
        Equipo tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsDeMateriales(tres);
        int movimientosAntes = contarMovimientos(tres.getId());

        // El segundo lleva un estadoOrigenEsperado viejo: la pantalla lo vio en LAVANDO, la base
        // dice NUEVO. El primero, que va antes y sí matchea, tiene que revertirse igual.
        assertThrows(ConflictoConcurrenciaException.class, () -> dao.aplicarMovimientos(tres.getId(), List.of(
            new MovimientoMaterial(ids.get(0), 2, EstadoEquipo.NUEVO,   EstadoEquipo.LAVANDO),
            new MovimientoMaterial(ids.get(1), 1, EstadoEquipo.LAVANDO, EstadoEquipo.LAVADO),
            new MovimientoMaterial(ids.get(2), 5, EstadoEquipo.NUEVO,   EstadoEquipo.LAVANDO))));

        Equipo cargado = equipoDAO.obtenerPorId(String.valueOf(tres.getId()));
        assertTrue(cargado.getMateriales().stream().allMatch(m -> m.getEstado() == EstadoEquipo.NUEVO),
            "los tres siguen en su estado original");
        assertEquals(3, cargado.getMateriales().size(), "sin filas nuevas");
        assertEquals(movimientosAntes, contarMovimientos(tres.getId()), "ningún movimiento registrado");
    }

    // ── eliminarMaterialesPorCodigo ───────────────────────────────────────────

    @Test
    void eliminarMaterialesPorCodigo_existente_retornaTrue() {
        assertTrue(dao.eliminarMaterialesPorCodigo(equipo.getId(), 400, 0));
        assertTrue(dao.obtenerMaterialesPorCodigo(equipo.getId(), 400).isEmpty());
    }

    @Test
    void eliminarMaterialesPorCodigo_codigoNoExistente_lanzaConflicto() {
        // La guarda matcheó (version 0 es la correcta) pero no hay filas con ese código: es
        // contradictorio, no un "no había nada que borrar" silencioso.
        assertThrows(ConflictoConcurrenciaException.class,
            () -> dao.eliminarMaterialesPorCodigo(equipo.getId(), 9999, 0));
    }

    // ── entregarMateriales ────────────────────────────────────────────────────

    @Test
    void entregarMateriales_filaEsterilizada_quedaEntregadaConSuMovimiento() throws SQLException {
        esterilizar(materialId);
        int movimientosAntes = contarMovimientos(equipo.getId());

        dao.entregarMateriales(List.of(new FilaAEntregar(equipo.getId(), materialId, 3)));

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(), estadoDe(materialId));
        assertEquals(movimientosAntes + 1, contarMovimientos(equipo.getId()));
        assertEquals("3|Esterilizado|Entregado", ultimoMovimiento(materialId));
    }

    /** El bug que motiva la ruta: la escritura vieja entregaba todo lo esterilizado del destino. */
    @Test
    void entregarMateriales_noTocaUnMaterialEsterilizadoQueNoEstabaEnLaSolicitud() throws SQLException {
        Equipo tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsDeMateriales(tres);
        esterilizar(ids.get(0));
        esterilizar(ids.get(1));

        dao.entregarMateriales(List.of(new FilaAEntregar(tres.getId(), ids.get(0), 2)));

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(),    estadoDe(ids.get(0)));
        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(ids.get(1)),
            "no estaba en la solicitud: no se entrega aunque esté esterilizado");
    }

    @Test
    void entregarMateriales_filaYaEntregada_conflictoYNoEscribeNada() throws SQLException {
        ejecutarSQL("UPDATE equipo_materiales SET estado = 'Entregado' WHERE id = " + materialId);
        int movimientosAntes = contarMovimientos(equipo.getId());

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregarMateriales(
            List.of(new FilaAEntregar(equipo.getId(), materialId, 3))));

        assertEquals(movimientosAntes, contarMovimientos(equipo.getId()), "sin movimiento duplicado");
    }

    /** Defensivo: hoy ninguna ruta de la UI cambia la cantidad de una fila esterilizada. */
    @Test
    void entregarMateriales_cantidadDistintaALaVista_conflicto() throws SQLException {
        esterilizar(materialId);
        ejecutarSQL("UPDATE equipo_materiales SET cantidad = 2 WHERE id = " + materialId);

        assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregarMateriales(
            List.of(new FilaAEntregar(equipo.getId(), materialId, 3))));

        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(materialId));
    }

    @Test
    void entregarMateriales_segundaFilaEnConflicto_revierteLaPrimera() throws SQLException {
        Equipo tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsDeMateriales(tres);
        esterilizar(ids.get(0));   // la primera en el orden de escritura: matchea y se escribe
        int movimientosAntes = contarMovimientos(tres.getId());

        // La segunda sigue en NUEVO: la pantalla la vio esterilizada, la base dice otra cosa.
        assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregarMateriales(List.of(
            new FilaAEntregar(tres.getId(), ids.get(1), 1),
            new FilaAEntregar(tres.getId(), ids.get(0), 2))));

        assertEquals(EstadoEquipo.ESTERILIZADO.getNombre(), estadoDe(ids.get(0)),
            "la primera se revirtió con la transacción entera");
        assertEquals(movimientosAntes, contarMovimientos(tres.getId()), "ningún movimiento registrado");
    }

    @Test
    void entregarMateriales_equipoIncompleto_entregaSoloLaFilaPedidaYElEquipoQuedaEnProceso()
            throws SQLException {
        Equipo tres = equipoConTresMaterialesEnNuevo();
        List<Integer> ids = idsDeMateriales(tres);
        esterilizar(ids.get(0));

        dao.entregarMateriales(List.of(new FilaAEntregar(tres.getId(), ids.get(0), 2)));

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(), estadoDe(ids.get(0)));
        assertEquals(EstadoEquipo.NUEVO.getNombre(), estadoDe(ids.get(1)));
        assertEquals(EstadoEquipo.NUEVO.getNombre(),
            texto("SELECT estado FROM equipos WHERE id = " + tres.getId()),
            "el equipo sigue en proceso: su material más atrasado está en NUEVO");
    }

    @Test
    void entregarMateriales_recalculaElEstadoYBumpeaLaVersionDelEquipo() throws SQLException {
        esterilizar(materialId);
        ejecutarSQL("UPDATE equipos SET estado = 'Esterilizado' WHERE id = " + equipo.getId());
        int versionAntes = versionDeEquipo(equipo.getId());

        dao.entregarMateriales(List.of(new FilaAEntregar(equipo.getId(), materialId, 3)));

        assertEquals(EstadoEquipo.ENTREGADO.getNombre(),
            texto("SELECT estado FROM equipos WHERE id = " + equipo.getId()));
        assertEquals(versionAntes + 1, versionDeEquipo(equipo.getId()));
    }

    @Test
    void entregarMateriales_contencionDeLock_saleComoConflicto() throws SQLException {
        esterilizar(materialId);
        try (Connection otro = ConnectionPool.getConnection()) {
            otro.setAutoCommit(false);
            try (PreparedStatement ps = otro.prepareStatement(
                    "SELECT id FROM equipo_materiales WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, materialId);
                ps.executeQuery().close();
            }

            // Otra entrega tiene la fila tomada y la base corta la espera: es "otro se te
            // adelantó", no un error técnico.
            assertThrows(ConflictoConcurrenciaException.class, () -> dao.entregarMateriales(
                List.of(new FilaAEntregar(equipo.getId(), materialId, 3))));

            otro.rollback();
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private void esterilizar(int id) throws SQLException {
        ejecutarSQL("UPDATE equipo_materiales SET estado = 'Esterilizado' WHERE id = " + id);
    }

    private String estadoDe(int id) {
        return texto("SELECT estado FROM equipo_materiales WHERE id = " + id);
    }

    private int versionDeEquipo(int id) {
        return Integer.parseInt(texto("SELECT version FROM equipos WHERE id = " + id));
    }

    /** {@code cantidad|origen|destino} del último movimiento del material. */
    private String ultimoMovimiento(int id) {
        return texto("SELECT CONCAT(cantidad, '|', estado_origen, '|', estado_destino) "
            + "FROM material_movimientos WHERE material_id = " + id + " ORDER BY id DESC LIMIT 1");
    }

    private String texto(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Códigos distintos: si compartieran código, la unificación de la base mezclaría las filas. */
    private Equipo equipoConTresMaterialesEnNuevo() {
        Equipo nuevo = new Equipo();
        nuevo.setNroCliente(1);
        nuevo.setNroInstitucion(1);
        nuevo.agregarMaterial(new Material(400, "Tornillera", 2));
        nuevo.agregarMaterial(new Material(401, "Placa", 1));
        nuevo.agregarMaterial(new Material(402, "Tornillo", 5));
        equipoDAO.guardarEquipo(nuevo);
        return equipoDAO.obtenerPorId(String.valueOf(nuevo.getId()));
    }

    /** Ids en el orden de los códigos 400, 401, 402, que es el de las cantidades de la fixture. */
    private static List<Integer> idsDeMateriales(Equipo equipo) {
        return equipo.getMateriales().stream()
            .sorted(java.util.Comparator.comparingInt(Material::getCodigo))
            .map(Material::getId)
            .toList();
    }

    private int contarMovimientos(int equipoId) {
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM material_movimientos WHERE equipo_id = ?")) {
            ps.setInt(1, equipoId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
