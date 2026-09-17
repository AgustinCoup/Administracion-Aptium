package com.example.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Timeouts de red de la URL JDBC y arranque diagnosticable.
 *
 * <p>Los dos tests de relación existen porque cada par de números sólo tiene sentido en un
 * orden, y un comentario no impide que alguien baje uno sin mirar el otro.
 */
class ConnectionPoolTest {

    private DataSource testDataSourceOriginal;

    @BeforeEach
    void guardarEstado() throws Exception {
        testDataSourceOriginal = (DataSource) campo("testDataSource").get(null);
    }

    @AfterEach
    void restaurarEstado() throws Exception {
        campo("fallaDeArranque").set(null, null);
        ConnectionPool.timeoutPermisoMs = ConnectionPool.TIMEOUT_PERMISO_MS;
        ConnectionPool.setDataSourceForTesting(testDataSourceOriginal);
    }

    @Test
    void paramsJdbc_llevaLosDosTimeoutsDeRed() {
        assertAll(
            () -> assertTrue(ConnectionPool.PARAMS_JDBC.contains(
                "connectTimeout=" + ConnectionPool.CONNECT_TIMEOUT_MS), ConnectionPool.PARAMS_JDBC),
            () -> assertTrue(ConnectionPool.PARAMS_JDBC.contains(
                "socketTimeout=" + ConnectionPool.SOCKET_TIMEOUT_MS), ConnectionPool.PARAMS_JDBC),
            () -> assertTrue(ConnectionPool.PARAMS_JDBC.contains("sslMode=REQUIRED"), ConnectionPool.PARAMS_JDBC)
        );
    }

    @Test
    void socketTimeout_esMayorQueElTechoDeConsulta() {
        // Anti-patrón A1: socketTimeout es el respaldo de red, no el techo de la consulta.
        assertTrue(ConnectionPool.SOCKET_TIMEOUT_MS > ConnectionPool.TIMEOUT_CONSULTA_S * 1000,
            "socketTimeout tiene que ser mayor que queryTimeout, o mata consultas legítimas");
    }

    @Test
    void connectTimeout_esEstrictamenteMenorQueElConnectionTimeoutDeHikari() {
        assertTrue(ConnectionPool.CONNECT_TIMEOUT_MS < ConnectionPool.CONNECTION_TIMEOUT_MS,
            "un handshake lento saldría como 'Connection is not available' en vez de como error de red");
    }

    @Test
    void conFallaDeArranque_verificarArranqueYGetConnectionLanzanConLaCausaOriginal() throws Exception {
        RuntimeException causa = simularFallaDeArranque();
        ConnectionPool.setDataSourceForTesting(null);

        SQLException alVerificar = assertThrows(SQLException.class, ConnectionPool::verificarArranque);
        SQLException alPedirConexion = assertThrows(SQLException.class, ConnectionPool::getConnection);

        assertSame(causa, alVerificar.getCause());
        assertSame(causa, alPedirConexion.getCause());
    }

    @Test
    void conFallaDeArranque_getStatsYGetDataSourceLanzanConLaCausaOriginal() throws Exception {
        RuntimeException causa = simularFallaDeArranque();
        ConnectionPool.setDataSourceForTesting(null);

        IllegalStateException alPedirStats = assertThrows(IllegalStateException.class, ConnectionPool::getStats);
        IllegalStateException alPedirDataSource = assertThrows(IllegalStateException.class, ConnectionPool::getDataSource);

        assertSame(causa, alPedirStats.getCause());
        assertSame(causa, alPedirDataSource.getCause());
    }

    @Test
    void conFallaDeArranque_validarConexionDevuelveFalse() throws Exception {
        simularFallaDeArranque();
        ConnectionPool.setDataSourceForTesting(null);

        assertFalse(ConnectionPool.validarConexion());
    }

    @Test
    void sinFallaDeArranque_verificarArranqueNoLanza() {
        assertDoesNotThrow(ConnectionPool::verificarArranque);
    }

    @Test
    void poolNoInicializado_hayPresionEsFalso() {
        // Bajo aptium.testing el campo dataSource real queda en null: es el caso de todos
        // los demás tests de la suite, no sólo de éste.
        assertFalse(ConnectionPool.hayPresion());
    }

    @Test
    void poolCerrado_getStatsYHayPresionNoExplotanYLoDicen() throws Exception {
        // La ventana real de NPE (hallazgo del Paso 3): dataSource != null pero
        // getHikariPoolMXBean() sí es null. Con testDataSource no se llega a esta rama porque
        // getStats()/hayPresion() miran el campo dataSource, no el override de test.
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:connectionpooltest;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        HikariDataSource poolCerrado = new HikariDataSource(config);
        poolCerrado.close();

        Field dataSourceField = campo("dataSource");
        Object original = dataSourceField.get(null);
        try {
            dataSourceField.set(null, poolCerrado);

            assertFalse(ConnectionPool.hayPresion());
            assertEquals("Pool cerrado", ConnectionPool.getStats());
        } finally {
            dataSourceField.set(null, original);
        }
    }

    // ---------- techo de lecturas concurrentes ----------

    @Test
    void permisosConexion_seDerivanDelTamanoDelPool() {
        // Si alguien baja maximumPoolSize y deja el techo escrito aparte, la reserva de los
        // autocompletados desaparece sin que nadie se entere.
        assertEquals(ConnectionPool.MAX_POOL - ConnectionPool.RESERVA_CONEXIONES,
            ConnectionPool.PERMISOS_CONEXION);
        assertTrue(ConnectionPool.PERMISOS_CONEXION > 0);
    }

    @Test
    void timeoutDelPermiso_esMenorQueElConnectionTimeoutDeHikari() {
        // Si fueran iguales, saturar el semáforo y saturar el pool darían el mismo síntoma.
        assertTrue(ConnectionPool.TIMEOUT_PERMISO_MS < ConnectionPool.CONNECTION_TIMEOUT_MS,
            "los dos techos tienen que distinguirse en el log");
    }

    @Test
    void semaforoSaturado_elCheckoutSiguienteFallaConUnMensajeDistintoAlDelPool() throws Exception {
        ConnectionPool.timeoutPermisoMs = 100;   // el test no puede tardar 8 s
        try (HikariDataSource h2 = h2ConPool(ConnectionPool.PERMISOS_CONEXION + 2)) {
            ConnectionPool.setDataSourceForTesting(h2);
            List<Connection> tomadas = new ArrayList<>();
            try {
                for (int i = 0; i < ConnectionPool.PERMISOS_CONEXION; i++) {
                    tomadas.add(ConnectionPool.getConnection());
                }

                SQLException e = assertThrows(SQLException.class, ConnectionPool::getConnection);

                assertTrue(e.getMessage().contains("Techo de lecturas concurrentes"), e.getMessage());
                assertTrue(e.getMessage().contains("No es el connectionTimeout"), e.getMessage());
            } finally {
                for (Connection c : tomadas) {
                    c.close();
                }
            }

            assertEquals(ConnectionPool.PERMISOS_CONEXION, ConnectionPool.permisosDisponibles());
        }
    }

    @Test
    void elPermisoSeLiberaAunqueLaOperacionFalle() throws Exception {
        try (HikariDataSource h2 = h2ConPool(2)) {
            ConnectionPool.setDataSourceForTesting(h2);
            int antes = ConnectionPool.permisosDisponibles();

            assertThrows(SQLException.class, () -> {
                try (Connection conn = ConnectionPool.getConnection();
                     java.sql.Statement st = conn.createStatement()) {
                    st.execute("SELECT * FROM tabla_que_no_existe");
                }
            });

            assertEquals(antes, ConnectionPool.permisosDisponibles());
        }
    }

    @Test
    void masOperacionesConcurrentesQuePermisos_ningunaFallaPorFaltaDeConexion() throws Exception {
        int concurrentes = ConnectionPool.PERMISOS_CONEXION + 2;
        try (HikariDataSource h2 = h2ConPool(ConnectionPool.MAX_POOL)) {
            ConnectionPool.setDataSourceForTesting(h2);
            ExecutorService hilos = Executors.newFixedThreadPool(concurrentes);
            try {
                List<Future<Boolean>> resultados = new ArrayList<>();
                for (int i = 0; i < concurrentes; i++) {
                    resultados.add(hilos.submit(() -> {
                        try (Connection conn = ConnectionPool.getConnection();
                             java.sql.Statement st = conn.createStatement()) {
                            return st.executeQuery("SELECT 1").next();
                        }
                    }));
                }
                for (Future<Boolean> r : resultados) {
                    assertTrue(r.get(30, TimeUnit.SECONDS), "una operación concurrente no leyó");
                }
            } finally {
                hilos.shutdownNow();
            }

            assertEquals(ConnectionPool.PERMISOS_CONEXION, ConnectionPool.permisosDisponibles());
        }
    }

    private static HikariDataSource h2ConPool(int tamano) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:permisos_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(tamano);
        return new HikariDataSource(config);
    }

    private static RuntimeException simularFallaDeArranque() throws Exception {
        RuntimeException causa = new RuntimeException("Communications link failure");
        campo("fallaDeArranque").set(null, causa);
        return causa;
    }

    private static Field campo(String nombre) throws NoSuchFieldException {
        Field f = ConnectionPool.class.getDeclaredField(nombre);
        f.setAccessible(true);
        return f;
    }
}
