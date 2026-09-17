package com.example.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.infrastructure.db.ConexionesSupervisadas;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TokenTarea;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prueba contra <b>MySQL real</b> que cancelar una tarea mata la consulta en el servidor.
 *
 * <p><b>Por qué existe y por qué no puede ser un test normal.</b> Toda la suite corre sobre H2, y
 * el propio Paso 4 de {@code plans/conexiones-y-paginacion.md} lo dice: <i>"el test no puede
 * depender de que H2 cancele igual que MySQL"</i>. Los tests de {@code ConexionesSupervisadasTest}
 * verifican con dobles que se le pida {@code cancel()} a la sentencia correcta — que es la parte que
 * se puede romper por descuido—, pero no que ese {@code cancel()} se traduzca en un {@code KILL
 * QUERY} que efectivamente mate la consulta. Eso sólo lo puede decir MySQL.
 *
 * <p><b>Por qué el smoke manual del plan no alcanzaba.</b> Mantener F5 en Historial de Lavadero
 * <em>no</em> produce una ráfaga: esa pantalla va por {@code RefrescadorPantallas}, cuyo
 * {@code solicitar()} hace {@code temporizador.restart()}, así que el auto-repeat del teclado
 * reinicia la ventana de 150 ms y la lectura dispara <b>una sola vez</b>, al soltar. Y aunque se
 * solaparan, cada lectura tarda milisegundos: terminan antes de que nadie alcance a correr un
 * {@code SHOW PROCESSLIST}. El smoke sirve como no-regresión; discriminar es lo que hace este test,
 * con una consulta que dura lo suficiente como para poder verla morir.
 *
 * <p><b>No es parte de {@code mvn test}.</b> {@link EnabledIfSystemProperty} lo saltea salvo que se
 * pida explícitamente, y su nombre no termina en {@code Test}, así que los patrones por defecto de
 * Surefire tampoco lo alcanzan por accidente:
 * <pre>{@code
 * mvn test -Dtest=CancelacionContraMySQL -Daptium.mysql=true
 * }</pre>
 *
 * <p><b>Es de sólo lectura</b> —lo único que ejecuta es {@code SELECT SLEEP(...)}— pero igual exige
 * host local, por la misma razón que el sembrador: ocupar una conexión durante veinte segundos en el
 * servidor de producción no es gratis, y equivocarse de {@code DB_HOST} es fácil.
 */
@EnabledIfSystemProperty(named = "aptium.mysql", matches = "true")
class CancelacionContraMySQL {

    private static final Logger log = LoggerFactory.getLogger(CancelacionContraMySQL.class);

    /** Cómodamente por debajo del techo de consulta (30 s): lo que mata la consulta tiene que ser
     *  el {@code cancel()}, no el {@code queryTimeout}, o el test probaría otra cosa. */
    private static final int SEGUNDOS_DE_CONSULTA = 20;

    private static final long ESPERA_MAXIMA_MS = 10_000;

    @Test
    void cancelarLaTarea_mataLaConsultaEnElServidor() throws Exception {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        String name = env("DB_NAME", "sistema_empresa");
        String user = env("DB_USER", "root");
        String pass = env("DB_PASS", "root");
        exigirHostLocal(host);

        String url = "jdbc:mysql://" + host + ":" + port + "/" + name
            + "?serverTimezone=UTC&connectionTimeZone=LOCAL&sslMode=REQUIRED";
        // Marca única para reconocer ESTA consulta en el PROCESSLIST sin depender del texto.
        String marca = "aptium-cancelacion-" + UUID.randomUUID();
        String consultaLenta = "SELECT SLEEP(" + SEGUNDOS_DE_CONSULTA + ") /* " + marca + " */";

        TokenTarea token = TokenTarea.nuevo("refresco-historial-lavadero");
        CountDownLatch lanzada  = new CountDownLatch(1);
        CountDownLatch termino  = new CountDownLatch(1);
        AtomicReference<Throwable> loQueLevanto = new AtomicReference<>();

        try (HikariDataSource pool = poolDe(url, user, pass);
             Connection observador = DriverManager.getConnection(url, user, pass)) {

            ConnectionPool.setDataSourceForTesting(pool);
            int permisosAntes = ConnectionPool.permisosDisponibles();

            // Hilo de fondo: hace exactamente lo que hace TareaUI.doInBackground.
            Thread tarea = new Thread(() -> {
                TokenTarea.asociarAlHiloActual(token);
                try (Connection conn = ConnectionPool.getConnection();
                     Statement sentencia = conn.createStatement()) {
                    lanzada.countDown();
                    sentencia.executeQuery(consultaLenta);
                } catch (Throwable t) {
                    loQueLevanto.set(t);
                } finally {
                    TokenTarea.desasociarDelHiloActual();
                    ConexionesSupervisadas.olvidar(token);
                    termino.countDown();
                }
            }, "tarea-de-prueba");
            tarea.setDaemon(true);
            tarea.start();

            assertTrue(lanzada.await(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS), "la consulta no arrancó");
            // Contraprueba: sin esto, un test que no encontrara nunca la consulta pasaría igual.
            assertTrue(esperarA(observador, marca, true),
                "la consulta lenta nunca apareció en el PROCESSLIST: el test no estaría probando nada");
            log.info("Consulta lenta visible en el servidor; cancelando el token '{}'", token);

            long inicio = System.nanoTime();
            ConexionesSupervisadas.cancelarDe(token);

            assertTrue(esperarA(observador, marca, false),
                "la consulta siguió viva en MySQL después de cancelar: el KILL QUERY no llegó");
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            log.info("La consulta desapareció del PROCESSLIST en {} ms", ms);

            assertTrue(termino.await(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS), "la tarea no terminó");
            assertTrue(ms < SEGUNDOS_DE_CONSULTA * 1000L,
                "murió por agotarse el SLEEP, no por la cancelación");
            assertTrue(loQueLevanto.get() != null,
                "una consulta cancelada tiene que levantar; si no, se pintaría un resultado a medias");
            log.info("La sentencia cancelada levantó {}: {}",
                loQueLevanto.get().getClass().getSimpleName(), loQueLevanto.get().getMessage());

            assertEquals(permisosAntes, ConnectionPool.permisosDisponibles(),
                "el permiso del semáforo tiene que volver aunque la consulta muera cancelada");
        } finally {
            ConnectionPool.setDataSourceForTesting(null);
        }
    }

    /** Espera hasta {@link #ESPERA_MAXIMA_MS} a que la consulta marcada esté (o deje de estar). */
    private static boolean esperarA(Connection observador, String marca, boolean presente)
            throws Exception {
        long limite = System.currentTimeMillis() + ESPERA_MAXIMA_MS;
        do {
            if (estaViva(observador, marca) == presente) {
                return true;
            }
            Thread.sleep(25);
        } while (System.currentTimeMillis() < limite);
        return false;
    }

    private static boolean estaViva(Connection observador, String marca) throws Exception {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.PROCESSLIST "
                   + "WHERE INFO LIKE CONCAT('%', ?, '%') AND ID <> CONNECTION_ID()";
        try (PreparedStatement ps = observador.prepareStatement(sql)) {
            ps.setString(1, marca);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static HikariDataSource poolDe(String url, String user, String pass) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(2);
        config.setPoolName("CancelacionPool");
        return new HikariDataSource(config);
    }

    private static void exigirHostLocal(String host) {
        if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
            throw new IllegalStateException(
                "Este test ocupa una conexión durante " + SEGUNDOS_DE_CONSULTA + " s: sólo contra "
                + "un MySQL local. DB_HOST=" + host);
        }
    }

    private static String env(String clave, String porDefecto) {
        String valor = System.getenv(clave);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}
