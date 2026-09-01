package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.common.exception.DatabaseException;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.TipoLavado;
import com.example.infrastructure.db.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CicloLavaderoDAOTest extends AbstractDAOTest {

    /** Los ids de instancia del staging son locales a la tanda: cualquiera sirve. */
    private static final int STAGING_ID = 1;

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

    // ── lanzarTanda ──────────────────────────────────────────────────────────

    @Test
    void lanzarTanda_insertaEnAmbosTablas() throws SQLException {
        lanzarCiclo(1, config(new BigDecimal("1.50")), linea(3));

        assertEquals(1, contarFilas("ciclos_lavadero"));
        assertEquals(1, contarFilas("elementos_ciclo_lavadero"));
    }

    @Test
    void lanzarTanda_variosLavarropas_escribeUnCicloPorCadaUno() throws SQLException {
        dao.lanzarTanda(List.of(
            new LanzamientoCiclo(1, config(new BigDecimal("1.5")), List.of(linea(3))),
            new LanzamientoCiclo(2, config(new BigDecimal("1.5")), List.of(linea(4)))));

        assertEquals(2, contarFilas("ciclos_lavadero"));
        assertEquals(2, contarFilas("elementos_ciclo_lavadero"));
    }

    /**
     * El motivo de que la tanda sea una sola transacción: con una por lavarropas, el ciclo #1 y
     * la instancia quedaban escritos y la instancia quedaba con 1 de sus 2 fracciones —contada
     * como consumida en Disponibles y nunca completa en Salidas, o sea el equipo desaparecido.
     */
    @Test
    void lanzarTanda_siUnCicloFalla_noQuedaNadaEscrito() throws SQLException {
        final int clasificacionInexistente = 999_999;
        List<LanzamientoCiclo> tanda = List.of(
            new LanzamientoCiclo(1, config(new BigDecimal("1.5")),
                List.of(new LineaLanzamiento(elementoClasifId, 1, STAGING_ID, 2))),
            new LanzamientoCiclo(2, config(new BigDecimal("1.5")),
                List.of(new LineaLanzamiento(clasificacionInexistente, 1, STAGING_ID, 2))));

        assertThrows(DatabaseException.class, () -> dao.lanzarTanda(tanda));

        assertEquals(0, contarFilas("instancias_equipo_ciclo"),
            "la instancia se crea dentro de la misma transacción: si la tanda falla, no queda");
        assertEquals(0, contarFilas("ciclos_lavadero"),
            "el ciclo del lavarropas 1 alcanzó a insertarse pero tiene que volver atrás");
        assertEquals(0, contarFilas("elementos_ciclo_lavadero"));
    }

    @Test
    void lanzarTanda_cicloActivoPorLavarropas_apareceMapeado() {
        lanzarCiclo(2, new ConfiguracionCiclo(
            TipoLavado.LIMPIO, jabon, new BigDecimal("2.00"), true, true, new BigDecimal("40.00")),
            linea(3));

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
        lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.PODRIDO), linea(3));

        assertEquals("PODRIDO", tipoLavadoPersistido());
    }

    @Test
    void tipoLavado_roundTrip_enCiclosActivos() {
        lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.LIMPIO),  linea(1));
        lanzarCiclo(2, config(new BigDecimal("1.5"), TipoLavado.SUCIO),   linea(1));
        lanzarCiclo(3, config(new BigDecimal("1.5"), TipoLavado.PODRIDO), linea(1));

        Map<Integer, CicloLavadero> activos = dao.obtenerCiclosActivosPorLavarropas();

        assertEquals(TipoLavado.LIMPIO,  activos.get(1).getTipoLavado());
        assertEquals(TipoLavado.SUCIO,   activos.get(2).getTipoLavado());
        assertEquals(TipoLavado.PODRIDO, activos.get(3).getTipoLavado());
    }

    @Test
    void tipoLavado_roundTrip_enCiclosFinalizados() throws SQLException {
        lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.PODRIDO), linea(1));
        dao.finalizarCiclo(lastInsertIdDeCiclos());

        List<CicloLavadero> finalizados = dao.obtenerCiclosFinalizados();

        assertEquals(1, finalizados.size());
        assertEquals(TipoLavado.PODRIDO, finalizados.get(0).getTipoLavado());
    }

    @Test
    void tipoLavado_roundTrip_enTodosLosCiclos() throws SQLException {
        lanzarCiclo(1, config(new BigDecimal("1.5"), TipoLavado.LIMPIO), linea(1));
        dao.finalizarCiclo(lastInsertIdDeCiclos());
        lanzarCiclo(2, config(new BigDecimal("1.5"), TipoLavado.SUCIO), linea(1));

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
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(10));

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertTrue(items.isEmpty());
    }

    @Test
    void disponibles_incluyeElementoParcialmenteProcesado() {
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(4));

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(6, items.get(0).getCantidadDisponible());
        assertEquals(4, items.get(0).getCantidadYaProcesada());
    }

    // ── instancias de equipo repartido ───────────────────────────────────────
    // Invariante 9: un equipo repartido en N lavarropas consume 1 unidad de su
    // línea de clasificación, nunca N.

    @Test
    void lanzarTanda_creaUnaInstanciaConSuTotalDePartes() throws SQLException {
        int instanciaId = repartirEquipo(elementoClasifId, 1, 2, 3);

        assertEquals(1, contarFilas("instancias_equipo_ciclo"),
            "las 3 fracciones comparten un id de staging: una sola instancia en la base");
        assertEquals(3, totalPartesPersistido(instanciaId));
    }

    @Test
    void disponibles_fraccionesDeEquipoCuentanComoUnaSolaUnidad() {
        repartirEquipo(elementoClasifId, 1, 2, 3, 4);

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(1, items.get(0).getCantidadYaProcesada());
        assertEquals(9, items.get(0).getCantidadDisponible());
    }

    @Test
    void disponibles_dosInstanciasDeLaMismaLinea_sumanDosUnidades() {
        repartirEquipo(elementoClasifId, 1);
        repartirEquipo(elementoClasifId, 2, 3, 4);

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
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(3));
        repartirEquipo(elementoClasifId, 2);

        List<ElementoCicloItem> items = dao.obtenerElementosDisponiblesParaCiclo();

        assertEquals(1, items.size());
        assertEquals(4, items.get(0).getCantidadYaProcesada());
    }

    @Test
    void lanzarTanda_persisteElIdDeInstanciaQueCreoLaPropiaTanda() throws SQLException {
        int instanciaId = repartirEquipo(elementoClasifId, 1);

        assertEquals(instanciaId, instanciaEquipoIdPersistido());
    }

    @Test
    void lanzarTanda_sinInstancia_persisteInstanciaEquipoIdNulo() throws SQLException {
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(3));

        assertNull(instanciaEquipoIdPersistido());
    }

    // ── detectarLineasSobregiradas (decisión D) ──────────────────────────────

    @Test
    void detectarLineasSobregiradas_datosSucios_delataLaLinea() throws SQLException {
        int lineaId = clasificacionConCantidad(1);
        for (int lav = 1; lav <= 4; lav++) {
            lanzarCiclo(lav, config(new BigDecimal("1.5")), new LineaLanzamiento(lineaId, 1));
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
        repartirEquipo(clasificacionConCantidad(1), 1, 2, 3, 4);

        assertTrue(dao.detectarLineasSobregiradas().isEmpty());
    }

    @Test
    void detectarLineasSobregiradas_lineaEnElLimite_noAparece() {
        int lineaId = clasificacionConCantidad(4);
        for (int lav = 1; lav <= 4; lav++) {
            lanzarCiclo(lav, config(new BigDecimal("1.5")), new LineaLanzamiento(lineaId, 1));
        }

        assertTrue(dao.detectarLineasSobregiradas().isEmpty());
    }

    // ── finalizarCiclo ───────────────────────────────────────────────────────

    @Test
    void finalizarCiclo_marcaFechaFinYEstado() throws SQLException {
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(5));
        int cicloId = lastInsertIdDeCiclos();

        dao.finalizarCiclo(cicloId);

        assertEquals(0, contarFilas("ciclos_lavadero WHERE fecha_fin IS NULL"));
        assertEquals(1, contarFilas("ciclos_lavadero WHERE estado = 'FINALIZADO'"));
    }

    @Test
    void finalizarCiclo_marcaIngresoLavadoCuandoTodoProcesado() throws SQLException {
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(10));
        int cicloId = lastInsertIdDeCiclos();

        dao.finalizarCiclo(cicloId);

        assertEquals(1, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId));
    }

    @Test
    void finalizarCiclo_noMarcaLavadoCuandoProcesadoParcial() throws SQLException {
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(5));
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
        lanzarCiclo(1, config(new BigDecimal("1.5")), linea(6));
        dao.finalizarCiclo(lastInsertIdDeCiclos());
        assertEquals(0, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId),
            "con 6 de 10 lavadas el ingreso todavía no está lavado");

        lanzarCiclo(2, config(new BigDecimal("1.5")), linea(4));
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
        repartirEquipo(elementoClasifId, 1, 2, 3);
        for (int lav = 1; lav <= 3; lav++) {
            dao.finalizarCiclo(cicloIdDe(lav));
        }

        assertEquals(0, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId),
            "3 fracciones consumen 1 de las 10 unidades de la línea, no 3");

        lanzarCiclo(4, config(new BigDecimal("1.5")), linea(9));
        dao.finalizarCiclo(lastInsertIdDeCiclos());

        assertEquals(1, contarFilas("ingresos_lavadero WHERE estado = 'LAVADO' AND id = " + ingresoId),
            "1 del equipo repartido + 9 regulares cubren las 10");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private LineaLanzamiento linea(int cantidad) {
        return new LineaLanzamiento(elementoClasifId, cantidad);
    }

    /** Una tanda de un solo lavarropas: el caso más común de los tests. */
    private void lanzarCiclo(int lavarropas, ConfiguracionCiclo config, LineaLanzamiento... lineas) {
        dao.lanzarTanda(List.of(new LanzamientoCiclo(lavarropas, config, List.of(lineas))));
    }

    /**
     * Reparte un equipo entre los lavarropas dados: una fracción de cantidad 1 en cada uno,
     * todas con el mismo id de staging, en la <b>única</b> tanda que las puede crear juntas.
     *
     * @return el id que la instancia recibió en la base
     */
    private int repartirEquipo(int elementoClasificacionId, int... lavarropas) {
        List<LanzamientoCiclo> tanda = new ArrayList<>();
        for (int lav : lavarropas) {
            tanda.add(new LanzamientoCiclo(lav, config(new BigDecimal("1.5")),
                List.of(new LineaLanzamiento(elementoClasificacionId, 1, STAGING_ID, lavarropas.length))));
        }
        dao.lanzarTanda(tanda);
        return escalar("SELECT MAX(id) FROM instancias_equipo_ciclo");
    }

    private int cicloIdDe(int lavarropas) {
        return escalar("SELECT MAX(id) FROM ciclos_lavadero WHERE lavarropas_numero = " + lavarropas);
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

    private int totalPartesPersistido(int instanciaId) {
        return escalar("SELECT total_partes FROM instancias_equipo_ciclo WHERE id = " + instanciaId);
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

    private int lastInsertId() {
        return escalar("SELECT LAST_INSERT_ID()");
    }

    private int lastInsertIdDeCiclos() {
        return escalar("SELECT MAX(id) FROM ciclos_lavadero");
    }

    private int contarFilas(String tableAndCondition) {
        return escalar("SELECT COUNT(*) FROM " + tableAndCondition);
    }

    private int escalar(String sql) {
        try (Connection conn = ConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
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
