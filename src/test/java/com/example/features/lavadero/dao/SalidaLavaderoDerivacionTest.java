package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.constants.Constantes;
import com.example.common.exception.BusinessException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteAptium;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteCDE;
import com.example.features.lavadero.dao.derivadores.ConstructorIngresoCDE;
import com.example.features.lavadero.dao.derivadores.DerivadorFueraDeFlujo;
import com.example.features.lavadero.dao.derivadores.DerivadorIngresoCDE;
import com.example.features.lavadero.dao.derivadores.DerivadorSalidas;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.features.lavadero.model.TipoLavado;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Derivación de salidas de lavadero: la transacción que cruza {@code salidas_lavadero} y
 * {@code equipo_otros}, y el paso del ingreso de lavadero a FINALIZADO.
 */
class SalidaLavaderoDerivacionTest extends AbstractDAOTest {

    private SalidaLavaderoDAO dao;
    private CicloLavaderoDAO  ciclosDao;
    private EquipoOtrosDAO    equipoOtrosDAO;

    private DerivadorSalidas fueraDeFlujo;
    private DerivadorSalidas cdeCliente;
    private DerivadorSalidas cdeAptium;

    private int clienteA;
    private int clienteB;
    private int ingresoA;
    private int ingresoB;

    private int clasifA1;   // ingreso A, elemento 1, 10 unidades
    private int clasifA2;   // ingreso A, elemento 2,  6 unidades
    private int clasifB1;   // ingreso B, elemento 3,  4 unidades
    private String nombre1;
    private String nombre2;
    private String nombre3;

    private JabonCatalogo jabon;

    @BeforeEach
    void setUp() throws SQLException {
        dao            = new SalidaLavaderoDAO();
        ciclosDao      = new CicloLavaderoDAO();
        equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());

        fueraDeFlujo = new DerivadorFueraDeFlujo();
        cdeCliente   = new DerivadorIngresoCDE(AccionSalida.CDE_CLIENTE, new ConstructorIngresoCDE(),
                                               AsignadorClienteCDE.CLIENTE_ORIGINAL, equipoOtrosDAO);
        cdeAptium    = new DerivadorIngresoCDE(AccionSalida.CDE_APTIUM, new ConstructorIngresoCDE(),
                                               new AsignadorClienteAptium(new ClienteDAO()), equipoOtrosDAO);

        jabon = primerJabon();

        clienteA = insertarCliente("TestDerivClienteA");
        clienteB = insertarCliente("TestDerivClienteB");
        ingresoA = insertarIngreso(clienteA);
        ingresoB = insertarIngreso(clienteB);

        int catalogo1 = catalogoElementoId(1);
        int catalogo2 = catalogoElementoId(2);
        int catalogo3 = catalogoElementoId(3);
        nombre1 = catalogoElementoNombre(catalogo1);
        nombre2 = catalogoElementoNombre(catalogo2);
        nombre3 = catalogoElementoNombre(catalogo3);

        clasifA1 = insertarClasificacion(ingresoA, catalogo1, 10);
        clasifA2 = insertarClasificacion(ingresoA, catalogo2, 6);
        clasifB1 = insertarClasificacion(ingresoB, catalogo3, 4);
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM salidas_lavadero");
        ejecutarSQL("DELETE FROM equipo_otros WHERE nro_cliente IN "
                + "(SELECT id FROM clientes WHERE nombre LIKE 'TestDeriv%' OR nombre = '"
                + Constantes.Lavadero.CLIENTE_APTIUM + "')");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestDeriv%'");
        // Los nombres de los elementos de lavadero no están entre las semillas de catalogo_otros,
        // así que borrarlos por nombre sólo saca lo que creó este test al derivar al CDE.
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion IN ('"
                + nombre1 + "', '" + nombre2 + "', '" + nombre3 + "')");
    }

    // ── FUERA_DE_FLUJO ───────────────────────────────────────────────────────

    @Test
    @DisplayName("salir del flujo estampa el destino y no crea nada en el CDE")
    void fueraDeFlujo_noCreaIngresoDeCde() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10));
        marcarTodoListo();
        List<SalidaLista> salidas = dao.obtenerListasSinDestino();

        dao.derivar(fueraDeFlujo, salidas);

        assertTrue(dao.obtenerListasSinDestino().isEmpty());
        assertEquals(1, contar("salidas_lavadero WHERE destino = 'FUERA_DE_FLUJO'"));
        assertEquals(1, contar("salidas_lavadero WHERE equipo_otros_id IS NULL"));
        assertEquals(1, contar("salidas_lavadero WHERE fecha_salida IS NOT NULL"));
        assertEquals(0, contar("equipo_otros WHERE nro_cliente = " + clienteA));
    }

    // ── CDE_CLIENTE ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ingresar al CDE con su cliente crea un equipo otros a nombre del cliente original")
    void cdeCliente_creaElIngresoConSusBanderas() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10), movimiento(clasifA2, 6));
        marcarTodoListo();

        dao.derivar(cdeCliente, dao.obtenerListasSinDestino());

        int equipoId = escalar("SELECT MAX(id) FROM equipo_otros WHERE nro_cliente = " + clienteA);
        assertEquals(1, contar("equipo_otros WHERE nro_cliente = " + clienteA));
        assertEquals(0, escalar("SELECT requiere_lavado FROM equipo_otros WHERE id = " + equipoId));
        assertEquals(1, escalar("SELECT requiere_empaque FROM equipo_otros WHERE id = " + equipoId));
        assertEquals("DETALLES", texto("SELECT tipo_ingreso FROM equipo_otros WHERE id = " + equipoId));
        assertEquals(2, contar("equipo_otros_materiales WHERE equipo_otros_id = " + equipoId));
        assertEquals(10, escalar("SELECT cantidad FROM equipo_otros_materiales WHERE equipo_otros_id = "
                + equipoId + " AND descripcion = '" + nombre1 + "'"));
        assertEquals(2, contar("salidas_lavadero WHERE equipo_otros_id = " + equipoId));
        assertEquals(2, contar("salidas_lavadero WHERE destino = 'CDE_OTROS'"));
    }

    @Test
    @DisplayName("dos clientes derivados juntos con su cliente producen dos ingresos separados")
    void cdeCliente_unIngresoPorCliente() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10), movimiento(clasifB1, 4));
        marcarTodoListo();

        dao.derivar(cdeCliente, dao.obtenerListasSinDestino());

        int equipoA = escalar("SELECT MAX(id) FROM equipo_otros WHERE nro_cliente = " + clienteA);
        int equipoB = escalar("SELECT MAX(id) FROM equipo_otros WHERE nro_cliente = " + clienteB);
        assertNotEquals(equipoA, equipoB);
        assertEquals(1, contar("salidas_lavadero WHERE equipo_otros_id = " + equipoA));
        assertEquals(1, contar("salidas_lavadero WHERE equipo_otros_id = " + equipoB));
        assertEquals(0, contar("equipo_otros WHERE nro_cliente = " + idAptium()));
    }

    // ── CDE_APTIUM ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("ingresar al CDE como APTIUM junta dos clientes en un solo ingreso")
    void cdeAptium_unSoloIngresoParaTodos() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10), movimiento(clasifB1, 4));
        marcarTodoListo();

        dao.derivar(cdeAptium, dao.obtenerListasSinDestino());

        int aptium = idAptium();
        assertEquals(1, contar("equipo_otros WHERE nro_cliente = " + aptium));
        assertEquals(0, contar("equipo_otros WHERE nro_cliente IN (" + clienteA + ", " + clienteB + ")"));

        int equipoId = escalar("SELECT MAX(id) FROM equipo_otros WHERE nro_cliente = " + aptium);
        assertEquals(2, contar("equipo_otros_materiales WHERE equipo_otros_id = " + equipoId));
        assertEquals(2, contar("salidas_lavadero WHERE equipo_otros_id = " + equipoId));
    }

    @Test
    @DisplayName("las dos acciones de CDE dejan el mismo destino persistido")
    void ambasAccionesDeCde_persistenCdeOtros() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10));
        lanzarYFinalizar(2, movimiento(clasifB1, 4));
        marcarTodoListo();

        List<SalidaLista> salidas = dao.obtenerListasSinDestino();
        dao.derivar(cdeCliente, salidas.stream().filter(s -> s.ingresoId() == ingresoA).toList());
        dao.derivar(cdeAptium,  salidas.stream().filter(s -> s.ingresoId() == ingresoB).toList());

        assertEquals(2, contar("salidas_lavadero WHERE destino = 'CDE_OTROS'"));
        assertEquals(0, contar("salidas_lavadero WHERE destino = 'FUERA_DE_FLUJO'"));
    }

    @Test
    @DisplayName("sin el cliente APTIUM la derivación falla y no escribe nada")
    void cdeAptium_sinElClienteAptium_lanzaYNoEscribe() throws SQLException {
        ClienteDAO sinAptium = mock(ClienteDAO.class);
        when(sinAptium.buscarPorNombre(anyString())).thenReturn(List.of());
        DerivadorSalidas derivador = new DerivadorIngresoCDE(
            AccionSalida.CDE_APTIUM, new ConstructorIngresoCDE(),
            new AsignadorClienteAptium(sinAptium), equipoOtrosDAO);

        lanzarYFinalizar(1, movimiento(clasifA1, 10));
        marcarTodoListo();
        List<SalidaLista> salidas = dao.obtenerListasSinDestino();

        assertThrows(ResourceNotFoundException.class, () -> dao.derivar(derivador, salidas));

        assertEquals(1, dao.obtenerListasSinDestino().size());
        assertEquals(1, contar("salidas_lavadero WHERE destino IS NULL"));
        assertEquals(0, contar("equipo_otros WHERE nro_cliente = " + clienteA));
    }

    // ── atomicidad y relectura ───────────────────────────────────────────────

    @Test
    @DisplayName("si falla la creación del ingreso de CDE, salidas_lavadero queda intacta")
    void fallaAlCrearElIngreso_noEstampaNingunDestino() throws SQLException {
        EquipoOtrosDAO roto = mock(EquipoOtrosDAO.class);
        when(roto.guardar(any(Connection.class), any(EquipoOtros.class)))
            .thenThrow(new SQLException("fallo simulado al guardar el ingreso"));
        DerivadorSalidas derivador = new DerivadorIngresoCDE(
            AccionSalida.CDE_CLIENTE, new ConstructorIngresoCDE(),
            AsignadorClienteCDE.CLIENTE_ORIGINAL, roto);

        lanzarYFinalizar(1, movimiento(clasifA1, 10), movimiento(clasifA2, 6));
        marcarTodoListo();
        List<SalidaLista> salidas = dao.obtenerListasSinDestino();

        assertThrows(DatabaseException.class, () -> dao.derivar(derivador, salidas));

        assertEquals(2, dao.obtenerListasSinDestino().size());
        assertEquals(2, contar("salidas_lavadero WHERE destino IS NULL"));
        assertEquals(0, contar("salidas_lavadero WHERE destino IS NOT NULL"));
        assertEquals(EstadoIngresoLavadero.LAVADO.name(), estadoIngreso(ingresoA));
    }

    @Test
    @DisplayName("derivar una salida ya derivada falla entera y no cambia nada")
    void salidaYaDerivada_lanzaYNoTocaNada() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10), movimiento(clasifA2, 6));
        marcarTodoListo();
        List<SalidaLista> snapshot = dao.obtenerListasSinDestino();
        dao.derivar(fueraDeFlujo, List.of(snapshot.get(0)));

        assertThrows(BusinessException.class, () -> dao.derivar(cdeCliente, snapshot));

        assertEquals(1, contar("salidas_lavadero WHERE destino = 'FUERA_DE_FLUJO'"));
        assertEquals(1, contar("salidas_lavadero WHERE destino IS NULL"));
        assertEquals(0, contar("equipo_otros WHERE nro_cliente = " + clienteA));
    }

    // ── paso a FINALIZADO ────────────────────────────────────────────────────

    @Test
    @DisplayName("derivar todo lo clasificado de un ingreso lo pasa a FINALIZADO")
    void ingresoCompleto_pasaAFinalizado() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifB1, 4));
        marcarTodoListo();
        assertEquals(EstadoIngresoLavadero.LAVADO.name(), estadoIngreso(ingresoB));

        dao.derivar(fueraDeFlujo, dao.obtenerListasSinDestino());

        assertEquals(EstadoIngresoLavadero.FINALIZADO.name(), estadoIngreso(ingresoB));
    }

    @Test
    @DisplayName("derivar sólo una parte deja el ingreso en LAVADO")
    void ingresoParcial_sigueEnLavado() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10), movimiento(clasifA2, 6));
        marcarTodoListo();

        List<SalidaLista> deA1 = dao.obtenerListasSinDestino().stream()
                .filter(s -> nombre1.equals(s.elementoNombre())).toList();
        dao.derivar(fueraDeFlujo, deA1);

        assertEquals(EstadoIngresoLavadero.LAVADO.name(), estadoIngreso(ingresoA));
    }

    @Test
    @DisplayName("derivar el resto después completa el ingreso y recién ahí pasa a FINALIZADO")
    void ingresoCompletadoEnDosTandas_terminaFinalizado() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10), movimiento(clasifA2, 6));
        marcarTodoListo();

        List<SalidaLista> deA1 = dao.obtenerListasSinDestino().stream()
                .filter(s -> nombre1.equals(s.elementoNombre())).toList();
        dao.derivar(fueraDeFlujo, deA1);
        assertEquals(EstadoIngresoLavadero.LAVADO.name(), estadoIngreso(ingresoA));

        dao.derivar(cdeCliente, dao.obtenerListasSinDestino());

        assertEquals(EstadoIngresoLavadero.FINALIZADO.name(), estadoIngreso(ingresoA));
    }

    // ── convivencia con Correcciones ─────────────────────────────────────────

    @Test
    @DisplayName("eliminar desde Correcciones el ingreso derivado deja la salida sin puntero pero con destino")
    void eliminarElIngresoDeCde_noRompeLaSalida() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA1, 10));
        marcarTodoListo();
        dao.derivar(cdeCliente, dao.obtenerListasSinDestino());
        int equipoId = escalar("SELECT MAX(id) FROM equipo_otros WHERE nro_cliente = " + clienteA);

        equipoOtrosDAO.eliminarEquipo(equipoId);

        assertEquals(0, contar("equipo_otros WHERE id = " + equipoId));
        assertEquals(1, contar("salidas_lavadero WHERE destino = 'CDE_OTROS'"));
        assertEquals(1, contar("salidas_lavadero WHERE equipo_otros_id IS NULL"));
    }

    // ── guardas ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("derivar una selección vacía no abre ninguna transacción")
    void seleccionVacia_lanza() {
        assertThrows(BusinessException.class, () -> dao.derivar(fueraDeFlujo, List.of()));
        assertThrows(BusinessException.class, () -> dao.derivar(fueraDeFlujo, null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private LineaLanzamiento movimiento(int clasificacionId, int cantidad) {
        return new LineaLanzamiento(clasificacionId, cantidad);
    }

    private void lanzarYFinalizar(int lavarropas, LineaLanzamiento... lineas) throws SQLException {
        ciclosDao.lanzarTanda(List.of(new LanzamientoCiclo(lavarropas,
            new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, new BigDecimal("1.50"), false, false, null),
            List.of(lineas))));
        ciclosDao.finalizarCiclo(escalar("SELECT MAX(id) FROM ciclos_lavadero"));
    }

    /** Marca como Listo todo el saldo pendiente: el punto de partida de cualquier derivación. */
    private void marcarTodoListo() {
        List<ElementoLavadoPendiente> pendientes = dao.obtenerLavadosPendientesDeListo();
        dao.marcarListo(pendientes.stream()
                .map(p -> new MarcaListo(p, p.cantidadPendiente()))
                .toList());
    }

    private int idAptium() throws SQLException {
        return escalar("SELECT id FROM clientes WHERE nombre = '"
                + Constantes.Lavadero.CLIENTE_APTIUM + "'");
    }

    private String estadoIngreso(int ingresoId) throws SQLException {
        return texto("SELECT estado FROM ingresos_lavadero WHERE id = " + ingresoId);
    }

    private int insertarCliente(String nombre) throws SQLException {
        ejecutarSQL("INSERT INTO clientes (nombre) VALUES ('" + nombre + "')");
        return escalar("SELECT LAST_INSERT_ID()");
    }

    private int insertarIngreso(int clienteId) throws SQLException {
        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES ("
                + clienteId + ", NOW(), 'CLASIFICADO')");
        int id = escalar("SELECT LAST_INSERT_ID()");
        ejecutarSQL("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + id + ", 5.00)");
        return id;
    }

    private int insertarClasificacion(int ingresoId, int catalogoId, int cantidad) throws SQLException {
        ejecutarSQL("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES ("
                + ingresoId + ", " + catalogoId + ", " + cantidad + ")");
        return escalar("SELECT LAST_INSERT_ID()");
    }

    private int catalogoElementoId(int offset) throws SQLException {
        return escalar("SELECT id FROM catalogo_elementos_lavadero ORDER BY id LIMIT 1 OFFSET " + (offset - 1));
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
