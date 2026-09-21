package com.example.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.infrastructure.db.ConexionesSupervisadas;
import com.example.infrastructure.db.ConnectionPool;
import com.example.infrastructure.db.TokenTarea;
import java.awt.EventQueue;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TareaUITest {

    private static final int TIMEOUT_SEGUNDOS = 10;

    @Test
    @DisplayName("leer corre fuera del hilo de UI y pintar dentro")
    void separaLosDosHilos() throws Exception {
        AtomicBoolean leyoEnHiloUi  = new AtomicBoolean(true);
        AtomicBoolean pintoEnHiloUi = new AtomicBoolean(false);
        AtomicReference<String> recibido = new AtomicReference<>();
        CountDownLatch termino = new CountDownLatch(1);

        TareaUI.<String>nueva()
            .leer(() -> {
                leyoEnHiloUi.set(EventQueue.isDispatchThread());
                return "datos";
            })
            .pintar(valor -> {
                pintoEnHiloUi.set(EventQueue.isDispatchThread());
                recibido.set(valor);
            })
            .despues(termino::countDown)
            .lanzar();

        esperar(termino);

        assertFalse(leyoEnHiloUi.get(), "leer no debe correr en el hilo de UI");
        assertTrue(pintoEnHiloUi.get(), "pintar debe correr en el hilo de UI");
        assertEquals("datos", recibido.get());
    }

    @Test
    @DisplayName("una excepción en leer llega a siFalla, no a pintar")
    void excepcionEnLeerVaASiFalla() throws Exception {
        RuntimeException falla = new IllegalStateException("BD caída");
        AtomicBoolean pinto = new AtomicBoolean(false);
        AtomicBoolean falloEnHiloUi = new AtomicBoolean(false);
        AtomicReference<Throwable> recibida = new AtomicReference<>();
        CountDownLatch termino = new CountDownLatch(1);

        TareaUI.<String>nueva()
            .leer(() -> { throw falla; })
            .pintar(valor -> pinto.set(true))
            .siFalla(e -> {
                falloEnHiloUi.set(EventQueue.isDispatchThread());
                recibida.set(e);
            })
            .despues(termino::countDown)
            .lanzar();

        esperar(termino);

        assertFalse(pinto.get(), "pintar no debe ejecutarse si leer falló");
        assertSame(falla, recibida.get(), "siFalla debe recibir la causa original, no el wrapper");
        assertTrue(falloEnHiloUi.get(), "siFalla debe correr en el hilo de UI");
    }

    @Test
    @DisplayName("antes y despues corren en el camino de éxito")
    void antesYDespuesEnExito() throws Exception {
        AtomicInteger antes = new AtomicInteger();
        CountDownLatch termino = new CountDownLatch(1);

        TareaUI.<String>nueva()
            .antes(antes::incrementAndGet)
            .leer(() -> "ok")
            .despues(termino::countDown)
            .lanzar();

        esperar(termino);
        assertEquals(1, antes.get());
    }

    @Test
    @DisplayName("despues corre también cuando leer falla")
    void despuesEnError() throws Exception {
        CountDownLatch termino = new CountDownLatch(1);

        TareaUI.<String>nueva()
            .leer(() -> { throw new IllegalStateException("falla"); })
            .siFalla(e -> { })
            .despues(termino::countDown)
            .lanzar();

        esperar(termino);
    }

    @Test
    @DisplayName("sin siFalla declarado la excepción no se propaga ni rompe la tarea")
    void sinSiFallaNoExplota() throws Exception {
        AtomicBoolean pinto = new AtomicBoolean(false);
        CountDownLatch termino = new CountDownLatch(1);

        TareaUI.<String>nueva()
            .leer(() -> { throw new IllegalStateException("falla sin manejador"); })
            .pintar(valor -> pinto.set(true))
            .despues(termino::countDown)
            .lanzar();

        esperar(termino);
        assertFalse(pinto.get());
    }

    @Test
    @DisplayName("una tarea cancelada no pinta")
    void canceladaNoPinta() throws Exception {
        CountDownLatch arranco = new CountDownLatch(1);
        CountDownLatch puedeTerminar = new CountDownLatch(1);
        CountDownLatch leyo = new CountDownLatch(1);
        AtomicBoolean pinto = new AtomicBoolean(false);

        TareaUI.Ejecucion ejecucion = TareaUI.<String>nueva()
            .leer(() -> {
                arranco.countDown();
                puedeTerminar.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
                leyo.countDown();
                return "datos viejos";
            })
            .pintar(valor -> pinto.set(true))
            .lanzar();

        // Se cancela con la lectura ya en curso: es el caso real (una query JDBC
        // lanzada no se interrumpe, su resultado se descarta al volver).
        esperar(arranco);
        ejecucion.cancelar();
        puedeTerminar.countDown();
        esperar(leyo);
        vaciarColaDelHiloUi();

        assertTrue(ejecucion.estaCancelada());
        assertFalse(pinto.get(), "un resultado de una tarea cancelada no debe pintarse");
    }

    @Test
    @DisplayName("cancelar una tarea en vuelo cancela su consulta, y fuera del hilo de UI")
    void cancelarCancelaLaSentenciaEnVuelo() throws Exception {
        // Con un doble y no con una consulta real: el test no puede depender de que H2 cancele
        // igual que MySQL. Lo que se verifica es que el token llegue del proxy JDBC al Handle, y
        // que el cancel() —que en Connector/J abre una conexión para mandar el KILL QUERY— no
        // corra en el hilo de la interfaz, que es desde donde se llama a cancelar().
        AtomicReference<String> hiloDelCancel = new AtomicReference<>();
        Statement sentencia = mock(Statement.class);
        doAnswer(invocacion -> {
            hiloDelCancel.set(Thread.currentThread().getName());
            return null;
        }).when(sentencia).cancel();
        Connection real = mock(Connection.class);
        when(real.getAutoCommit()).thenReturn(true);
        when(real.createStatement()).thenReturn(sentencia);

        CountDownLatch consultaEnVuelo = new CountDownLatch(1);
        CountDownLatch puedeTerminar   = new CountDownLatch(1);

        TareaUI.Ejecucion ejecucion = TareaUI.<String>nueva()
            .nombre("refresco-historial-lavadero")
            .leer(() -> {
                try (Connection conn = ConexionesSupervisadas.envolver(real, () -> { })) {
                    conn.createStatement();
                    consultaEnVuelo.countDown();
                    puedeTerminar.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
                }
                return "datos viejos";
            })
            .lanzar();

        esperar(consultaEnVuelo);
        SwingUtilities.invokeAndWait(ejecucion::cancelar);   // como lo hace RefrescadorPantallas

        verify(sentencia, timeout(TIMEOUT_SEGUNDOS * 1000L)).cancel();
        assertEquals("cancelador-sql", hiloDelCancel.get(),
            "el cancel() hace I/O: no puede correr en el hilo de la interfaz");
        puedeTerminar.countDown();
    }

    @Test
    @DisplayName("cancelar prende la marca del token en el acto, sin esperar al cancelador-sql")
    void cancelarPrendeLaMarcaSincronicamente() throws Exception {
        // La mitad de la carrera que cierra la marca: tiene que estar prendida ANTES de que
        // cancelarDe recorra el registro, y eso sólo se garantiza si la prende el mismo cancelar().
        AtomicReference<TokenTarea> tokenDeLaTarea = new AtomicReference<>();
        CountDownLatch arranco       = new CountDownLatch(1);
        CountDownLatch puedeTerminar = new CountDownLatch(1);

        TareaUI.Ejecucion ejecucion = TareaUI.<String>nueva()
            .leer(() -> {
                tokenDeLaTarea.set(TokenTarea.vigente());
                arranco.countDown();
                puedeTerminar.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
                return "datos";
            })
            .lanzar();

        esperar(arranco);
        assertFalse(tokenDeLaTarea.get().estaCancelado());
        AtomicBoolean marcadoAlVolver = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> {
            ejecucion.cancelar();
            marcadoAlVolver.set(tokenDeLaTarea.get().estaCancelado());
        });
        puedeTerminar.countDown();

        assertTrue(marcadoAlVolver.get(), "la marca tiene que estar prendida al volver de cancelar()");
    }

    @Test
    @DisplayName("la cancelación no dispara siFalla ni pintar")
    void canceladaNoDisparaSiFalla() throws Exception {
        CountDownLatch arranco       = new CountDownLatch(1);
        CountDownLatch puedeTerminar = new CountDownLatch(1);
        CountDownLatch leyo          = new CountDownLatch(1);
        AtomicBoolean pinto  = new AtomicBoolean(false);
        AtomicBoolean fallo  = new AtomicBoolean(false);

        TareaUI.Ejecucion ejecucion = TareaUI.<String>nueva()
            .leer(() -> {
                arranco.countDown();
                puedeTerminar.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
                leyo.countDown();
                // Es lo que tira una consulta cancelada de verdad: no puede llegar al usuario
                // como un cartel de error por un refresco que él mismo reemplazó.
                throw new SQLException("Statement cancelled due to client request");
            })
            .pintar(valor -> pinto.set(true))
            .siFalla(e -> fallo.set(true))
            .lanzar();

        esperar(arranco);
        ejecucion.cancelar();
        puedeTerminar.countDown();
        esperar(leyo);
        vaciarColaDelHiloUi();

        assertFalse(pinto.get(), "una tarea cancelada no pinta");
        assertFalse(fallo.get(), "el error de una consulta cancelada no puede llegar al usuario");
    }

    @Test
    @DisplayName("una tarea que no toca la base no consume un permiso de conexión")
    void tareaSinJdbcNoTomaPermiso() throws Exception {
        // Anti-patrón A14: ajustes-descargar-actualizacion baja el fat JAR y tarda minutos. Un
        // techo alrededor de leer la haría retener un permiso de conexión que no usa mientras
        // registrar-estado-confirmar espera.
        int antes = ConnectionPool.permisosDisponibles();
        AtomicInteger durante = new AtomicInteger();
        CountDownLatch termino = new CountDownLatch(1);

        TareaUI.<String>nueva()
            .nombre("ajustes-descargar-actualizacion")
            .leer(() -> {
                durante.set(ConnectionPool.permisosDisponibles());
                return "descargado";
            })
            .despues(termino::countDown)
            .lanzar();

        esperar(termino);

        assertEquals(antes, durante.get(), "una tarea sin JDBC no puede consumir permisos");
        assertEquals(antes, ConnectionPool.permisosDisponibles());
    }

    @Test
    @DisplayName("una lectura que supera el umbral se loguea a WARN; una normal, a INFO")
    void lecturaLenta_vaAWarn() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(TareaUI.class);
        ListAppender<ILoggingEvent> eventos = new ListAppender<>();
        eventos.start();
        logger.addAppender(eventos);
        try {
            TareaUI.umbralLecturaLentaMs = 0;   // cualquier lectura es "lenta"
            lanzarYEsperar("lectura-lenta");
            TareaUI.umbralLecturaLentaMs = Long.MAX_VALUE;
            lanzarYEsperar("lectura-normal");
        } finally {
            TareaUI.umbralLecturaLentaMs = TareaUI.UMBRAL_LECTURA_LENTA_MS;
            logger.detachAppender(eventos);
        }

        assertEquals(Level.WARN, nivelDeLaLectura(eventos, "lectura-lenta"));
        assertEquals(Level.INFO, nivelDeLaLectura(eventos, "lectura-normal"));
    }

    private static void lanzarYEsperar(String nombre) throws InterruptedException {
        CountDownLatch termino = new CountDownLatch(1);
        TareaUI.<String>nueva().nombre(nombre).leer(() -> "x").despues(termino::countDown).lanzar();
        esperar(termino);
    }

    private static Level nivelDeLaLectura(ListAppender<ILoggingEvent> eventos, String nombre) {
        return eventos.list.stream()
            .filter(e -> e.getFormattedMessage().startsWith("Tarea '" + nombre + "' leyó en"))
            .map(ILoggingEvent::getLevel)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no se logueó la lectura de " + nombre));
    }

    @Test
    @DisplayName("lanzar sin leer es un error de programación, no un fallo silencioso")
    void lanzarSinLeerFalla() {
        assertThrows(IllegalStateException.class, () -> TareaUI.nueva().lanzar());
    }

    private static void esperar(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS), "la tarea no terminó a tiempo");
    }

    /** Deja que el hilo de UI procese todo lo encolado, incluido el done() del worker. */
    private static void vaciarColaDelHiloUi() throws Exception {
        for (int i = 0; i < 5; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(20);
        }
    }
}
