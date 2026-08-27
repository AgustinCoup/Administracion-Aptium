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
        ejecutarSQL("DELETE FROM instancias_equipo_ciclo");
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

    // ── crearInstanciaEquipo / instancia_equipo_id ───────────────────────────
    // Invariante 9: un equipo repartido en N lavarropas consume 1 unidad de su
    // línea de clasificación, nunca N.

    @Test
    void crearInstanciaEquipo_devuelveIdYPersisteTotalPartes() throws SQLException {
        int instanciaId = dao.crearInstanciaEquipo(elementoClasifId, 3);

        assertTrue(instanciaId > 0);
        assertEquals(3, totalPartesPersistido(instanciaId));
    }

    @Test
    void disponibles_fraccionesDeEquipoCuentanComoUnaSolaUnidad() {
        int instanciaId = dao.crearInstanciaEquipo(elementoClasifId, 4);
        for (int lav = 1; lav <= 4; lav++) {
            dao.lanzarCiclo(lav, config(new BigDecimal("1.5")),
                List.of(new ElementoCicloMovimiento(elementoClasifId, 1, instanciaId)));
        }

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(1, items.get(0).getCantidadYaProcesada());
        assertEquals(9, items.get(0).getCantidadDisponible());
    }

    @Test
    void disponibles_dosInstanciasDeLaMismaLinea_sumanDosUnidades() {
        int instanciaSinRepartir = dao.crearInstanciaEquipo(elementoClasifId, 1);
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")),
            List.of(new ElementoCicloMovimiento(elementoClasifId, 1, instanciaSinRepartir)));

        int instanciaRepartida = dao.crearInstanciaEquipo(elementoClasifId, 3);
        for (int lav = 2; lav <= 4; lav++) {
            dao.lanzarCiclo(lav, config(new BigDecimal("1.5")),
                List.of(new ElementoCicloMovimiento(elementoClasifId, 1, instanciaRepartida)));
        }

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(2, items.get(0).getCantidadYaProcesada());
    }

    @Test
    void disponibles_combinaFraccionesDeEquipoYUnidadesRegulares() {
        // SQL_DISPONIBLES no distingue por categoría (cel.categoria), sólo por si
        // instancia_equipo_id es NULL. En el dominio real una línea de clasificación es o
        // EQUIPO o REGULAR (no se mezclan), pero esta prueba verifica que la aritmética
        // combinada (suma de regulares + cuenta de instancias) es correcta igual con datos
        // que sí la mezclan, para no depender de esa restricción de dominio no forzada en BD.
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")),
            List.of(new ElementoCicloMovimiento(elementoClasifId, 3)));

        int instanciaId = dao.crearInstanciaEquipo(elementoClasifId, 1);
        dao.lanzarCiclo(2, config(new BigDecimal("1.5")),
            List.of(new ElementoCicloMovimiento(elementoClasifId, 1, instanciaId)));

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(4, items.get(0).getCantidadYaProcesada());
    }

    @Test
    void lanzarCiclo_persisteInstanciaEquipoIdCuandoElMovimientoLoTrae() throws SQLException {
        int instanciaId = dao.crearInstanciaEquipo(elementoClasifId, 1);

        dao.lanzarCiclo(1, config(new BigDecimal("1.5")),
            List.of(new ElementoCicloMovimiento(elementoClasifId, 1, instanciaId)));

        assertEquals(instanciaId, instanciaEquipoIdPersistido());
    }

    @Test
    void lanzarCiclo_sinInstancia_persisteInstanciaEquipoIdNulo() throws SQLException {
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")),
            List.of(new ElementoCicloMovimiento(elementoClasifId, 3)));

        assertNull(instanciaEquipoIdPersistido());
    }

    // ── detectarLineasSobregiradas (decisión D) ──────────────────────────────

    @Test
    void detectarLineasSobregiradas_datosSucios_delataLaLinea() throws SQLException {
        int lineaId = clasificacionConCantidad(1);
        for (int lav = 1; lav <= 4; lav++) {
            dao.lanzarCiclo(lav, config(new BigDecimal("1.5")),
                List.of(new ElementoCicloMovimiento(lineaId, 1)));
        }

        List<com.example.features.lavadero.dao.helpers.LineaSobregirada> lineas =
            dao.detectarLineasSobregiradas();

        assertEquals(1, lineas.size());
        assertEquals(lineaId, lineas.get(0).elementoClasificacionId());
        assertEquals(1, lineas.get(0).cantidad());
        assertEquals(4, lineas.get(0).yaProcesada());
    }

    @Test
    void detectarLineasSobregiradas_datosLimpios_noApareceNada() {
        int lineaId = clasificacionConCantidad(1);
        int instanciaId = dao.crearInstanciaEquipo(lineaId, 4);
        for (int lav = 1; lav <= 4; lav++) {
            dao.lanzarCiclo(lav, config(new BigDecimal("1.5")),
                List.of(new ElementoCicloMovimiento(lineaId, 1, instanciaId)));
        }

        assertTrue(dao.detectarLineasSobregiradas().isEmpty());
    }

    @Test
    void detectarLineasSobregiradas_lineaEnElLimite_noAparece() {
        int lineaId = clasificacionConCantidad(4);
        for (int lav = 1; lav <= 4; lav++) {
            dao.lanzarCiclo(lav, config(new BigDecimal("1.5")),
                List.of(new ElementoCicloMovimiento(lineaId, 1)));
        }

        assertTrue(dao.detectarLineasSobregiradas().isEmpty());
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

    /**
     * Una línea lavada en varios ciclos suma igual que si se hubiera lavado en uno.
     * El {@code LEFT JOIN} del cálculo multiplica la línea por cada fila de ciclo, así que el
     * total tiene que salir de la clasificación agregada aparte y no del producto cartesiano.
     */
    @Test
    void finalizarCiclo_marcaIngresoLavadoCuandoLaLineaSeLavoEnDosCiclos() throws SQLException {
        dao.lanzarCiclo(1, config(new BigDecimal("1.5")), movimientos(6));
        dao.finalizarCiclo(lastInsertIdDeCiclos());
        assertEquals(0, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId),
            "con 6 de 10 lavadas el ingreso todavía no está lavado");

        dao.lanzarCiclo(2, config(new BigDecimal("1.5")), movimientos(4));
        dao.finalizarCiclo(lastInsertIdDeCiclos());

        assertEquals(1, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId),
            "6 + 4 cubren las 10 de la línea: el ingreso está lavado");
    }

    /**
     * Invariante 9 en esta capa: el cálculo de "ya está todo lavado" cuenta un equipo repartido
     * como 1 y no como N. Con la suma cruda de {@code eci.cantidad}, las 3 fracciones darían 3
     * y pasarían a LAVADO un ingreso al que todavía le faltan 7 unidades de la línea.
     */
    @Test
    void finalizarCiclo_lasFraccionesDeUnEquipoNoAdelantanElLavadoDelIngreso() throws SQLException {
        int instanciaId = dao.crearInstanciaEquipo(elementoClasifId, 3);
        for (int lav = 1; lav <= 3; lav++) {
            dao.lanzarCiclo(lav, config(new BigDecimal("1.5")),
                List.of(new ElementoCicloMovimiento(elementoClasifId, 1, instanciaId)));
            dao.finalizarCiclo(lastInsertIdDeCiclos());
        }

        assertEquals(0, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId),
            "3 fracciones consumen 1 de las 10 unidades de la línea, no 3");

        dao.lanzarCiclo(4, config(new BigDecimal("1.5")), movimientos(9));
        dao.finalizarCiclo(lastInsertIdDeCiclos());

        assertEquals(1, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId),
            "1 del equipo repartido + 9 regulares cubren las 10");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<ElementoCicloMovimiento> movimientos(int cantidad) {
        return List.of(new ElementoCicloMovimiento(elementoClasifId, cantidad));
    }

    private int clasificacionConCantidad(int cantidad) {
        try {
            ejecutarSQL("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES ("
                    + ingresoId + ", " + elementoCatalogoId + ", " + cantidad + ")");
            return lastInsertId();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

    private int totalPartesPersistido(int instanciaId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT total_partes FROM instancias_equipo_ciclo WHERE id = " + instanciaId)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Integer instanciaEquipoIdPersistido() throws SQLException {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT instancia_equipo_id FROM elementos_ciclo_lavadero ORDER BY id DESC LIMIT 1")) {
            rs.next();
            int valor = rs.getInt(1);
            return rs.wasNull() ? null : valor;
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
