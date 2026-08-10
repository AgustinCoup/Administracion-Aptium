package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.TipoLavado;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CicloLavaderoDAOTest extends AbstractDAOTest {

    private CicloLavaderoDAO dao;

    private int clienteId;
    private int ingresoId;
    private int elementoClasifId;
    private int elementoCatalogoId;
    private JabonCatalogo jabon;

    @BeforeEach
    void setUp() throws SQLException {
        dao = new CicloLavaderoDAO();

        jabon = primerJabon();

        ejecutarSQL("INSERT INTO clientes (nombre) VALUES ('TestCicloCliente')");
        clienteId = lastInsertId();

        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, estado) VALUES ("
                + clienteId + ", NOW(), 'CLASIFICADO')");
        ingresoId = lastInsertId();

        ejecutarSQL("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES (" + ingresoId + ", 5.00)");

        elementoCatalogoId = catalogoElementoId(1);

        ejecutarSQL("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES ("
                + ingresoId + ", " + elementoCatalogoId + ", 10)");
        elementoClasifId = lastInsertId();
    }

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM elementos_ciclo_lavadero");
        ejecutarSQL("DELETE FROM ciclos_lavadero");
        ejecutarSQL("DELETE FROM elementos_clasificacion_lavadero");
        ejecutarSQL("DELETE FROM bolsas_lavadero");
        ejecutarSQL("DELETE FROM ingresos_lavadero");
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestCiclo%'");
    }

    // ── lanzarCiclo ──────────────────────────────────────────────────────────

    @Test
    void lanzarCiclo_insertaEnAmbosTablas() throws SQLException {
        List<ElementoCicloMovimiento> movimientos = List.of(new ElementoCicloMovimiento(elementoClasifId, 3));

        dao.lanzarCiclo(1, config(new BigDecimal("1.50")), movimientos);

        assertEquals(1, contarFilas("ciclos_lavadero"));
        assertEquals(1, contarFilas("elementos_ciclo_lavadero"));
    }

    @Test
    void lanzarCiclo_cicloActivoPorLavarropas_apareceMapeado() {
        List<ElementoCicloMovimiento> movimientos = List.of(new ElementoCicloMovimiento(elementoClasifId, 3));
        dao.lanzarCiclo(2, new ConfiguracionCiclo(
            TipoLavado.LIMPIO, jabon, new BigDecimal("2.00"), true, true, new BigDecimal("40.00")),
            movimientos);

        Map<Integer, CicloLavadero> activos = dao.obtenerCiclosActivosPorLavarropas();

        assertTrue(activos.containsKey(2));
        CicloLavadero ciclo = activos.get(2);
        assertEquals(jabon.getId(), ciclo.getJabon().getId());
        assertEquals(jabon.getNombre(), ciclo.getJabon().getNombre());
        assertTrue(ciclo.isSuavizante());
        assertTrue(ciclo.isPotenciador());
        assertTrue(ciclo.estaActivo());
    }

    // ── tipo de lavado ───────────────────────────────────────────────────────
    // Los tres caminos de lectura tienen su propio SELECT: si uno se olvida de la columna,
    // sólo lo detecta un round-trip por cada uno.

    @Test
    void tipoLavado_sePersisteElName_yNoElNombreDeUi() throws SQLException {
        dao.lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.PODRIDO), movimientos(3));

        assertEquals("PODRIDO", tipoLavadoPersistido());
    }

    @Test
    void tipoLavado_roundTrip_enCiclosActivos() {
        dao.lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.LIMPIO),  movimientos(1));
        dao.lanzarCiclo(2, config(new BigDecimal("1.5"), TipoLavado.SUCIO),   movimientos(1));
        dao.lanzarCiclo(3, config(new BigDecimal("1.5"), TipoLavado.PODRIDO), movimientos(1));

        Map<Integer, CicloLavadero> activos = dao.obtenerCiclosActivosPorLavarropas();

        assertEquals(TipoLavado.LIMPIO,  activos.get(1).getTipoLavado());
        assertEquals(TipoLavado.SUCIO,   activos.get(2).getTipoLavado());
        assertEquals(TipoLavado.PODRIDO, activos.get(3).getTipoLavado());
    }

    @Test
    void tipoLavado_roundTrip_enCiclosFinalizados() throws SQLException {
        dao.lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.PODRIDO), movimientos(1));
        dao.finalizarCiclo(lastInsertIdDeCiclos());

        List<CicloLavadero> finalizados = dao.obtenerCiclosFinalizados();

        assertEquals(1, finalizados.size());
        assertEquals(TipoLavado.PODRIDO, finalizados.get(0).getTipoLavado());
    }

    @Test
    void tipoLavado_roundTrip_enTodosLosCiclos() throws SQLException {
        dao.lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.LIMPIO), movimientos(1));
        dao.finalizarCiclo(lastInsertIdDeCiclos());
        dao.lanzarCiclo(2, config(new BigDecimal("1.5"), TipoLavado.SUCIO), movimientos(1));

        Map<Integer, TipoLavado> porLavarropas = new HashMap<>();
        for (CicloLavadero c : dao.obtenerTodosLosCiclos()) {
            porLavarropas.put(c.getLavarropasNumero(), c.getTipoLavado());
        }

        assertEquals(TipoLavado.LIMPIO, porLavarropas.get(1));
        assertEquals(TipoLavado.SUCIO,  porLavarropas.get(2));
    }

    // ── obtenerElementosDisponiblesParaCiclo ─────────────────────────────────

    @Test
    void disponibles_incluyeElementoConCantidadCompleta() {
        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(10, items.get(0).getCantidadDisponible());
        assertEquals(0, items.get(0).getCantidadYaProcesada());
    }

    @Test
    void disponibles_excluyeElementoTotalmenteProcesado() {
        List<ElementoCicloMovimiento> movimientos = List.of(new ElementoCicloMovimiento(elementoClasifId, 10));
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")), movimientos);

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertTrue(items.isEmpty());
    }

    @Test
    void disponibles_incluyeElementoParcialmenteProcesado() {
        List<ElementoCicloMovimiento> movimientos = List.of(new ElementoCicloMovimiento(elementoClasifId, 4));
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")), movimientos);

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(6, items.get(0).getCantidadDisponible());
        assertEquals(4, items.get(0).getCantidadYaProcesada());
    }

    // ── finalizarCiclo ───────────────────────────────────────────────────────

    @Test
    void finalizarCiclo_marcaFechaFinYEstado() throws SQLException {
        List<ElementoCicloMovimiento> movimientos = List.of(new ElementoCicloMovimiento(elementoClasifId, 5));
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")), movimientos);
        int cicloId = lastInsertIdDeCiclos();

        dao.finalizarCiclo(cicloId);

        assertEquals(0, contarFilas("ciclos_lavadero WHERE fecha_fin IS NULL"));
        assertEquals(1, contarFilas("ciclos_lavadero WHERE estado = 'FINALIZADO'"));
    }

    @Test
    void finalizarCiclo_marcaIngresoLavadoCuandoTodoProcesado() throws SQLException {
        List<ElementoCicloMovimiento> movimientos = List.of(new ElementoCicloMovimiento(elementoClasifId, 10));
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")), movimientos);
        int cicloId = lastInsertIdDeCiclos();

        dao.finalizarCiclo(cicloId);

        assertEquals(1, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId));
    }

    @Test
    void finalizarCiclo_noMarcaLavadoCuandoProcesadoParcial() throws SQLException {
        List<ElementoCicloMovimiento> movimientos = List.of(new ElementoCicloMovimiento(elementoClasifId, 5));
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")), movimientos);
        int cicloId = lastInsertIdDeCiclos();

        dao.finalizarCiclo(cicloId);

        assertEquals(0, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<ElementoCicloMovimiento> movimientos(int cantidad) {
        return List.of(new ElementoCicloMovimiento(elementoClasifId, cantidad));
    }

    private ConfiguracionCiclo config(BigDecimal litrosJabon) {
        return config(litrosJabon, TipoLavado.SUCIO);
    }

    private ConfiguracionCiclo config(BigDecimal litrosJabon, TipoLavado tipoLavado) {
        return new ConfiguracionCiclo(tipoLavado, jabon, litrosJabon, false, false, null);
    }

    private String tipoLavadoPersistido() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo_lavado FROM ciclos_lavadero ORDER BY id DESC LIMIT 1")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private int lastInsertId() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int lastInsertIdDeCiclos() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(id) FROM ciclos_lavadero")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int contarFilas(String tableAndCondition) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableAndCondition)) {
            rs.next();
            return rs.getInt(1);
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

    private int catalogoElementoId(int offset) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id FROM catalogo_elementos_lavadero LIMIT 1 OFFSET " + (offset - 1))) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
