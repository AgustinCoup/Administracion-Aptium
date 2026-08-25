package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.BusinessException;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.features.lavadero.model.TipoLavado;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalidaLavaderoDAOTest extends AbstractDAOTest {

    private SalidaLavaderoDAO dao;
    private CicloLavaderoDAO  ciclosDao;

    private int clienteId;
    private int ingresoId;
    private JabonCatalogo jabon;

    /** Tres líneas de clasificación distintas, para probar el marcado de varias filas a la vez. */
    private int clasifA;   // 10 unidades
    private int clasifB;   //  6 unidades
    private int clasifC;   //  4 unidades
    private String nombreA;
    private String nombreB;
    private String nombreC;

    @BeforeEach
    void setUp() throws SQLException {
        dao       = new SalidaLavaderoDAO();
        ciclosDao = new CicloLavaderoDAO();

        jabon = primerJabon();

        ejecutarSQL("INSERT INTO clientes (nombre) VALUES ('TestSalidaCliente')");
        clienteId = lastInsertId();

        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES ("
                + clienteId + ", NOW(), 'CLASIFICADO')");
        ingresoId = lastInsertId();

        ejecutarSQL("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingresoId + ", 5.00)");

        int catalogoA = catalogoElementoId(1);
        int catalogoB = catalogoElementoId(2);
        int catalogoC = catalogoElementoId(3);
        nombreA = catalogoElementoNombre(catalogoA);
        nombreB = catalogoElementoNombre(catalogoB);
        nombreC = catalogoElementoNombre(catalogoC);

        clasifA = insertarClasificacion(catalogoA, 10);
        clasifB = insertarClasificacion(catalogoB, 6);
        clasifC = insertarClasificacion(catalogoC, 4);
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM salidas_lavadero");
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestSalida%'");
    }

    // ── obtenerLavadosPendientesDeListo ──────────────────────────────────────

    @Test
    void elementoDeCicloActivo_noApareceComoPendienteDeListo() {
        lanzarCiclo(1, movimiento(clasifA, 5));

        assertTrue(dao.obtenerLavadosPendientesDeListo().isEmpty());
    }

    @Test
    void alFinalizarElCiclo_apareceConPendienteIgualALaCantidadDelCiclo() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 5));

        List<ElementoLavadoPendiente> pendientes = dao.obtenerLavadosPendientesDeListo();

        assertEquals(1, pendientes.size());
        ElementoLavadoPendiente item = pendientes.get(0);
        assertEquals(nombreA, item.elementoNombre());
        assertEquals("TestSalidaCliente", item.clienteNombre());
        assertEquals(clienteId, item.clienteId());
        assertEquals(ingresoId, item.ingresoId());
        assertEquals(5, item.cantidadLavada());
        assertEquals(0, item.cantidadYaLista());
        assertEquals(5, item.cantidadPendiente());
    }

    @Test
    void unElementoRepartidoEnDosCiclos_apareceComoDosFilasConSaldosIndependientes() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 4));
        lanzarYFinalizar(2, movimiento(clasifA, 6));

        ElementoLavadoPendiente enLavarropas1 = pendientePorLavarropas(1);
        ElementoLavadoPendiente enLavarropas2 = pendientePorLavarropas(2);

        assertEquals(2, dao.obtenerLavadosPendientesDeListo().size());
        assertEquals(4, enLavarropas1.cantidadPendiente());
        assertEquals(6, enLavarropas2.cantidadPendiente());
        assertNotEquals(enLavarropas1.elementoCicloId(), enLavarropas2.elementoCicloId());

        dao.marcarListo(List.of(new MarcaListo(enLavarropas1, 4)));

        assertEquals(1, dao.obtenerLavadosPendientesDeListo().size());
        assertEquals(6, pendientePorLavarropas(2).cantidadPendiente());
    }

    @Test
    void pendientes_traenLavarropasYFechaFinParaDistinguirDosTandasDelMismoElemento() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 4));
        lanzarYFinalizar(3, movimiento(clasifA, 6));

        List<ElementoLavadoPendiente> pendientes = dao.obtenerLavadosPendientesDeListo();

        assertEquals(2, pendientes.size());
        assertTrue(pendientes.stream().allMatch(p -> nombreA.equals(p.elementoNombre())));
        assertEquals(List.of(1, 3), pendientes.stream().map(ElementoLavadoPendiente::lavarropasNumero).sorted().toList());
        assertTrue(pendientes.stream().allMatch(p -> p.fechaFinCiclo() != null));
        assertNotEquals(pendientes.get(0).cicloId(), pendientes.get(1).cicloId());
    }

    // ── marcarListo ──────────────────────────────────────────────────────────

    @Test
    void marcarListoParcial_bajaElPendienteYLaSalidaApareceEnListasSinDestino() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        ElementoLavadoPendiente item = unicoPendiente();

        dao.marcarListo(List.of(new MarcaListo(item, 4)));

        ElementoLavadoPendiente restante = unicoPendiente();
        assertEquals(4, restante.cantidadYaLista());
        assertEquals(6, restante.cantidadPendiente());

        List<SalidaLista> listas = dao.obtenerListasSinDestino();
        assertEquals(1, listas.size());
        assertEquals(4, listas.get(0).cantidad());
        assertEquals(nombreA, listas.get(0).elementoNombre());
    }

    @Test
    void marcarListoDelTotal_elementoDesapareceDePendientes() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));

        dao.marcarListo(List.of(new MarcaListo(unicoPendiente(), 10)));

        assertTrue(dao.obtenerLavadosPendientesDeListo().isEmpty());
        assertEquals(1, dao.obtenerListasSinDestino().size());
    }

    @Test
    void marcarListo_porEncimaDelSaldo_lanzaYNoInsertaNada() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        ElementoLavadoPendiente item = unicoPendiente();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dao.marcarListo(List.of(new MarcaListo(item, 11))));

        assertTrue(ex.getMessage().contains(nombreA), ex.getMessage());
        assertTrue(ex.getMessage().contains("lavarropas 1"), ex.getMessage());
        assertEquals(0, contarFilas("salidas_lavadero"));
    }

    @Test
    void marcarListo_conCantidadCero_lanzaYNoInsertaNada() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        ElementoLavadoPendiente item = unicoPendiente();

        assertThrows(BusinessException.class, () -> dao.marcarListo(List.of(new MarcaListo(item, 0))));

        assertEquals(0, contarFilas("salidas_lavadero"));
    }

    @Test
    void marcarListo_conCantidadNegativa_lanzaYNoInsertaNada() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        ElementoLavadoPendiente item = unicoPendiente();

        assertThrows(BusinessException.class, () -> dao.marcarListo(List.of(new MarcaListo(item, -3))));

        assertEquals(0, contarFilas("salidas_lavadero"));
    }

    @Test
    void marcarListo_conTresMarcasValidas_entranLasTres() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10), movimiento(clasifB, 6), movimiento(clasifC, 4));

        dao.marcarListo(List.of(
            new MarcaListo(pendientePorElemento(nombreA), 10),
            new MarcaListo(pendientePorElemento(nombreB), 6),
            new MarcaListo(pendientePorElemento(nombreC), 4)));

        assertEquals(3, contarFilas("salidas_lavadero"));
        assertEquals(3, dao.obtenerListasSinDestino().size());
        assertTrue(dao.obtenerLavadosPendientesDeListo().isEmpty());
    }

    /** El test del todo-o-nada: la primera marca era válida y tampoco tiene que quedar. */
    @Test
    void marcarListo_siLaDelMedioSobregira_noSeInsertaNingunaFila() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10), movimiento(clasifB, 6), movimiento(clasifC, 4));

        List<MarcaListo> marcas = List.of(
            new MarcaListo(pendientePorElemento(nombreA), 10),   // válida
            new MarcaListo(pendientePorElemento(nombreB), 7),    // sobregira: sólo hay 6
            new MarcaListo(pendientePorElemento(nombreC), 4));   // válida

        BusinessException ex = assertThrows(BusinessException.class, () -> dao.marcarListo(marcas));

        assertTrue(ex.getMessage().contains(nombreB), ex.getMessage());
        assertEquals(0, contarFilas("salidas_lavadero"));
        assertEquals(3, dao.obtenerLavadosPendientesDeListo().size());
    }

    @Test
    void marcarListo_dosVecesConElMismoSnapshot_noPermiteSobregirar() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        ElementoLavadoPendiente snapshot = unicoPendiente();

        dao.marcarListo(List.of(new MarcaListo(snapshot, 6)));

        assertThrows(BusinessException.class, () -> dao.marcarListo(List.of(new MarcaListo(snapshot, 6))));
        assertEquals(6, sumaDeSalidas());
    }

    /** Dos marcas de la misma tanda en la misma llamada se ven entre sí dentro de la transacción. */
    @Test
    void marcarListo_dosMarcasDeLaMismaTandaQueJuntasSobregiran_noInsertaNinguna() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        ElementoLavadoPendiente item = unicoPendiente();

        assertThrows(BusinessException.class, () -> dao.marcarListo(List.of(
            new MarcaListo(item, 6),
            new MarcaListo(item, 6))));

        assertEquals(0, contarFilas("salidas_lavadero"));
    }

    @Test
    void marcarListo_sobreUnCicloTodaviaActivo_lanzaYNoInsertaNada() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        ElementoLavadoPendiente item = unicoPendiente();
        reabrirCiclo(item.cicloId());

        assertThrows(BusinessException.class, () -> dao.marcarListo(List.of(new MarcaListo(item, 1))));

        assertEquals(0, contarFilas("salidas_lavadero"));
    }

    @Test
    void marcarListo_conSeleccionVacia_lanza() {
        assertThrows(BusinessException.class, () -> dao.marcarListo(List.of()));
    }

    // ── obtenerListasSinDestino ──────────────────────────────────────────────

    @Test
    void listas_conservanCicloLavarropasYFechaFinDeCadaTanda() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 4));
        lanzarYFinalizar(3, movimiento(clasifA, 6));
        dao.marcarListo(List.of(
            new MarcaListo(pendientePorLavarropas(1), 4),
            new MarcaListo(pendientePorLavarropas(3), 6)));

        List<SalidaLista> listas = dao.obtenerListasSinDestino();

        assertEquals(2, listas.size());
        assertEquals(List.of(1, 3), listas.stream().map(SalidaLista::lavarropasNumero).sorted().toList());
        assertTrue(listas.stream().allMatch(s -> s.fechaFinCiclo() != null));
        assertTrue(listas.stream().allMatch(s -> s.fechaListo() != null));
        assertTrue(listas.stream().allMatch(s -> nombreA.equals(s.elementoNombre())));
        assertTrue(listas.stream().allMatch(s -> s.ingresoId() == ingresoId));
        assertTrue(listas.stream().allMatch(s -> s.clienteId() == clienteId));
    }

    @Test
    void listas_excluyenLasSalidasQueYaTienenDestino() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        dao.marcarListo(List.of(new MarcaListo(unicoPendiente(), 4)));
        asignarDestino(dao.obtenerListasSinDestino().get(0).salidaId());

        assertTrue(dao.obtenerListasSinDestino().isEmpty());
        assertEquals(1, contarFilas("salidas_lavadero"));
    }

    // ── volverALavado ────────────────────────────────────────────────────────

    @Test
    void volverALavado_deUnaSalidaSinDestino_devuelveLaCantidadAlPendiente() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        dao.marcarListo(List.of(new MarcaListo(unicoPendiente(), 4)));
        int salidaId = dao.obtenerListasSinDestino().get(0).salidaId();

        dao.volverALavado(salidaId);

        assertEquals(0, contarFilas("salidas_lavadero"));
        assertTrue(dao.obtenerListasSinDestino().isEmpty());
        assertEquals(10, unicoPendiente().cantidadPendiente());
    }

    @Test
    void volverALavado_deUnaSalidaConDestino_lanzaYLaFilaSigueAhi() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10));
        dao.marcarListo(List.of(new MarcaListo(unicoPendiente(), 4)));
        int salidaId = dao.obtenerListasSinDestino().get(0).salidaId();
        asignarDestino(salidaId);

        assertThrows(BusinessException.class, () -> dao.volverALavado(salidaId));

        assertEquals(1, contarFilas("salidas_lavadero WHERE id = " + salidaId));
        assertEquals(6, unicoPendiente().cantidadPendiente());
    }

    @Test
    void volverALavado_deUnaSalidaInexistente_lanza() {
        assertThrows(BusinessException.class, () -> dao.volverALavado(999_999));
    }

    // ── volverALavado en lote ────────────────────────────────────────────────

    @Test
    void volverALavado_deTresSalidasSinDestino_lasRevierteTodas() throws SQLException {
        List<Integer> ids = tresSalidasListas();

        dao.volverALavado(ids);

        assertEquals(0, contarFilas("salidas_lavadero"));
        assertTrue(dao.obtenerListasSinDestino().isEmpty());
        assertEquals(3, dao.obtenerLavadosPendientesDeListo().size());
    }

    /** El todo-o-nada de la dirección inversa: la primera era reversible y tampoco se borra. */
    @Test
    void volverALavado_siLaDelMedioYaTieneDestino_lanzaYLasTresSiguenExistiendo() throws SQLException {
        List<Integer> ids = tresSalidasListas();
        asignarDestino(ids.get(1));

        BusinessException ex = assertThrows(BusinessException.class, () -> dao.volverALavado(ids));

        assertTrue(ex.getMessage().contains(String.valueOf(ids.get(1))), ex.getMessage());
        assertEquals(3, contarFilas("salidas_lavadero"));
    }

    @Test
    void volverALavado_conSeleccionVacia_lanza() {
        assertThrows(BusinessException.class, () -> dao.volverALavado(List.of()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ElementoCicloMovimiento movimiento(int clasificacionId, int cantidad) {
        return new ElementoCicloMovimiento(clasificacionId, cantidad);
    }

    private void lanzarCiclo(int lavarropas, ElementoCicloMovimiento... movimientos) {
        ciclosDao.lanzarCiclo(lavarropas,
            new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, new BigDecimal("1.50"), false, false, null),
            List.of(movimientos));
    }

    private void lanzarYFinalizar(int lavarropas, ElementoCicloMovimiento... movimientos) throws SQLException {
        lanzarCiclo(lavarropas, movimientos);
        ciclosDao.finalizarCiclo(ultimoCicloId());
    }

    /** Tres elementos distintos lavados en el mismo ciclo y marcados Listo enteros. */
    private List<Integer> tresSalidasListas() throws SQLException {
        lanzarYFinalizar(1, movimiento(clasifA, 10), movimiento(clasifB, 6), movimiento(clasifC, 4));
        dao.marcarListo(List.of(
            new MarcaListo(pendientePorElemento(nombreA), 10),
            new MarcaListo(pendientePorElemento(nombreB), 6),
            new MarcaListo(pendientePorElemento(nombreC), 4)));
        return dao.obtenerListasSinDestino().stream().map(SalidaLista::salidaId).toList();
    }

    private ElementoLavadoPendiente unicoPendiente() {
        List<ElementoLavadoPendiente> pendientes = dao.obtenerLavadosPendientesDeListo();
        assertEquals(1, pendientes.size(), "se esperaba un único pendiente");
        return pendientes.get(0);
    }

    private ElementoLavadoPendiente pendientePorElemento(String nombre) {
        return dao.obtenerLavadosPendientesDeListo().stream()
                .filter(p -> nombre.equals(p.elementoNombre()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay pendiente para " + nombre));
    }

    private ElementoLavadoPendiente pendientePorLavarropas(int lavarropas) {
        return dao.obtenerLavadosPendientesDeListo().stream()
                .filter(p -> p.lavarropasNumero() == lavarropas)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay pendiente en el lavarropas " + lavarropas));
    }

    private void asignarDestino(int salidaId) throws SQLException {
        ejecutarSQL("UPDATE salidas_lavadero SET destino = 'FUERA_DE_FLUJO', fecha_salida = NOW() "
                + "WHERE id = " + salidaId);
    }

    private void reabrirCiclo(int cicloId) throws SQLException {
        ejecutarSQL("UPDATE ciclos_lavadero SET fecha_fin = NULL, estado = 'ACTIVO' WHERE id = " + cicloId);
    }

    private int insertarClasificacion(int catalogoId, int cantidad) throws SQLException {
        ejecutarSQL("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES ("
                + ingresoId + ", " + catalogoId + ", " + cantidad + ")");
        return lastInsertId();
    }

    private int sumaDeSalidas() throws SQLException {
        return escalar("SELECT COALESCE(SUM(cantidad), 0) FROM salidas_lavadero");
    }

    private int contarFilas(String tablaYCondicion) throws SQLException {
        return escalar("SELECT COUNT(*) FROM " + tablaYCondicion);
    }

    private int ultimoCicloId() throws SQLException {
        return escalar("SELECT MAX(id) FROM ciclos_lavadero");
    }

    private int lastInsertId() throws SQLException {
        return escalar("SELECT LAST_INSERT_ID()");
    }

    private int catalogoElementoId(int offset) throws SQLException {
        return escalar("SELECT id FROM catalogo_elementos_lavadero ORDER BY id LIMIT 1 OFFSET " + (offset - 1));
    }

    private String catalogoElementoNombre(int id) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT nombre FROM catalogo_elementos_lavadero WHERE id = " + id)) {
            rs.next();
            return rs.getString(1);
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

    private int escalar(String sql) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
