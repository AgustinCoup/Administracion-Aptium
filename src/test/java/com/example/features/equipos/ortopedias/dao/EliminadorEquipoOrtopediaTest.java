package com.example.features.equipos.ortopedias.dao;

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
import com.example.common.exception.ValidationException;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.infrastructure.db.ConnectionPool;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EliminadorEquipoOrtopediaTest extends AbstractDAOTest {

    private static final String MOTIVO = "Se cargó dos veces";
    private static final String PUESTO = "operador@PC-1";

    private final EquipoDAO equipoDAO = new EquipoDAO();
    private final MaterialDAO materialDAO = new MaterialDAO();
    private final LoteDAO loteDAO = new LoteDAO();
    private final EliminadorEquipoOrtopedia eliminador = new EliminadorEquipoOrtopedia(new ArchivoIngresosDAO());

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM ingresos_eliminados");
        ejecutarSQL("DELETE FROM equipos");   // CASCADE: materiales y movimientos
        ejecutarSQL("DELETE FROM lotes");
    }

    // ── resumir ───────────────────────────────────────────────────────────────

    @Test
    void resumir_listaMaterialesConLoteYBloqueoSiHayLoteEnCurso() {
        Equipo equipo = equipoConDosMateriales();
        Material tornillera = material(equipo, 400);
        Lote lote = lanzarLote(equipo, tornillera);

        ResumenEquipo resumen = eliminador.resumir(equipo.getId());

        assertEquals(ModuloIngreso.ORTOPEDIA, resumen.ingreso().modulo());
        assertEquals(equipo.getId(), resumen.ingreso().id());
        assertNotNull(resumen.clienteNombre());
        assertNotNull(resumen.institucionNombre());
        assertEquals(version(equipo.getId()), resumen.version(), "el token de guarda es la version actual");
        assertEquals(2, resumen.materiales().size());
        ResumenEquipo.LineaMaterial enLote = resumen.materiales().stream()
            .filter(m -> m.loteIdNegocio() != null).findFirst().orElseThrow();
        assertEquals(lote.getIdNegocio(), enLote.loteIdNegocio());
        assertEquals(EstadoEquipo.ESTERILIZANDO.getNombre(), enLote.estado());
        assertEquals(List.of(new Bloqueo.LoteEnCurso(lote.getIdNegocio())), resumen.bloqueos());
        assertTrue(resumen.estaBloqueado());
        assertTrue(resumen.ingresosLavaderoOrigen().isEmpty());
    }

    @Test
    void resumir_sinLoteEnCurso_sinBloqueos() {
        Equipo equipo = equipoConDosMateriales();

        ResumenEquipo resumen = eliminador.resumir(equipo.getId());

        assertFalse(resumen.estaBloqueado());
        assertTrue(resumen.materiales().stream().allMatch(m -> m.loteIdNegocio() == null));
    }

    @Test
    void resumir_inexistente_resourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> eliminador.resumir(987654));
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Test
    void eliminar_entregado_borraYArchivaConMaterialesYMovimientos() {
        Equipo equipo = equipoConDosMateriales();
        Material tornillera = material(equipo, 400);
        materialDAO.aplicarMovimientos(equipo.getId(),
            List.of(new MovimientoMaterial(tornillera.getId(), 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));
        ejecutarSinChecked("UPDATE equipo_materiales SET estado = 'Entregado' WHERE equipo_id = " + equipo.getId());
        ejecutarSinChecked("UPDATE equipos SET estado = 'Entregado' WHERE id = " + equipo.getId());
        int movimientosAntes = escalar("SELECT COUNT(*) FROM material_movimientos WHERE equipo_id = " + equipo.getId());
        ResumenEquipo resumen = eliminador.resumir(equipo.getId());

        eliminador.eliminar(equipo.getId(), resumen.version(), MOTIVO, PUESTO);

        assertEquals(0, escalar("SELECT COUNT(*) FROM equipos WHERE id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM equipo_materiales WHERE equipo_id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM material_movimientos WHERE equipo_id = " + equipo.getId()));

        assertEquals(1, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
        assertEquals("ORTOPEDIA", texto("SELECT modulo FROM ingresos_eliminados"));
        assertEquals(equipo.getId(), escalar("SELECT ingreso_id_original FROM ingresos_eliminados"));
        assertEquals("Entregado", texto("SELECT estado FROM ingresos_eliminados"));
        assertEquals(MOTIVO, texto("SELECT motivo FROM ingresos_eliminados"));
        assertEquals(PUESTO, texto("SELECT puesto FROM ingresos_eliminados"));
        assertEquals(resumen.clienteNombre(), texto("SELECT cliente_nombre FROM ingresos_eliminados"));

        JSONObject snapshot = new JSONObject(texto("SELECT snapshot FROM ingresos_eliminados"));
        assertEquals("ORTOPEDIA", snapshot.getString("modulo"));
        assertEquals(EliminadorEquipoOrtopedia.FORMATO_SNAPSHOT, snapshot.getInt("formato"));
        JSONObject cabecera = snapshot.getJSONObject("equipo");
        assertEquals(equipo.getId(), cabecera.getInt("id"));
        assertEquals(resumen.institucionNombre(), cabecera.getString("institucion_nombre"));
        JSONArray materiales = snapshot.getJSONArray("materiales");
        assertEquals(2, materiales.length());
        assertEquals(400, materiales.getJSONObject(0).getInt("codigo_catalogo"));
        assertFalse(materiales.getJSONObject(0).getString("descripcion").isBlank(),
            "la descripción viaja con el material: el catálogo puede cambiar después");
        JSONArray movimientos = snapshot.getJSONArray("movimientos");
        assertEquals(movimientosAntes, movimientos.length(), "se archivan todos los movimientos");
        assertTrue(movimientos.toList().stream()
            .anyMatch(m -> "Lavando".equals(((Map<?, ?>) m).get("estado_destino"))));
    }

    @Test
    void eliminar_conMaterialEnLoteEnCurso_rechazaYNoBorraNada() {
        Equipo equipo = equipoConDosMateriales();
        Lote lote = lanzarLote(equipo, material(equipo, 400));
        int version = eliminador.resumir(equipo.getId()).version();

        EliminacionBloqueadaException e = assertThrows(EliminacionBloqueadaException.class,
            () -> eliminador.eliminar(equipo.getId(), version, MOTIVO, PUESTO));

        assertEquals(List.of(new Bloqueo.LoteEnCurso(lote.getIdNegocio())), e.getBloqueos());
        assertTrue(e.getMessage().contains(lote.getIdNegocio()), "el mensaje dice qué lote finalizar");
        assertEquals(1, escalar("SELECT COUNT(*) FROM equipos WHERE id = " + equipo.getId()));
        assertEquals(2, escalar("SELECT COUNT(*) FROM equipo_materiales WHERE equipo_id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
    }

    @Test
    void eliminar_conLoteFinalizado_borraYElLoteQuedaSinSusMateriales() {
        Equipo equipo = equipoConDosMateriales();
        Lote lote = lanzarLote(equipo, material(equipo, 400));
        assertTrue(loteDAO.finalizarLote(lote.getId()));
        ResumenEquipo resumen = eliminador.resumir(equipo.getId());
        assertFalse(resumen.estaBloqueado(), "un lote finalizado no bloquea");

        eliminador.eliminar(equipo.getId(), resumen.version(), MOTIVO, PUESTO);

        assertEquals(1, escalar("SELECT COUNT(*) FROM lotes WHERE id = " + lote.getId()),
            "el lote queda, vacío: conserva la numeración y la constancia del autoclave");
        assertEquals(45, escalar("SELECT capacidad_usada FROM lotes WHERE id = " + lote.getId()),
            "capacidad_usada es lo que entró al autoclave: no se recalcula");
        assertEquals(0, escalar("SELECT COUNT(*) FROM equipo_materiales WHERE lote_id = " + lote.getId()));
        JSONArray materiales = new JSONObject(texto("SELECT snapshot FROM ingresos_eliminados"))
            .getJSONArray("materiales");
        assertTrue(materiales.toList().stream()
                .anyMatch(m -> lote.getIdNegocio().equals(((Map<?, ?>) m).get("lote_id_negocio"))),
            "el archivo dice en qué lote se esterilizó");
    }

    @Test
    void eliminar_conVersionVieja_conflictoYNoArchivaNada() {
        Equipo equipo = equipoConDosMateriales();
        int versionVista = eliminador.resumir(equipo.getId()).version();
        Material tornillera = material(equipo, 400);
        materialDAO.aplicarMovimientos(equipo.getId(),
            List.of(new MovimientoMaterial(tornillera.getId(), 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));

        assertThrows(ConflictoConcurrenciaException.class,
            () -> eliminador.eliminar(equipo.getId(), versionVista, MOTIVO, PUESTO));

        assertEquals(1, escalar("SELECT COUNT(*) FROM equipos WHERE id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados"));
    }

    @Test
    void eliminar_yaEliminado_conflicto() {
        Equipo equipo = equipoConDosMateriales();
        int version = eliminador.resumir(equipo.getId()).version();
        eliminador.eliminar(equipo.getId(), version, MOTIVO, PUESTO);

        assertThrows(ConflictoConcurrenciaException.class,
            () -> eliminador.eliminar(equipo.getId(), version, MOTIVO, PUESTO));

        assertEquals(1, escalar("SELECT COUNT(*) FROM ingresos_eliminados"), "un solo archivo");
    }

    /**
     * Atomicidad: el archivador inserta su fila y después falla. Ni el borrado ni esa fila pueden
     * quedar — si el archivo pudiera fallar sin revertir, el ingreso se perdería sin copia.
     */
    @Test
    void eliminar_fallaElArchivo_noBorra() {
        Equipo equipo = equipoConDosMateriales();
        int version = eliminador.resumir(equipo.getId()).version();
        EliminadorEquipoOrtopedia conArchivoRoto = new EliminadorEquipoOrtopedia(new ArchivoIngresosDAO() {
            @Override
            public int archivar(Connection conn, IngresoArchivado ingreso) throws SQLException {
                super.archivar(conn, ingreso);
                throw new SQLException("disco lleno");
            }
        });

        assertThrows(DatabaseException.class,
            () -> conArchivoRoto.eliminar(equipo.getId(), version, MOTIVO, PUESTO));

        assertEquals(1, escalar("SELECT COUNT(*) FROM equipos WHERE id = " + equipo.getId()));
        assertEquals(2, escalar("SELECT COUNT(*) FROM equipo_materiales WHERE equipo_id = " + equipo.getId()));
        assertEquals(0, escalar("SELECT COUNT(*) FROM ingresos_eliminados"),
            "la fila que el archivador sí insertó se revirtió con el resto");
    }

    @Test
    void eliminar_motivoBlanco_validationYNoBorra() {
        Equipo equipo = equipoConDosMateriales();
        int version = eliminador.resumir(equipo.getId()).version();

        assertThrows(ValidationException.class, () -> eliminador.eliminar(equipo.getId(), version, "  ", PUESTO));

        assertEquals(1, escalar("SELECT COUNT(*) FROM equipos WHERE id = " + equipo.getId()));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Equipo equipoConDosMateriales() {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroInstitucion(1);
        equipo.agregarMaterial(new Material(400, "Tornillera", 3));
        equipo.agregarMaterial(new Material(401, "Caja", 1));
        equipoDAO.guardarEquipo(equipo);
        return equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
    }

    private static Material material(Equipo equipo, int codigo) {
        return equipo.getMateriales().stream()
            .filter(m -> m.getCodigo() == codigo)
            .min(Comparator.comparingInt(Material::getId))
            .orElseThrow();
    }

    private Lote lanzarLote(Equipo equipo, Material material) {
        return loteDAO.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(material.getId(), equipo.getId(), material.getCantidad(), EstadoEquipo.NUEVO)),
            Map.of());
    }

    private int version(int equipoId) {
        return escalar("SELECT version FROM equipos WHERE id = " + equipoId);
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
