package com.example.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.AbstractDAOTest;
import com.example.features.autoclaves.dao.AutoclaveDAO;
import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.service.LoteService;
import com.example.infrastructure.db.ConnectionPool;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La medición de la Fase 6, como regresión en vez de como número suelto.
 *
 * <p>El objetivo de partir el snapshot no era hacer menos queries en abstracto:
 * era que el costo de <b>cada guardado</b> dejara de crecer con el volumen que la
 * empresa acumula. Estos tests cuentan sentencias JDBC reales contra H2 y
 * comprueban esa propiedad, no un número concreto — un número se desactualiza,
 * la propiedad no.
 */
class CostoDelRefrescoTest extends AbstractDAOTest {

    private static final Logger log = LoggerFactory.getLogger(CostoDelRefrescoTest.class);

    /** Cuántos ingresos entregados simulan el histórico acumulado. */
    private static final int HISTORICO = 25;

    private final EquipoDAO      equipoDAO      = new EquipoDAO();
    private final EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(new CatalogoOtrosDAO());

    private final EquipoService      equipoService      = new EquipoService(equipoDAO);
    private final EquipoOtrosService equipoOtrosService = new EquipoOtrosService(equipoOtrosDAO);
    private final AutoclaveService   autoclaveService   = new AutoclaveService(new AutoclaveDAO());
    private final CatalogoService    catalogoService    = new CatalogoService(new CatalogoDAO());
    private final LoteService        loteService        = new LoteService(new LoteDAO());

    private final LectorDatosOperativos operativo = new LectorDatosOperativos(
        equipoService, equipoOtrosService, autoclaveService, catalogoService, loteService);

    @Override
    protected void limpiarTablas() throws SQLException {
        ejecutarSQL("DELETE FROM otros_material_movimientos");
        ejecutarSQL("DELETE FROM equipo_otros_materiales");
        ejecutarSQL("DELETE FROM equipo_otros");
        ejecutarSQL("DELETE FROM equipo_materiales");
        ejecutarSQL("DELETE FROM equipos");
        ejecutarSQL("DELETE FROM catalogo_otros WHERE descripcion LIKE 'TestDesc%'");
    }

    @Test
    @DisplayName("el costo de un guardado no crece con el histórico acumulado")
    void refrescoOperativo_costoIndependienteDelHistorico() {
        sembrarActivos(3);
        int conColaSola = contarSentencias(operativo::get);

        sembrarEntregados(HISTORICO);
        int conHistorico = contarSentencias(operativo::get);

        log.info("Refresco operativo: {} sentencias sin histórico, {} con {} ingresos entregados",
            conColaSola, conHistorico, HISTORICO);

        assertEquals(conColaSola, conHistorico,
            "agregar " + HISTORICO + " ingresos entregados no debe costarle nada a un guardado");
    }

    @Test
    @DisplayName("el refresco operativo cuesta menos que el snapshot único que reemplaza")
    void refrescoOperativo_cuestaMenosQueElSnapshotUnico() {
        sembrarActivos(3);
        sembrarEntregados(HISTORICO);

        int nuevo = contarSentencias(operativo::get);
        int viejo = contarSentencias(this::leerComoAntesDeLaFase6);

        log.info("Por guardado: {} sentencias antes de la Fase 6, {} después", viejo, nuevo);

        assertTrue(nuevo < viejo,
            "el refresco por guardado debe costar menos: antes " + viejo + ", ahora " + nuevo);
    }

    @Test
    @DisplayName("el histórico sigue costando lo que cuesta, pero solo al abrir su pantalla")
    void historial_pagaPorTodoElVolumen() {
        sembrarActivos(3);
        int sinHistorico = contarSentencias(
            new LectorHistorialEquipos(equipoService, equipoOtrosService)::get);

        sembrarEntregados(HISTORICO);
        int conHistorico = contarSentencias(
            new LectorHistorialEquipos(equipoService, equipoOtrosService)::get);

        // No es un defecto: es la contraparte del reparto. Alguien tiene que leer
        // el histórico para que las pantallas de consulta lo muestren; lo que cambió
        // es que ese costo se paga al abrirlas y no en cada guardado.
        assertTrue(conHistorico > sinHistorico,
            "el histórico se lee entero: " + sinHistorico + " → " + conHistorico);
    }

    // ── Lo que leía el snapshot único, para tener con qué comparar ────────────

    private void leerComoAntesDeLaFase6() {
        equipoService.obtenerTodos();
        equipoOtrosService.obtenerTodos();
        autoclaveService.obtenerTodos();
        catalogoService.obtenerVolumenes();
        loteService.obtenerLotesActivosPorAutoclave();
        loteService.obtenerTodosLosLotes();
    }

    // ── Semillas ─────────────────────────────────────────────────────────────

    private void sembrarActivos(int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            equipoDAO.guardarEquipo(ortopedia(EstadoEquipo.NUEVO));
            equipoOtrosDAO.guardar(otros("TestDescActivo" + i));
        }
    }

    private void sembrarEntregados(int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            equipoDAO.guardarEquipo(ortopedia(EstadoEquipo.ENTREGADO));

            EquipoOtros equipo = otros("TestDescEntregado" + i);
            equipoOtrosDAO.guardar(equipo);
            try {
                ejecutarSQL("UPDATE equipo_otros_materiales SET estado = 'Entregado' " +
                            "WHERE equipo_otros_id = " + equipo.getId());
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo sembrar el histórico", e);
            }
        }
    }

    private static Equipo ortopedia(EstadoEquipo estadoMaterial) {
        Equipo equipo = new Equipo();
        equipo.setNroCliente(1);
        equipo.setNroInstitucion(1);
        equipo.setClienteNombre("Cliente Seed");
        equipo.agregarMaterial(new Material(400, "Tornillera", 1, estadoMaterial));
        return equipo;
    }

    private static EquipoOtros otros(String descripcion) {
        EquipoOtros equipo = new EquipoOtros();
        equipo.setNroCliente(1);
        equipo.setTipoIngreso(TipoIngresoOtros.DETALLES);
        equipo.agregarMaterial(new MaterialOtros(descripcion, 1));
        return equipo;
    }

    // ── Contador de sentencias ───────────────────────────────────────────────

    /**
     * Ejecuta la acción con el DataSource envuelto en un proxy que cuenta cada
     * {@code execute*} que pasa por JDBC. Restaura el original siempre.
     */
    private static int contarSentencias(Runnable accion) {
        DataSource real = ConnectionPool.getDataSource();
        AtomicInteger sentencias = new AtomicInteger();
        ConnectionPool.setDataSourceForTesting(contando(real, sentencias));
        try {
            accion.run();
        } finally {
            ConnectionPool.setDataSourceForTesting(real);
        }
        return sentencias.get();
    }

    private static DataSource contando(DataSource real, AtomicInteger sentencias) {
        return proxy(DataSource.class, (proxy, metodo, args) -> {
            Object resultado = invocar(real, metodo, args);
            return resultado instanceof Connection conexion
                ? envolverConexion(conexion, sentencias)
                : resultado;
        });
    }

    private static Connection envolverConexion(Connection real, AtomicInteger sentencias) {
        return proxy(Connection.class, (proxy, metodo, args) -> {
            Object resultado = invocar(real, metodo, args);
            if (resultado instanceof PreparedStatement sentencia) {
                return envolverSentencia(PreparedStatement.class, sentencia, sentencias);
            }
            if (resultado instanceof Statement sentencia) {
                return envolverSentencia(Statement.class, sentencia, sentencias);
            }
            return resultado;
        });
    }

    private static <T extends Statement> T envolverSentencia(Class<T> tipo, T real,
                                                             AtomicInteger sentencias) {
        return proxy(tipo, (proxy, metodo, args) -> {
            if (metodo.getName().startsWith("execute")) sentencias.incrementAndGet();
            return invocar(real, metodo, args);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> tipo, InvocationHandler manejador) {
        return (T) Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, manejador);
    }

    private static Object invocar(Object destino, java.lang.reflect.Method metodo, Object[] args)
            throws Throwable {
        try {
            return metodo.invoke(destino, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
