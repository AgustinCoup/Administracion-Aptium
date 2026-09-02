package com.example.features.lavadero.dao;

import com.example.AbstractDAOTest;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.DestinoSalida;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.EstadoIngresoLavadero;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaHistorial;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.MarcaListo;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HistorialLavaderoDAOTest extends AbstractDAOTest {

    private HistorialLavaderoDAO dao;
    private CicloLavaderoDAO     ciclosDao;
    private SalidaLavaderoDAO    salidasDao;

    private int ingresoId;
    private JabonCatalogo jabon;

    private int clasifA;        // 10 unidades
    private int clasifEquipo;   //  1 unidad, para repartir entre lavarropas
    private String nombreA;
    private String nombreB;
    private String nombreEquipo;

    @BeforeEach
    void setUp() throws SQLException {
        dao        = new HistorialLavaderoDAO();
        ciclosDao  = new CicloLavaderoDAO();
        salidasDao = new SalidaLavaderoDAO();

        jabon = primerJabon();

        ejecutarSQL("INSERT INTO clientes (nombre) VALUES ('TestHistorialCliente')");
        int clienteId = lastInsertId();

        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, peso_total_kg, estado) "
                + "VALUES (" + clienteId + ", NOW(), 12.50, 'CLASIFICADO')");
        ingresoId = lastInsertId();

        int catalogoA = catalogoElementoId(1);
        int catalogoB = catalogoElementoId(2);
        int catalogoEquipo = catalogoElementoId(3);
        nombreA = catalogoElementoNombre(catalogoA);
        nombreB = catalogoElementoNombre(catalogoB);
        nombreEquipo = catalogoElementoNombre(catalogoEquipo);

        clasifA = insertarClasificacion(catalogoA, 10);
        insertarClasificacion(catalogoB, 4);   // presente en el resumen, nunca se lanza
        clasifEquipo = insertarClasificacion(catalogoEquipo, 1);
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
        ejecutarSQL("DELETE FROM clientes WHERE nombre LIKE 'TestHistorial%'");
    }

    // ── tabla maestra ────────────────────────────────────────────────────────

    @Test
    void ingresoSinClasificar_daUnaFilaConLosDosConjuntosVacios() throws SQLException {
        int vacio = insertarIngresoPendiente();

        IngresoHistorial fila = historialDe(vacio);

        assertEquals("TestHistorialCliente", fila.clienteNombre());
        assertEquals(EstadoIngresoLavadero.PENDIENTE, fila.estado());
        assertTrue(fila.elementos().isEmpty());
        assertTrue(fila.lavarropas().isEmpty());
        assertEquals(0, fila.cantBolsas());
        assertNotNull(fila.fechaIngreso());
    }

    @Test
    void ingresoConTresBolsasYDosElementos_cuentaTresBolsasYNoSeisNiCatorce() throws SQLException {
        insertarBolsa("5.00");
        insertarBolsa("4.00");
        insertarBolsa("3.50");
        lanzarYFinalizar(1, new LineaLanzamiento(clasifA, 10));

        IngresoHistorial fila = historialDe(ingresoId);

        assertEquals(3, fila.cantBolsas());
    }

    @Test
    void resumen_agregaLosElementosClasificadosYLosLavarropasQueLosLavaron() throws SQLException {
        lanzarYFinalizar(1, new LineaLanzamiento(clasifA, 4));
        lanzarYFinalizar(3, new LineaLanzamiento(clasifA, 6));

        IngresoHistorial fila = historialDe(ingresoId);

        assertEquals(Set.of(nombreA, nombreB, nombreEquipo), fila.elementos());
        assertEquals(Set.of(1, 3), fila.lavarropas());
        assertEquals(0, new BigDecimal("12.50").compareTo(fila.pesoTotalKg()));
    }

    @Test
    void resumen_traeTambienLosIngresosFinalizados() throws SQLException {
        ejecutarSQL("UPDATE ingresos_lavadero SET estado = 'FINALIZADO' WHERE id = " + ingresoId);

        assertEquals(EstadoIngresoLavadero.FINALIZADO, historialDe(ingresoId).estado());
    }

    // ── detalle ──────────────────────────────────────────────────────────────

    @Test
    void lineaClasificadaYTodaviaSinLanzar_apareceSinLavarropasNiFechas() {
        List<LineaHistorial> lineas = dao.findDetalle(ingresoId);

        LineaHistorial lineaA = porElemento(lineas, nombreA);
        assertEquals(10, lineaA.cantidad());
        assertNull(lineaA.lavarropas());
        assertNull(lineaA.fechaLavado());
        assertNull(lineaA.fechaListo());
        assertNull(lineaA.destino());
        assertEquals(3, lineas.size(), "las tres líneas clasificadas, ninguna lanzada");
    }

    @Test
    void clasificacionLanzadaAMedias_lasCantidadesDelDetalleCierranContraLoClasificado() throws SQLException {
        lanzarYFinalizar(1, new LineaLanzamiento(clasifA, 6));

        List<LineaHistorial> deA = dao.findDetalle(ingresoId).stream()
            .filter(l -> nombreA.equals(l.elementoNombre())).toList();

        assertEquals(2, deA.size(), "la tanda lanzada y lo que queda pendiente de lavar");
        assertEquals(10, deA.stream().mapToInt(LineaHistorial::cantidad).sum());
        assertEquals(1, deA.stream().filter(l -> "1".equals(l.lavarropas())).count());
        assertEquals(1, deA.stream().filter(l -> l.lavarropas() == null && l.cantidad() == 4).count());
    }

    @Test
    void equipoRepartidoEnDosLavarropas_daUnaSolaLineaYNoDos() throws SQLException {
        repartirEquipo(2, 3);
        finalizarCiclosDe(2, 3);

        List<LineaHistorial> delEquipo = dao.findDetalle(ingresoId).stream()
            .filter(l -> nombreEquipo.equals(l.elementoNombre())).toList();

        assertEquals(1, delEquipo.size());
        LineaHistorial linea = delEquipo.get(0);
        assertEquals("2, 3", linea.lavarropas());
        assertEquals(1, linea.cantidad());
        assertEquals(2, linea.totalPartes());
        assertNotNull(linea.fechaLavado());
    }

    @Test
    void equipoRepartidoConUnaParteEnCicloActivo_seMuestraSinFechaDeLavado() throws SQLException {
        repartirEquipo(2, 3);
        finalizarCiclosDe(2);

        LineaHistorial linea = porElemento(dao.findDetalle(ingresoId), nombreEquipo);

        assertEquals("2, 3", linea.lavarropas());
        assertNull(linea.fechaLavado());
    }

    @Test
    void tandaLavadaYMarcadaListo_tieneFechaListoYDestinoNulo() throws SQLException {
        lanzarYFinalizar(1, new LineaLanzamiento(clasifA, 10));
        salidasDao.marcarListo(List.of(new MarcaListo(pendienteDe(nombreA), 10)));

        LineaHistorial linea = porElemento(dao.findDetalle(ingresoId), nombreA);

        assertNotNull(linea.fechaListo());
        assertNull(linea.destino(), "sin destino todavía es un estado legítimo, no un dato faltante");
        assertEquals(10, linea.cantidad());
    }

    @Test
    void tandaYaDerivada_muestraSuDestino() throws SQLException {
        lanzarYFinalizar(1, new LineaLanzamiento(clasifA, 10));
        salidasDao.marcarListo(List.of(new MarcaListo(pendienteDe(nombreA), 10)));
        ejecutarSQL("UPDATE salidas_lavadero SET destino = 'FUERA_DE_FLUJO', fecha_salida = NOW()");

        assertEquals(DestinoSalida.FUERA_DE_FLUJO,
            porElemento(dao.findDetalle(ingresoId), nombreA).destino());
    }

    @Test
    void unaMismaTandaConDosSalidas_sigueSiendoUnaSolaLinea() throws SQLException {
        lanzarYFinalizar(1, new LineaLanzamiento(clasifA, 10));
        salidasDao.marcarListo(List.of(new MarcaListo(pendienteDe(nombreA), 6)));
        ejecutarSQL("UPDATE salidas_lavadero SET destino = 'FUERA_DE_FLUJO', fecha_salida = NOW()");
        salidasDao.marcarListo(List.of(new MarcaListo(pendienteDe(nombreA), 4)));

        List<LineaHistorial> deA = dao.findDetalle(ingresoId).stream()
            .filter(l -> nombreA.equals(l.elementoNombre())).toList();

        assertEquals(1, deA.size());
        assertEquals(10, deA.get(0).cantidad());
    }

    @Test
    void detalleDeUnIngresoInexistente_daListaVacia() {
        assertTrue(dao.findDetalle(999_999).isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private IngresoHistorial historialDe(int id) {
        return dao.obtenerHistorial().stream()
            .filter(i -> i.id() == id)
            .findFirst()
            .orElseThrow(() -> new AssertionError("el historial no trajo el ingreso " + id));
    }

    private LineaHistorial porElemento(List<LineaHistorial> lineas, String nombre) {
        List<LineaHistorial> coincidencias = lineas.stream()
            .filter(l -> nombre.equals(l.elementoNombre())).toList();
        assertEquals(1, coincidencias.size(), "se esperaba una única línea de " + nombre);
        return coincidencias.get(0);
    }

    private ElementoLavadoPendiente pendienteDe(String nombre) {
        return salidasDao.obtenerLavadosPendientesDeListo().stream()
            .filter(p -> nombre.equals(p.elementoNombre()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no hay pendiente para " + nombre));
    }

    private ConfiguracionCiclo configuracion() {
        return new ConfiguracionCiclo(TipoLavado.SUCIO, jabon, new BigDecimal("1.50"), false, false, null);
    }

    private void lanzarYFinalizar(int lavarropas,
                                  LineaLanzamiento... lineas)
            throws SQLException {
        ciclosDao.lanzarTanda(List.of(new LanzamientoCiclo(lavarropas, configuracion(), List.of(lineas))));
        ciclosDao.finalizarCiclo(escalar("SELECT MAX(id) FROM ciclos_lavadero"));
    }

    /** Reparte el equipo entre los lavarropas dados, una fracción de cantidad 1 en cada uno. */
    private void repartirEquipo(int... lavarropas) {
        final int stagingId = 1;
        List<LanzamientoCiclo> tanda = new ArrayList<>();
        for (int lav : lavarropas) {
            tanda.add(new LanzamientoCiclo(lav, configuracion(),
                List.of(new LineaLanzamiento(
                    clasifEquipo, 1, stagingId, lavarropas.length))));
        }
        ciclosDao.lanzarTanda(tanda);
    }

    private void finalizarCiclosDe(int... lavarropas) throws SQLException {
        for (int lav : lavarropas) {
            ciclosDao.finalizarCiclo(escalar(
                "SELECT MAX(id) FROM ciclos_lavadero WHERE lavarropas_numero = " + lav));
        }
    }

    private int insertarIngresoPendiente() throws SQLException {
        int clienteId = escalar("SELECT id FROM clientes WHERE nombre = 'TestHistorialCliente'");
        ejecutarSQL("INSERT INTO ingresos_lavadero (cliente_id, fecha_ingreso, peso_total_kg, estado) "
                + "VALUES (" + clienteId + ", NOW(), 3.00, 'PENDIENTE')");
        return lastInsertId();
    }

    private void insertarBolsa(String peso) throws SQLException {
        ejecutarSQL("INSERT INTO bolsas_lavadero (ingreso_id, peso_kg) VALUES ("
                + ingresoId + ", " + peso + ")");
    }

    private int insertarClasificacion(int catalogoId, int cantidad) throws SQLException {
        ejecutarSQL("INSERT INTO elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad) VALUES ("
                + ingresoId + ", " + catalogoId + ", " + cantidad + ")");
        return lastInsertId();
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
