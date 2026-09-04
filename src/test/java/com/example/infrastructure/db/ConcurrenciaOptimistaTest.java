package com.example.infrastructure.db;

import com.example.AbstractDAOTest;
import com.example.common.exception.ConflictoConcurrenciaException;
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
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.dao.ClasificacionLavaderoDAO;
import com.example.features.lavadero.dao.SalidaLavaderoDAO;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.LoteMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite transversal del bloqueo optimista: <b>la regla, no los casos sueltos</b>.
 *
 * <p>Cada flujo protegido tiene además sus tests en el {@code *DAOTest} de su feature, que cubren
 * las variantes. Acá va <em>un</em> caso por flujo, todos con la misma forma, para que se lea de
 * corrido qué garantiza el mecanismo:
 *
 * <pre>
 *   A lee  →  B modifica y commitea  →  A escribe  →  conflicto
 *                                                     y el estado final es exactamente el de B
 * </pre>
 *
 * <p>La segunda mitad es la que importa. Que A reciba una {@link ConflictoConcurrenciaException}
 * es la mitad barata; lo que hay que demostrar es que <b>no quedó nada de A</b> — ni una fila a
 * medias, ni un lote huérfano, ni un movimiento con el estado de origen equivocado. Un rechazo que
 * igual deja rastro es peor que no rechazar: nadie va a buscar ese rastro.
 *
 * <h2>Qué verifica y qué no</h2>
 *
 * <p>Estos tests corren sobre <b>H2 en modo MySQL</b> ({@link AbstractDAOTest}), con
 * {@code maximumPoolSize = 5}, así que dos conexiones simultáneas salen sin infraestructura nueva.
 * Pero <b>H2 no es MySQL</b>: el nivel de aislamiento por defecto y el comportamiento de
 * {@code SELECT ... FOR UPDATE} difieren (H2 corre en {@code READ COMMITTED}; MySQL, en
 * {@code REPEATABLE READ}).
 *
 * <p>Lo que se verifica acá es <b>la guarda</b> —la condición del {@code WHERE} no matchea, o el
 * valor releído no coincide, y entonces la operación se cae y revierte—, que es idéntica en los
 * dos motores. Lo que <b>no</b> se puede verificar acá es el comportamiento del lock: un test de
 * deadlock o de bloqueo mutuo pasaría en H2 y mentiría sobre producción. Para eso está el smoke
 * manual de dos máquinas contra la misma base MySQL.
 *
 * <p>Por eso los casos no arrancan dos hilos: B commitea <b>antes</b> de que A escriba. Esa es
 * exactamente la ventana que el plan viene a cerrar —el tiempo de pensar del operador— y no
 * depende del scheduler para reproducirse.
 */
class ConcurrenciaOptimistaTest extends AbstractDAOTest {

    private final EquipoDAO      equipoDAO      = new EquipoDAO();
    private final MaterialDAO    materialDAO    = new MaterialDAO();
    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());
    private final LoteDAO        loteDAO        = new LoteDAO();

    private final ClasificacionLavaderoDAO clasificacionDAO = new ClasificacionLavaderoDAO();
    private final CicloLavaderoDAO         cicloDAO         = new CicloLavaderoDAO();
    private final SalidaLavaderoDAO        salidaDAO        = new SalidaLavaderoDAO();

    /** Los ids de instancia del staging son locales a la tanda: cualquiera sirve. */
    private static final int STAGING_ID = 1;

    private JabonCatalogo jabon;

    @BeforeEach
    void leerSemillas() throws SQLException {
        jabon = primerJabon();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM salidas_lavadero");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM instancias_equipo_ciclo");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM lote_otros_volumenes");
        ejecutarSQL("DELETE FROM lotes");
        ejecutarSQL("DELETE FROM equipos");   // CASCADE cubre equipo_materiales y material_movimientos
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestConc%' OR descripcion = 'Elementos'");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestConc%'");
    }

    // ── Registrar Estado — ortopedias ─────────────────────────────────────────

    @Test
    @DisplayName("Registrar Estado (ortopedias): el segundo operador choca y no deja movimiento falso")
    void registrarEstadoOrtopedias() {
        Equipo equipo = equipoOrtopediaConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        // B avanza el material y commitea.
        materialDAO.aplicarMovimientos(equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));
        int movimientosTrasB = escalar("SELECT COUNT(*) FROM material_movimientos");

        // A confirma con el snapshot viejo: para él el material seguía en NUEVO.
        assertThrows(ConflictoConcurrenciaException.class, () -> materialDAO.aplicarMovimientos(
            equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO))));

        Equipo despues = equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
        assertEquals(EstadoEquipo.LAVANDO, despues.getMateriales().get(0).getEstado(),
            "queda el avance de B, no el de A");
        assertEquals(movimientosTrasB, escalar("SELECT COUNT(*) FROM material_movimientos"),
            "el movimiento de A no se registró: sin la guarda habría un estado_origen 'Nuevo' falso");
    }

    // ── Registrar Estado — otros ──────────────────────────────────────────────

    @Test
    @DisplayName("Registrar Estado (otros): mismo choque, mismo resultado que en ortopedias")
    void registrarEstadoOtros() {
        EquipoOtros equipo = equipoOtrosConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        equipoOtrosDAO.aplicarMovimientos(equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO)));
        int movimientosTrasB = escalar("SELECT COUNT(*) FROM otros_material_movimientos");

        assertThrows(ConflictoConcurrenciaException.class, () -> equipoOtrosDAO.aplicarMovimientos(
            equipo.getId(),
            List.of(new MovimientoMaterial(materialId, 3, EstadoEquipo.NUEVO, EstadoEquipo.LAVANDO))));

        assertEquals(EstadoEquipo.LAVANDO.getNombre(),
            texto("SELECT estado FROM equipo_otros_materiales WHERE id = " + materialId),
            "queda el avance de B");
        assertEquals(movimientosTrasB, escalar("SELECT COUNT(*) FROM otros_material_movimientos"),
            "el movimiento de A no se registró");
    }

    // ── Lanzar Lote ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Lanzar Lote: el segundo lote se cae entero y no deja fila huérfana en `lotes`")
    void lanzarLote() {
        Equipo equipo = equipoOrtopediaConMaterial(3);
        int materialId = equipo.getMateriales().get(0).getId();

        // B lanza primero: el material queda ESTERILIZANDO y con lote_id poblado.
        loteDAO.lanzarLote("E01", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)),
            Map.of());

        // A arrastró el mismo material a su autoclave cuando todavía estaba en NUEVO.
        assertThrows(ConflictoConcurrenciaException.class, () -> loteDAO.lanzarLote("E02", 120, 45,
            List.of(new LoteMovimiento(materialId, equipo.getId(), 3, EstadoEquipo.NUEVO)),
            Map.of()));

        assertEquals(1, escalar("SELECT COUNT(*) FROM lotes"),
            "el lote de A se revirtió entero: sin eso quedaría un lote vacío ocupando el autoclave");
        assertEquals(0, escalar("SELECT COUNT(*) FROM lotes WHERE autoclave_nombre = 'E02'"));
        assertEquals(1, escalar(
            "SELECT COUNT(*) FROM equipo_materiales WHERE lote_id IS NOT NULL"),
            "el material sigue apuntando sólo al lote de B");
    }

    // ── Lavadero: clasificación ───────────────────────────────────────────────

    @Test
    @DisplayName("Clasificación: la segunda no duplica la ropa del ingreso")
    void clasificacion() {
        int ingresoId = ingresoDeLavadero("TestConcClasif", "PENDIENTE");
        int elemento1 = catalogoElementoId(1);
        int elemento2 = catalogoElementoId(2);

        clasificacionDAO.guardar(ingresoId, List.of(new ElementoClasificacion(elemento1, 10)));

        assertThrows(ConflictoConcurrenciaException.class, () -> clasificacionDAO.guardar(
            ingresoId, List.of(new ElementoClasificacion(elemento2, 7))));

        assertEquals(1, escalar("SELECT COUNT(*) FROM elementos_clasificacion_lavadero"),
            "sin la guarda el ingreso quedaba con el doble de ropa de la que entró");
        assertEquals(10, escalar("SELECT SUM(cantidad) FROM elementos_clasificacion_lavadero"));
    }

    // ── Lavadero: lanzar tanda ────────────────────────────────────────────────

    @Test
    @DisplayName("Lanzar tanda: la segunda no sobregira la línea de clasificación")
    void lanzarTanda() {
        int ingresoId = ingresoDeLavadero("TestConcTanda", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 10);

        // B se lleva 7 de las 10 unidades.
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 7)))));

        // A armó su tanda cuando había 10 disponibles y pide 6: ya no alcanzan.
        assertThrows(ConflictoConcurrenciaException.class,
            () -> cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(2, config(), List.of(
                new LineaLanzamiento(lineaId, 6))))));

        assertEquals(1, escalar("SELECT COUNT(*) FROM ciclos_lavadero"),
            "la tanda de A no dejó ni un ciclo: es todo o nada");
        assertEquals(7, escalar("SELECT SUM(cantidad) FROM elementos_ciclo_lavadero"),
            "la línea no se sobregiró — justo lo que detectarLineasSobregiradas() sale a buscar");
        assertTrue(cicloDAO.detectarLineasSobregiradas().isEmpty(),
            "el detector de líneas sobregiradas no encuentra nada");
    }

    /**
     * El reparto de un mismo equipo entre dos lavarropas de <b>la misma</b> tanda consume 1 unidad,
     * no 2. Es el falso positivo que rompería el flujo si la fórmula de saldo divergiera de
     * {@code SQL_DISPONIBLES}: una tanda legítima rechazada es peor que el bug que la guarda cierra,
     * porque el operador no tiene forma de saber qué hacer con el cartel.
     */
    @Test
    @DisplayName("Lanzar tanda: un equipo repartido en dos lavarropas no se rechaza a sí mismo")
    void lanzarTandaConFraccionesNoEsUnFalsoPositivo() {
        int ingresoId = ingresoDeLavadero("TestConcFrac", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 1);

        cicloDAO.lanzarTanda(List.of(
            new LanzamientoCiclo(1, config(), List.of(new LineaLanzamiento(lineaId, 1, STAGING_ID, 2))),
            new LanzamientoCiclo(2, config(), List.of(new LineaLanzamiento(lineaId, 1, STAGING_ID, 2)))));

        assertEquals(2, escalar("SELECT COUNT(*) FROM elementos_ciclo_lavadero"));
        assertEquals(1, escalar("SELECT COUNT(*) FROM instancias_equipo_ciclo"),
            "las dos fracciones son una sola instancia y consumen 1 de la línea de 1");
    }

    // ── Lavadero: salidas ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Salidas: una salida ya derivada no se puede volver a Lavado")
    void salidaYaDerivadaNoSeRevierte() throws SQLException {
        int ingresoId = ingresoDeLavadero("TestConcSalida", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 5);
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 5)))));
        cicloDAO.finalizarCiclo(escalar("SELECT MAX(id) FROM ciclos_lavadero"));

        ElementoLavadoPendiente pendiente = salidaDAO.obtenerLavadosPendientesDeListo().get(0);
        salidaDAO.marcarListo(List.of(new MarcaListo(pendiente, 5)));
        int salidaId = escalar("SELECT MAX(id) FROM salidas_lavadero");

        // B deriva la salida (le estampa destino) y commitea.
        ejecutarSQL("UPDATE salidas_lavadero SET destino = 'FUERA_DE_FLUJO', fecha_salida = NOW() "
            + "WHERE id = " + salidaId);

        // A la tenía en pantalla como "lista sin destino" y aprieta Volver a Lavado.
        assertThrows(ConflictoConcurrenciaException.class,
            () -> salidaDAO.volverALavado(List.of(salidaId)));

        assertEquals(1, escalar("SELECT COUNT(*) FROM salidas_lavadero WHERE id = " + salidaId),
            "el DELETE de A no borró la salida que B ya derivó");
        assertEquals("FUERA_DE_FLUJO",
            texto("SELECT destino FROM salidas_lavadero WHERE id = " + salidaId));
    }

    @Test
    @DisplayName("Salidas: no se puede marcar Listo más de lo que quedó sin marcar")
    void marcarListoSobreSaldoYaConsumido() {
        int ingresoId = ingresoDeLavadero("TestConcListo", "CLASIFICADO");
        int lineaId = insertarClasificacion(ingresoId, catalogoElementoId(1), 5);
        cicloDAO.lanzarTanda(List.of(new LanzamientoCiclo(1, config(), List.of(
            new LineaLanzamiento(lineaId, 5)))));
        cicloDAO.finalizarCiclo(escalar("SELECT MAX(id) FROM ciclos_lavadero"));

        ElementoLavadoPendiente pendiente = salidaDAO.obtenerLavadosPendientesDeListo().get(0);

        // B marca 4 de las 5 y commitea; A tenía las 5 en pantalla.
        salidaDAO.marcarListo(List.of(new MarcaListo(pendiente, 4)));

        assertThrows(ConflictoConcurrenciaException.class,
            () -> salidaDAO.marcarListo(List.of(new MarcaListo(pendiente, 5))));

        assertEquals(4, escalar("SELECT SUM(cantidad) FROM salidas_lavadero"),
            "queda sólo lo de B: la marca de A no se aplicó ni parcialmente");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Equipo equipoOrtopediaConMaterial(int cantidad) {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroInstitucion(1);
        equipo.agregarMaterial(new Material(400, "Tornillera", cantidad));
        equipoDAO.guardarEquipo(equipo);
        return equipoDAO.obtenerPorId(String.valueOf(equipo.getId()));
    }

    private EquipoOtros equipoOtrosConMaterial(int cantidad) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros("TestConcMaterial", cantidad));
        equipoOtrosDAO.guardar(equipo);
        return equipoOtrosDAO.obtenerTodos().stream()
            .filter(e -> e.getId().equals(equipo.getId()))
            .findFirst().orElseThrow();
    }

    private int ingresoDeLavadero(String nombreCliente, String estado) {
        ejecutarSinChecked("INSERT INTO clientes (nombre) VALUES ('" + nombreCliente + "')");
        int clienteId = escalar("SELECT id FROM clientes WHERE nombre = '" + nombreCliente + "'");
        ejecutarSinChecked("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES ("
            + clienteId + ", NOW(), '" + estado + "')");
        int ingresoId = escalar("SELECT MAX(id) FROM ingresos_lavadero");
        ejecutarSinChecked("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingresoId + ", 5.00)");
        return ingresoId;
    }

    private int insertarClasificacion(int ingresoId, int elementoId, int cantidad) {
        ejecutarSinChecked("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) "
            + "VALUES (" + ingresoId + ", " + elementoId + ", " + cantidad + ")");
        return escalar("SELECT MAX(id) FROM elementos_clasificacion_lavadero");
    }

    private ConfiguracionCiclo config() {
        return new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, new BigDecimal("1.50"), false, false, null);
    }

    // ── Helpers de lectura ────────────────────────────────────────────────────

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

    private JabonCatalogo primerJabon() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, nombre FROM catalogo_jabones ORDER BY id LIMIT 1")) {
            rs.next();
            return new JabonCatalogo(rs.getInt("id"), rs.getString("nombre"));
        }
    }

    private int catalogoElementoId(int offset) {
        return escalar("SELECT id FROM catalogo_elementos_lavadero LIMIT 1 OFFSET " + (offset - 1));
    }
}
