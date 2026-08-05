package com.example.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El refrescador es agnóstico al tipo de snapshot — lo comparten los tres grupos
 * de pantallas — así que se prueba con {@code String}: lo que importa es en qué
 * hilo corre cada mitad y qué resultado termina pintándose.
 */
class RefrescadorPantallasTest {

    private static final int TIMEOUT_SEGUNDOS = 10;
    private static final int DEBOUNCE_TEST_MS = 60;

    @Test
    @DisplayName("una ráfaga de solicitudes colapsa en una sola lectura")
    void solicitar_colapsaLaRafaga() throws Exception {
        AtomicInteger lecturas = new AtomicInteger();
        CountDownLatch pinto = new CountDownLatch(1);

        RefrescadorPantallas<String> refrescador = refrescador(
            () -> { lecturas.incrementAndGet(); return "datos"; },
            datos -> pinto.countDown(),
            e -> { });

        // Cinco guardados seguidos, como pasa con una corrección que notifica dos veces.
        for (int i = 0; i < 5; i++) refrescador.solicitar();

        esperar(pinto);
        Thread.sleep(DEBOUNCE_TEST_MS * 3L);

        assertEquals(1, lecturas.get(), "cinco solicitudes seguidas deben leer una sola vez");
    }

    @Test
    @DisplayName("el snapshot se reparte en el hilo de UI")
    void pintar_corrreEnElHiloDeUi() throws Exception {
        AtomicBoolean enHiloUi = new AtomicBoolean(false);
        CountDownLatch pinto = new CountDownLatch(1);

        refrescador(() -> "datos",
                    datos -> { enHiloUi.set(EventQueue.isDispatchThread()); pinto.countDown(); },
                    e -> { })
            .solicitar();

        esperar(pinto);
        assertTrue(enHiloUi.get());
    }

    @Test
    @DisplayName("la lectura no corre en el hilo de UI")
    void leer_noCorreEnElHiloDeUi() throws Exception {
        AtomicBoolean enHiloUi = new AtomicBoolean(true);
        CountDownLatch pinto = new CountDownLatch(1);

        refrescador(() -> { enHiloUi.set(EventQueue.isDispatchThread()); return "datos"; },
                    datos -> pinto.countDown(),
                    e -> { })
            .solicitar();

        esperar(pinto);
        assertTrue(!enHiloUi.get(), "la lectura debe hacerse en fondo");
    }

    @Test
    @DisplayName("un fallo en la lectura llega al manejador y no se pierde")
    void fallo_llegaAlManejador() throws Exception {
        RuntimeException falla = new IllegalStateException("BD caída");
        AtomicReference<Throwable> recibida = new AtomicReference<>();
        CountDownLatch fallo = new CountDownLatch(1);
        AtomicBoolean pinto = new AtomicBoolean(false);

        refrescador(() -> { throw falla; },
                    datos -> pinto.set(true),
                    e -> { recibida.set(e); fallo.countDown(); })
            .solicitar();

        esperar(fallo);
        assertEquals(falla, recibida.get());
        assertTrue(!pinto.get(), "no debe pintarse nada si la lectura falló");
    }

    @Test
    @DisplayName("un resultado viejo no pisa a uno nuevo")
    void refrescoViejo_noPisaAlNuevo() throws Exception {
        CountDownLatch primeraArranco  = new CountDownLatch(1);
        CountDownLatch primeraContinua = new CountDownLatch(1);
        AtomicInteger lecturas = new AtomicInteger();
        AtomicInteger pintados = new AtomicInteger();
        AtomicReference<String> ultimoPintado = new AtomicReference<>();
        CountDownLatch pinto = new CountDownLatch(1);

        Supplier<String> lector = () -> {
            if (lecturas.incrementAndGet() == 1) {
                primeraArranco.countDown();
                try {
                    primeraContinua.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "vieja";
            }
            return "nueva";
        };

        RefrescadorPantallas<String> refrescador = new RefrescadorPantallas<>(
            "test",
            lector,
            datos -> { pintados.incrementAndGet(); ultimoPintado.set(datos); pinto.countDown(); },
            e -> { },
            DEBOUNCE_TEST_MS);

        refrescador.solicitar();
        esperar(primeraArranco);

        // Llega un guardado nuevo mientras la primera lectura sigue colgada.
        refrescador.solicitar();
        esperar(pinto);

        // La primera termina tarde: su resultado ya no vale y no debe pintarse.
        primeraContinua.countDown();
        vaciarColaDelHiloUi();

        assertEquals(2, lecturas.get(), "las dos lecturas llegan a ejecutarse");
        assertEquals(1, pintados.get(), "el resultado viejo se descarta, no se pinta");
        assertEquals("nueva", ultimoPintado.get(), "lo pintado es la lectura nueva");
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static RefrescadorPantallas<String> refrescador(Supplier<String> lector,
                                                            Consumer<String> repartir,
                                                            Consumer<Throwable> alFallar) {
        return new RefrescadorPantallas<>("test", lector, repartir, alFallar, DEBOUNCE_TEST_MS);
    }

    private static void esperar(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS), "el refresco no terminó a tiempo");
    }

    private static void vaciarColaDelHiloUi() throws Exception {
        for (int i = 0; i < 5; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(20);
        }
    }
}
