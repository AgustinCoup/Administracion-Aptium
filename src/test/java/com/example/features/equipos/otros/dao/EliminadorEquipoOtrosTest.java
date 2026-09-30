package com.example.features.equipos.otros.dao;

import com.example.AbstractDAOTest;
import com.example.common.eliminacion.ArchivoIngresosDAO;
import com.example.common.eliminacion.Bloqueo;
import com.example.common.eliminacion.EliminacionBloqueadaException;
import com.example.common.eliminacion.IngresoArchivado;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TransactionalConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EliminadorEquipoOtrosTest extends AbstractDAOTest {

    private static final String MOTIVO = "Se cargó dos veces";
    private static final String PUESTO = "operador@PC-1";

    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());
    private final LoteDAO loteDAO = new LoteDAO();
    private final EliminadorEquipoOtros eliminador = new EliminadorEquipoOtros(new ArchivoIngresosDAO());

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM ingresos_eliminados");
        ejecutarSQL("DELETE FROM salidas_lavadero");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM instancias_equipo_ciclo");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM equipo_otros");   // CASCADE: materiales, movimientos, volúmenes
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestElim%'");
    }

    // ── resumir ───────────────────────────────────────────────────────────────

    @Test
    void resumir_listaMaterialesConLoteYBloqueoSiHayLoteEnCurso() {
        EquipoOtros equipo = equipoOtros(3);
        Lote lote = lanzarLote(Map.of(equipo, 10));

        ResumenEquipo resumen = eliminador.resumir(equipo.getId());

        assertEquals(ModuloIngreso.OTROS, resumen.ingreso().modulo());
        assertNotNull(resumen.clienteNombre());
        assertNull(resumen.institucionNombre(), "Otros no tiene institución");
        assertEquals(version(equipo.getId()), resumen.version());
        assertEquals(1, resumen.materiales().size());
        assertEquals(lote.getIdNegocio(), resumen.materiales().get(0).loteIdNegocio());
        assertEquals(List.of(new Bloqueo.LoteEnCurso(lote.getIdNegocio())), resumen.bloqueos());
        assertTrue(resumen.ingresosLavaderoOrigen().isEmpty(), "cargado a mano");
    }

    @Test
    void resumir_inexistente_resourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> eliminador.resumir(987654));
    }

    /**
     * Las dos formas de salida derivada: por {@code elemento_ciclo_id} y, la de un equipo repartido
     * en varios lavarropas, por {@code instancia_equipo_id}. El derivado es compartido entre dos
     * ingresos de Lavadero: el resumen nombra a los dos.
     */
    @Test
    void resumir_derivadoDeLavadero_informaElIngresoDeOrigen() {
        int equipoOtrosId = equipoOtros(5).getId();
        int ingresoA = ingresoLavadero();
        derivarPorElementoCiclo(ingresoA, equipoOtrosId);
        int ingresoB = ingresoLavadero();
        derivarPorInstancia(ingresoB, equipoOtrosId);

        ResumenEquipo resumen = eliminador.resumir(equipoOtrosId);

        assertEquals(List.of(ingresoA, ingresoB), resumen.ingresosLavaderoOrigen());
    }

    @Test
    void resumir_remitoSinFilas_unaLineaConRemitoCantidad() {
        int remitoId = remitoSinFilas(12);

        ResumenEquipo resumen = eliminador.resumir(remitoId);

        assertEquals(1, resumen.materiales().size());
        ResumenEquipo.LineaMaterial linea = resumen.materiales().get(0);
        assertEquals(12, linea.cantidad());
        assertTrue(linea.descripcion().contains("29092026-" + remitoId), linea.descripcion());
        assertEquals(EstadoEquipo.NUEVO.getNombre(), linea.estado());
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Test
    void eliminar_entregado_borraYArchivaConMaterialesYMovimientos() {
        EquipoOtros equipo = equipoOtros(3);
        int materialId = equipo.getMateriales().get(0).getId();
        equipoOtrosDAO.aplicarMovimientos(equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));
        ejecutarSinChecked("UPDATE equipo_otros_materiales SET estado = 'Entregado' WHERE equipo_otros_id = " + equipo.getId());
        ejecutarSinChecked("UPDATE equipo_otros SET estado = 'Entregado' WHERE id = " + equipo.getId());
        int movimientosAntes = escalar("SELECT COUNT(*) FROM otros_material_movimientos WHERE equipo_otros_id = " + equipo.getId());
        ResumenEquipo resumen = eliminador.resumir(equipo.getId());

        eliminador.eliminar(equipo.getId(), resumen.version(), MOTIVO, PUESTO);

        assertEquals(0, escalar("SELECT COUNT(*) FROM equipo_otros WHERE id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM equipo_otros_materiales WHERE equipo_otros_id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM otros_material_movimientos WHERE equipo_otros_id = " + equipo.getId()));
        assertEquals("OTROS", texto("SELECT modulo FROM ingresos_eliminados"));
        assertEquals("Entregado", texto("SELECT estado FROM ingresos_eliminados"));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados WHERE archivo_padre_id IS NOT NULL"),
            "borrado desde Ver Equipos: no tiene padre");

        JSONObject snapshot = snapshot();
        assertEquals("OTROS", snapshot.getString("modulo"));
        assertEquals(equipo.getId(), snapshot.getJSONObject("equipo").getInt("id"));
        assertEquals("TestElimOtros", snapshot.getJSONArray("materiales").getJSONObject(0).getString("descripcion"));
        assertEquals(movimientosAntes, snapshot.getJSONArray("movimientos").length());
        assertTrue(snapshot.getJSONArray("salidas_lavadero").isEmpty());
    }

    @Test
    void eliminar_conMaterialEnLoteEnCurso_rechazaYNoBorraNada() {
        EquipoOtros equipo = equipoOtros(3);
        Lote lote = lanzarLote(Map.of(equipo, 10));
        int version = eliminador.resumir(equipo.getId()).version();

        EliminacionBloqueadaException e = assertThrows(EliminacionBloqueadaException.class,
            () -> eliminador.eliminar(equipo.getId(), version, MOTIVO, PUESTO));

        assertEquals(List.of(new Bloqueo.LoteEnCurso(lote.getIdNegocio())), e.getBloqueos());
        assertEquals(1, escalar("SELECT COUNT(*) FROM equipo_otros WHERE id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
    }

    /**
     * Los litros son por lote <em>e</em> ingreso: desaparece sólo la fila del equipo borrado. El otro
     * ingreso del lote conserva los suyos, y {@code capacidad_usada} no se toca.
     */
    @Test
    void eliminar_conLitrosEnLoteCompartido_soloDesapareceSuFilaDeLoteOtrosVolumenes() {
        EquipoOtros borrado = equipoOtros(3);
        EquipoOtros otro = equipoOtros(2);
        Lote lote = lanzarLote(Map.of(borrado, 10, otro, 20));
        assertTrue(loteDAO.finalizarLote(lote.getId()));
        int version = eliminador.resumir(borrado.getId()).version();

        eliminador.eliminar(borrado.getId(), version, MOTIVO, PUESTO);

        assertEquals(0, escalar("SELECT COUNT(*) FROM lote_otros_volumenes WHERE equipo_otros_id = " + borrado.getId()));
        assertEquals(20, escalar("SELECT volumen FROM lote_otros_volumenes WHERE equipo_otros_id = " + otro.getId()));
        assertEquals(45, escalar("SELECT capacidad_usada FROM lotes WHERE id = " + lote.getId()));
        JSONArray volumenes = snapshot().getJSONArray("volumenes_lote");
        assertEquals(1, volumenes.length());
        assertEquals(10, volumenes.getJSONObject(0).getInt("volumen"));
        assertEquals(lote.getIdNegocio(), volumenes.getJSONObject(0).getString("lote_id_negocio"));
    }

    /** Se permite, como en Correcciones: la salida queda con su destino y sin equipo (V17). */
    @Test
    void eliminar_derivadoDeLavadero_laSalidaQuedaConDestinoYEquipoNull() {
        int equipoOtrosId = equipoOtros(5).getId();
        int ingreso = ingresoLavadero();
        int salidaId = derivarPorElementoCiclo(ingreso, equipoOtrosId);
        int version = eliminador.resumir(equipoOtrosId).version();

        eliminador.eliminar(equipoOtrosId, version, MOTIVO, PUESTO);

        assertEquals(1, escalar("SELECT COUNT(*) FROM salidas_lavadero WHERE id = " + salidaId));
        assertEquals("CDE_OTROS", texto("SELECT destino FROM salidas_lavadero WHERE id = " + salidaId));
        assertNull(texto("SELECT equipo_otros_id FROM salidas_lavadero WHERE id = " + salidaId));
        assertEquals(1, escalar("SELECT COUNT(*) FROM ingresos_lavadero WHERE id = " + ingreso),
            "el ingreso de Lavadero no se elimina");
        JSONArray salidas = snapshot().getJSONArray("salidas_lavadero");
        assertEquals(1, salidas.length());
        assertEquals(ingreso, salidas.getJSONObject(0).getInt("ingreso_lavadero_id"));
    }

    @Test
    void eliminar_remitoSinFilas_archivaRemitoCantidad() {
        int remitoId = remitoSinFilas(12);
        int version = eliminador.resumir(remitoId).version();

        eliminador.eliminar(remitoId, version, MOTIVO, PUESTO);

        JSONObject cabecera = snapshot().getJSONObject("equipo");
        assertEquals("REMITO", cabecera.getString("tipo_ingreso"));
        assertEquals(12, cabecera.getInt("remito_cantidad"));
        assertEquals("Observación del remito", cabecera.getString("remito_observaciones"));
    }

    @Test
    void eliminar_conVersionVieja_conflictoYNoArchivaNada() {
        EquipoOtros equipo = equipoOtros(3);
        int versionVista = eliminador.resumir(equipo.getId()).version();
        equipoOtrosDAO.aplicarMovimientos(equipo.getId(), List.of(new MovimientoMaterial(
            equipo.getMateriales().get(0).getId(), 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));

        assertThrows(ConflictoConcurrenciaException.class,
            () -> eliminador.eliminar(equipo.getId(), versionVista, MOTIVO, PUESTO));

        assertEquals(1, escalar("SELECT COUNT(*) FROM equipo_otros WHERE id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
    }

    @Test
    void eliminar_yaEliminado_conflicto() {
        EquipoOtros equipo = equipoOtros(3);
        int version = eliminador.resumir(equipo.getId()).version();
        eliminador.eliminar(equipo.getId(), version, MOTIVO, PUESTO);

        assertThrows(ConflictoConcurrenciaException.class,
            () -> eliminador.eliminar(equipo.getId(), version, MOTIVO, PUESTO));

        assertEquals(1, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
    }

    /** Atomicidad: el archivador inserta y falla; no queda ni el borrado ni su fila. */
    @Test
    void eliminar_fallaElArchivo_noBorra() {
        EquipoOtros equipo = equipoOtros(3);
        int version = eliminador.resumir(equipo.getId()).version();
        EliminadorEquipoOtros conArchivoRoto = new EliminadorEquipoOtros(new ArchivoIngresosDAO() {
            @Override
            public int archivar(Connection conn, IngresoArchivado ingreso) throws SQLException {
                super.archivar(conn, ingreso);
                throw new SQLException("disco lleno");
            }
        });

        assertThrows(DatabaseException.class,
            () -> conArchivoRoto.eliminar(equipo.getId(), version, MOTIVO, PUESTO));

        assertEquals(1, escalar("SELECT COUNT(*) FROM equipo_otros WHERE id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
    }

    // ── Fases sobre una conexión ajena (la interfaz que usa el borrado de Lavadero) ──

    @Test
    void bloquear_cabeceraInexistente_vacio() throws SQLException {
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            assertEquals(Optional.empty(), eliminador.bloquear(tx.get(), 987654));
        }
    }

    /** {@code salidaIds} trae las salidas de todos los ingresos: así se detecta un derivado compartido. */
    @Test
    void bloquear_traeVersionLotesYTodasLasSalidas() throws SQLException {
        EquipoOtros equipo = equipoOtros(3);
        Lote lote = lanzarLote(Map.of(equipo, 10));
        int salidaA = derivarPorElementoCiclo(ingresoLavadero(), equipo.getId());
        int salidaB = derivarPorInstancia(ingresoLavadero(), equipo.getId());

        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            BloqueoEquipoOtros bloqueado = eliminador.bloquear(tx.get(), equipo.getId()).orElseThrow();

            assertEquals(version(equipo.getId()), bloqueado.version());
            assertEquals(Set.of(lote.getId()), bloqueado.loteIds());
            assertEquals(List.of(salidaA, salidaB), bloqueado.salidaIds());
            assertEquals(List.of(new Bloqueo.LoteEnCurso(lote.getIdNegocio())),
                eliminador.verificar(tx.get(), bloqueado));
        }
    }

    /**
     * Las fases no commitean: si la transacción del llamador se revierte, el equipo y su archivo
     * vuelven juntos. Es el contrato del que depende el borrado de Lavadero.
     */
    @Test
    void archivarYBorrar_sobreConexionAjena_noCommiteaNiCierra() throws SQLException {
        EquipoOtros equipo = equipoOtros(3);
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            Connection conn = tx.get();
            BloqueoEquipoOtros bloqueado = eliminador.bloquear(conn, equipo.getId()).orElseThrow();
            int archivoId = eliminador.archivarYBorrar(conn, equipo.getId(), bloqueado.version(), MOTIVO,
                PUESTO, 77);

            assertTrue(archivoId > 0);
            assertFalse(conn.isClosed());
            assertFalse(conn.getAutoCommit());
            // sin commit: el close() revierte
        }

        assertEquals(1, escalar("SELECT COUNT(*) FROM equipo_otros WHERE id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
    }

    @Test
    void archivarYBorrar_guardaElPadre() throws SQLException {
        EquipoOtros equipo = equipoOtros(3);
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            BloqueoEquipoOtros bloqueado = eliminador.bloquear(tx.get(), equipo.getId()).orElseThrow();
            eliminador.archivarYBorrar(tx.get(), equipo.getId(), bloqueado.version(), MOTIVO, PUESTO, 77);
            tx.commit();
        }

        assertEquals(77, escalar("SELECT archivo_padre_id FROM ingresos_eliminados"));
    }

    @Test
    void archivarYBorrar_versionDistinta_conflicto() throws SQLException {
        EquipoOtros equipo = equipoOtros(3);
        try (TransactionalConnection tx = TransactionalConnection.begin()) {
            int vieja = eliminador.bloquear(tx.get(), equipo.getId()).orElseThrow().version() - 1;
            assertThrows(ConflictoConcurrenciaException.class,
                () -> eliminador.archivarYBorrar(tx.get(), equipo.getId(), vieja, MOTIVO, PUESTO, null));
        }
        assertEquals(1, escalar("SELECT COUNT(*) FROM equipo_otros WHERE id = " + equipo.getId()));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private EquipoOtros equipoOtros(int cantidad) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros("TestElimOtros", cantidad));
        equipoOtrosDAO.guardar(equipo);
        return equipoOtrosDAO.obtenerPorId(equipo.getId());
    }

    private int remitoSinFilas(int cantidad) {
        ejecutarSinChecked("INSERT INTO equipo_otros (nro_cliente, estado, tipo_ingreso, remito_cantidad, "
            + "remito_observaciones) VALUES (1, 'Nuevo', 'REMITO', " + cantidad + ", 'Observación del remito')");
        int id = escalar("SELECT MAX(id) FROM equipo_otros");
        ejecutarSinChecked("UPDATE equipo_otros SET remito_id = '29092026-" + id + "' WHERE id = " + id);
        return id;
    }

    /** Un lote en curso con todos los materiales de esos equipos, y sus litros por ingreso. */
    private Lote lanzarLote(Map<EquipoOtros, Integer> litrosPorEquipo) {
        List<LoteMovimiento> movimientos = litrosPorEquipo.keySet().stream()
            .flatMap(e -> e.getMateriales().stream()
                .map(m -> new LoteMovimiento(m.getId(), e.getId(), m.getCantidad(), true, EstadoEquipo.NUEVO)))
            .toList();
        Map<Integer, Integer> volumenes = new HashMap<>();
        litrosPorEquipo.forEach((e, litros) -> volumenes.put(e.getId(), litros));
        return loteDAO.lanzarLote("E01", 120, 45, movimientos, volumenes);
    }

    private int ingresoLavadero() {
        ejecutarSinChecked("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES (1, NOW(), 'LAVADO')");
        return escalar("SELECT MAX(id) FROM ingresos_lavadero");
    }

    /** Una línea de clasificación del ingreso, lavada en un ciclo finalizado. */
    private int[] lineaLavada(int ingresoId) {
        int elementoId = escalar("SELECT MIN(id) FROM catalogo_elementos_lavadero");
        ejecutarSinChecked("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) "
            + "VALUES (" + ingresoId + ", " + elementoId + ", 5)");
        int lineaId = escalar("SELECT MAX(id) FROM elementos_clasificacion_lavadero");
        int jabonId = escalar("SELECT MIN(id) FROM catalogo_jabones");
        ejecutarSinChecked("INSERT INTO ciclos_lavadero (lavarropas_numero, jabon_id, litros_jabon, tipo_lavado, "
            + "estado, fecha_fin) VALUES (1, " + jabonId + ", 1.50, 'SUCIO', 'FINALIZADO', NOW())");
        int cicloId = escalar("SELECT MAX(id) FROM ciclos_lavadero");
        return new int[] {lineaId, cicloId};
    }

    private int derivarPorElementoCiclo(int ingresoId, int equipoOtrosId) {
        int[] lavada = lineaLavada(ingresoId);
        ejecutarSinChecked("INSERT INTO elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad) "
            + "VALUES (" + lavada[1] + ", " + lavada[0] + ", 5)");
        int elementoCicloId = escalar("SELECT MAX(id) FROM elementos_ciclo_lavadero");
        ejecutarSinChecked("INSERT INTO salidas_lavadero (elemento_ciclo_id, cantidad, destino, equipo_otros_id, "
            + "fecha_salida) VALUES (" + elementoCicloId + ", 5, 'CDE_OTROS', " + equipoOtrosId + ", NOW())");
        return escalar("SELECT MAX(id) FROM salidas_lavadero");
    }

    /** La salida de un equipo repartido: sin {@code elemento_ciclo_id}, con {@code instancia_equipo_id}. */
    private int derivarPorInstancia(int ingresoId, int equipoOtrosId) {
        int[] lavada = lineaLavada(ingresoId);
        ejecutarSinChecked("INSERT INTO instancias_equipo_ciclo (elemento_clasificacion_id, total_partes) "
            + "VALUES (" + lavada[0] + ", 2)");
        int instanciaId = escalar("SELECT MAX(id) FROM instancias_equipo_ciclo");
        ejecutarSinChecked("INSERT INTO elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad, "
            + "instancia_equipo_id) VALUES (" + lavada[1] + ", " + lavada[0] + ", 1, " + instanciaId + ")");
        ejecutarSinChecked("INSERT INTO salidas_lavadero (elemento_ciclo_id, instancia_equipo_id, cantidad, destino, "
            + "equipo_otros_id, fecha_salida) VALUES (NULL, " + instanciaId + ", 1, 'CDE_OTROS', "
            + equipoOtrosId + ", NOW())");
        return escalar("SELECT MAX(id) FROM salidas_lavadero");
    }

    private JSONObject snapshot() {
        return new JSONObject(texto("SELECT snapshot FROM ingresos_eliminados"));
    }

    private int version(int equipoOtrosId) {
        return escalar("SELECT version FROM equipo_otros WHERE id = " + equipoOtrosId);
    }

    private void ejecutarSinChecked(String sql) {
        try {
            ejecutarSQL(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Fixture fallida: " + sql, e);
        }
    }

    private int escalar(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Consulta fallida: " + sql, e);
        }
    }

    private String texto(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Consulta fallida: " + sql, e);
        }
    }
}
