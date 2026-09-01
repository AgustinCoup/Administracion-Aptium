package com.example.features.lavadero;

import com.example.AbstractDAOTest;
import com.example.common.constants.Constantes;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.dao.SalidaLavaderoDAO;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteAptium;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteCDE;
import com.example.features.lavadero.dao.derivadores.ConstructorIngresoCDE;
import com.example.features.lavadero.dao.derivadores.DerivadorFueraDeFlujo;
import com.example.features.lavadero.dao.derivadores.DerivadorIngresoCDE;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lavadero.service.CicloLavaderoService;
import com.example.features.lavadero.service.SalidaLavaderoService;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorrido completo de un {@code Equipo*} subdividido entre lavarropas: de la línea de
 * clasificación al ingreso del CDE, cruzando Ciclos y Salidas por las capas de Service y DAO
 * reales sobre H2.
 *
 * <p>{@code CiclosController} es Swing y no tiene test dedicado, así que este test <b>replica lo
 * que el controller orquesta</b> — armar la tanda con el id de staging compartido entre las
 * fracciones — sin pasar por la UI. Si el controller dejara de hacerlo, este test seguiría en
 * verde: lo que cubre es que el pipeline de abajo respeta la semántica, no que la pantalla lo
 * invoque bien.</p>
 *
 * <p>El invariante bajo prueba, en todas las capas que lo tocan:
 * <b>un equipo repartido en N lavarropas sigue siendo UN equipo</b> — consume 1 unidad de su
 * línea de clasificación (nunca N), no aparece en Salidas hasta que las N partes pasaron por un
 * ciclo finalizado, y genera una sola salida y un solo elemento en el CDE
 * (ver {@code plans/fracciones-de-equipo-persistidas.md}).</p>
 */
class EquipoSubdivididoIntegracionTest extends AbstractDAOTest {

    private static final int PARTES = 3;

    private CicloLavaderoService  ciclos;
    private SalidaLavaderoService salidas;

    private int clienteId;
    private int ingresoId;
    private JabonCatalogo jabon;

    /** Línea de clasificación de un elemento de categoría EQUIPO, cantidad 1. */
    private int    clasifEquipo;
    private String nombreEquipo;

    @BeforeEach
    void setUp() throws SQLException {
        CicloLavaderoDAO  ciclosDao  = new CicloLavaderoDAO();
        SalidaLavaderoDAO salidasDao = new SalidaLavaderoDAO();
        EquipoOtrosDAO    equiposDao = new EquipoOtrosDAO(new CatalogoOtrosDAO());

        ciclos  = new CicloLavaderoService(ciclosDao);
        salidas = new SalidaLavaderoService(salidasDao, List.of(
            new DerivadorFueraDeFlujo(),
            new DerivadorIngresoCDE(AccionSalida.CDE_CLIENTE, new ConstructorIngresoCDE(),
                                    AsignadorClienteCDE.CLIENTE_ORIGINAL, equiposDao),
            new DerivadorIngresoCDE(AccionSalida.CDE_APTIUM, new ConstructorIngresoCDE(),
                                    new AsignadorClienteAptium(new ClienteDAO()), equiposDao)));

        jabon = primerJabon();

        ejecutarSQL("INSERT INTO clientes (nombre) VALUES ('TestIntegracionCliente')");
        clienteId = lastInsertId();

        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES ("
                + clienteId + ", NOW(), '" + EstadoIngresoLavadero.CLASIFICADO + "')");
        ingresoId = lastInsertId();
        ejecutarSQL("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingresoId + ", 5.00)");

        int catalogoEquipo = primerElementoDeCategoria(ElementoCicloItem.CATEGORIA_EQUIPO);
        nombreEquipo = catalogoElementoNombre(catalogoEquipo);
        clasifEquipo = insertarClasificacion(catalogoEquipo, 1);
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM salidas_lavadero");
        ejecutarSQL("DELETE FROM equipo_otros_materiales WHERE equipo_otros_id IN "
                + "(SELECT id FROM equipo_otros WHERE nro_cliente IN "
                + "(SELECT id FROM clientes WHERE nombre LIKE 'TestIntegracion%' OR nombre = '"
                + Constantes.Lavadero.CLIENTE_APTIUM + "'))");
        ejecutarSQL("DELETE FROM equipo_otros WHERE nro_cliente IN "
                + "(SELECT id FROM clientes WHERE nombre LIKE 'TestIntegracion%' OR nombre = '"
                + Constantes.Lavadero.CLIENTE_APTIUM + "')");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM instancias_equipo_ciclo");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion = '" + nombreEquipo + "'");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestIntegracion%'");
    }

    @Test
    @DisplayName("un equipo repartido en 3 lavarropas recorre todo el circuito como UN solo equipo")
    void equipoRepartidoEnTres_deLaClasificacionAlCDE() throws SQLException {
        // ── 1-3. Lo que hace CiclosController al lanzar: una tanda con una instancia de
        //         total_partes = 3 y los tres ciclos que la comparten, todo en una transacción.
        int instanciaId = repartirEquipo(clasifEquipo, 1, 2, 3);
        assertEquals(PARTES, contar("elementos_ciclo_lavadero WHERE instancia_equipo_id = " + instanciaId),
            "las 3 fracciones tienen que compartir la misma instancia persistida");

        // ── 4. INVARIANTE "1 equipo repartido consume 1 unidad, no N": la línea es de cantidad 1
        //       y ya está enteramente procesada, así que no vuelve a ofrecerse para otro ciclo.
        //       Con la fórmula vieja (SUM de cantidades) daba ya_procesada = 3 > 1 y el HAVING
        //       escondía el sobregiro en vez de delatarlo.
        assertFalse(hayDisponible(clasifEquipo),
            "la línea ya está cubierta por la instancia: no puede seguir disponible para otro ciclo");

        // ── 5. INVARIANTE "no aparece en Salidas hasta que las N partes están lavadas".
        finalizarUltimoCiclo(1);
        assertTrue(salidas.obtenerLavadosPendientesDeListo().isEmpty(),
            "con 1 de 3 partes lavadas la instancia todavía no está lista para Salidas");
        finalizarUltimoCiclo(2);
        assertTrue(salidas.obtenerLavadosPendientesDeListo().isEmpty(),
            "con 2 de 3 partes lavadas sigue sin aparecer: ni parcial, ni con menos cantidad");

        // ── 6. Con la tercera parte lavada aparece, UNA sola vez, con los 3 lavarropas juntos.
        finalizarUltimoCiclo(3);
        List<ElementoLavadoPendiente> pendientes = salidas.obtenerLavadosPendientesDeListo();
        assertEquals(1, pendientes.size(), "3 fracciones lavadas son UN pendiente, no 3");
        ElementoLavadoPendiente pendiente = pendientes.get(0);
        assertTrue(pendiente.esInstanciaDeEquipo());
        assertEquals(instanciaId, pendiente.instanciaEquipoId());
        assertEquals(1, pendiente.cantidadPendiente(), "un equipo repartido pendiente es 1, nunca 3");
        assertEquals("1, 2, 3", pendiente.lavarropas());
        assertEquals(nombreEquipo, pendiente.elementoNombre());

        // ── 7. Marcar Listo produce UNA salida.
        salidas.marcarListo(List.of(new MarcaListo(pendiente, pendiente.cantidadPendiente())));
        assertTrue(salidas.obtenerLavadosPendientesDeListo().isEmpty());
        List<SalidaLista> listas = salidas.obtenerListasSinDestino();
        assertEquals(1, listas.size(), "3 fracciones marcadas Listo son UNA salida, no 3");
        SalidaLista lista = listas.get(0);
        assertTrue(lista.esInstanciaDeEquipo());
        assertEquals(1, lista.cantidad());
        assertEquals("1, 2, 3", lista.lavarropas());
        assertEquals(1, contar("salidas_lavadero"));

        // ── 8. Derivar al CDE: un solo EquipoOtros, con un solo material de cantidad 1.
        salidas.derivar(AccionSalida.CDE_CLIENTE, listas);

        assertTrue(salidas.obtenerListasSinDestino().isEmpty());
        assertEquals(1, contar("salidas_lavadero WHERE destino = 'CDE_OTROS' AND fecha_salida IS NOT NULL"));
        assertEquals(1, contar("equipo_otros WHERE nro_cliente = " + clienteId));
        int equipoId = escalar("SELECT MAX(id) FROM equipo_otros WHERE nro_cliente = " + clienteId);
        assertEquals(1, contar("equipo_otros_materiales WHERE equipo_otros_id = " + equipoId),
            "el equipo repartido es UN material en el ingreso del CDE");
        assertEquals(1, escalar("SELECT cantidad FROM equipo_otros_materiales WHERE equipo_otros_id = "
                + equipoId + " AND descripcion = '" + nombreEquipo + "'"),
            "cantidad 1 en el CDE: repartirlo en 3 lavarropas no lo multiplica por 3");
        assertEquals(EstadoIngresoLavadero.FINALIZADO.name(), estadoIngreso(),
            "toda la clasificación del ingreso ya salió con destino");
    }

    @Test
    @DisplayName("volver a Lavado una salida de instancia la devuelve entera a pendientes")
    void volverALavadoDeLaInstancia_laDevuelveComoUnSoloPendiente() throws SQLException {
        int instanciaId = repartirEquipo(clasifEquipo, 1, 2, 3);
        for (int lavarropas = 1; lavarropas <= PARTES; lavarropas++) {
            finalizarUltimoCiclo(lavarropas);
        }
        salidas.marcarListo(List.of(unicoPendienteMarcable()));

        salidas.volverALavado(salidas.obtenerListasSinDestino());

        assertEquals(0, contar("salidas_lavadero"));
        List<ElementoLavadoPendiente> pendientes = salidas.obtenerLavadosPendientesDeListo();
        assertEquals(1, pendientes.size(), "vuelve como UN pendiente, no como 3 fracciones sueltas");
        assertEquals(instanciaId, pendientes.get(0).instanciaEquipoId());
        assertEquals("1, 2, 3", pendientes.get(0).lavarropas());
    }

    @Test
    @DisplayName("de una línea de 2 equipos, repartir uno en 3 deja el otro disponible para lanzar")
    void dosEquiposEnLaMismaLinea_repartirUnoNoConsumeElOtro() throws SQLException {
        // Caso declarado real en la decisión A del blueprint: una línea de clasificación con dos
        // equipos, de los cuales sólo uno se reparte. El primero consume 1 unidad de las 2.
        int otroIngreso = insertarIngreso();
        int linea = insertarClasificacion(otroIngreso, primerElementoDeCategoria(ElementoCicloItem.CATEGORIA_EQUIPO), 2);

        repartirEquipo(linea, 1, 2, 3);
        for (int lavarropas = 1; lavarropas <= PARTES; lavarropas++) {
            finalizarUltimoCiclo(lavarropas);
        }

        // INVARIANTE "1 equipo repartido consume 1 unidad, no N": queda 1 de las 2 sin procesar,
        // así que el ingreso NO terminó de lavarse y la línea sigue disponible para el segundo.
        assertEquals(EstadoIngresoLavadero.CLASIFICADO.name(), estadoIngreso(otroIngreso),
            "el ingreso todavía tiene un equipo sin lavar: no puede estar en LAVADO");
        assertTrue(hayDisponible(linea),
            "queda 1 de los 2 equipos de la línea sin procesar: tiene que seguir disponible");
        assertEquals(1, ciclos.obtenerElementosDisponiblesParaCiclo().stream()
                .filter(item -> item.getElementoClasificacionId() == linea)
                .findFirst().orElseThrow().getCantidadDisponible(),
            "las 3 fracciones consumieron 1 unidad de las 2, no 3");
    }

    @Test
    @DisplayName("dos instancias completas se pueden marcar Listo en la misma selección")
    void dosInstanciasCompletas_seMarcanListoJuntas() throws SQLException {
        int otroIngreso = insertarIngreso();
        int linea = insertarClasificacion(otroIngreso, primerElementoDeCategoria(ElementoCicloItem.CATEGORIA_EQUIPO), 2);

        repartirEquipo(linea, 1, 2);
        finalizarUltimoCiclo(1);
        finalizarUltimoCiclo(2);
        repartirEquipo(linea, 3, 4);
        finalizarUltimoCiclo(3);
        finalizarUltimoCiclo(4);

        List<ElementoLavadoPendiente> pendientes = salidas.obtenerLavadosPendientesDeListo();
        assertEquals(2, pendientes.size(), "dos equipos repartidos son dos pendientes distintos");

        salidas.marcarListo(pendientes.stream().map(p -> new MarcaListo(p, p.cantidadPendiente())).toList());

        assertEquals(2, salidas.obtenerListasSinDestino().size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Reparte un equipo de {@code linea} entre los lavarropas dados: una fracción de cantidad 1
     * en cada uno, todas con el mismo id de staging. Es lo que hace {@code CiclosController} al
     * lanzar, y va en una sola tanda porque el DAO no acepta crear la instancia por separado.
     *
     * @return el id que la instancia recibió en la base
     */
    private int repartirEquipo(int linea, int... lavarropas) throws SQLException {
        final int stagingId = 1;   // local a la tanda: cualquiera sirve
        List<LanzamientoCiclo> tanda = new ArrayList<>();
        for (int lav : lavarropas) {
            tanda.add(new LanzamientoCiclo(lav, configuracion(),
                List.of(new LineaLanzamiento(linea, 1, stagingId, lavarropas.length))));
        }
        ciclos.lanzarTanda(tanda);
        return escalar("SELECT MAX(id) FROM instancias_equipo_ciclo");
    }

    private ConfiguracionCiclo configuracion() {
        return new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, new BigDecimal("1.50"), false, false, null);
    }

    private void finalizarUltimoCiclo(int lavarropas) throws SQLException {
        ciclos.finalizarCiclo(escalar(
            "SELECT MAX(id) FROM ciclos_lavadero WHERE lavarropas_numero = " + lavarropas));
    }

    private MarcaListo unicoPendienteMarcable() {
        List<ElementoLavadoPendiente> pendientes = salidas.obtenerLavadosPendientesDeListo();
        assertEquals(1, pendientes.size(), "se esperaba un único pendiente");
        return new MarcaListo(pendientes.get(0), pendientes.get(0).cantidadPendiente());
    }

    private boolean hayDisponible(int elementoClasificacionId) {
        return ciclos.obtenerElementosDisponiblesParaCiclo().stream()
                .anyMatch(item -> item.getElementoClasificacionId() == elementoClasificacionId);
    }

    private String estadoIngreso() throws SQLException {
        return estadoIngreso(ingresoId);
    }

    private String estadoIngreso(int id) throws SQLException {
        return texto("SELECT estado FROM ingresos_lavadero WHERE id = " + id);
    }

    private int insertarIngreso() throws SQLException {
        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES ("
                + clienteId + ", NOW(), '" + EstadoIngresoLavadero.CLASIFICADO + "')");
        int id = lastInsertId();
        ejecutarSQL("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + id + ", 5.00)");
        return id;
    }

    private int insertarClasificacion(int catalogoId, int cantidad) throws SQLException {
        return insertarClasificacion(ingresoId, catalogoId, cantidad);
    }

    private int insertarClasificacion(int ingreso, int catalogoId, int cantidad) throws SQLException {
        ejecutarSQL("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES ("
                + ingreso + ", " + catalogoId + ", " + cantidad + ")");
        return lastInsertId();
    }

    private int primerElementoDeCategoria(String categoria) throws SQLException {
        return escalar("SELECT MIN(id) FROM catalogo_elementos_lavadero WHERE categoria = '" + categoria + "'");
    }

    private String catalogoElementoNombre(int id) throws SQLException {
        return texto("SELECT nombre FROM catalogo_elementos_lavadero WHERE id = " + id);
    }

    private JabonCatalogo primerJabon() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, nombre FROM catalogo_jabones ORDER BY id LIMIT 1")) {
            rs.next();
            return new JabonCatalogo(rs.getInt("id"), rs.getString("nombre"));
        }
    }

    private int lastInsertId() throws SQLException {
        return escalar("SELECT LAST_INSERT_ID()");
    }

    private int contar(String tablaYCondicion) throws SQLException {
        return escalar("SELECT COUNT(*) FROM " + tablaYCondicion);
    }

    private int escalar(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String texto(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
