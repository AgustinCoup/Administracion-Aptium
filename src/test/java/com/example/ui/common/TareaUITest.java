package com.example.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
