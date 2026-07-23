package com.example.ui.common;

import java.awt.EventQueue;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Único mecanismo de la aplicación para hacer trabajo fuera del hilo de la interfaz.
 *
 * <p>Separa explícitamente las dos mitades de toda operación de fondo: qué se lee
 * (fuera del hilo de UI) y qué se pinta con lo leído (en el hilo de UI). Reemplaza al
 * patrón de {@code new Thread(...)} + {@code invokeLater} copiado por la app, que era
 * fácil de escribir mal y de escribir a medias — sobre todo omitiendo el manejo de error.
 *
 * <pre>{@code
 * TareaUI.<DatosRefresco>nueva()
 *     .nombre("refresco-pantallas")
 *     .leer(lector::leer)                        // fuera del hilo de UI
 *     .pintar(datos -> aplicar(datos))           // en el hilo de UI
 *     .siFalla(e -> panel.mostrarError(e))       // en el hilo de UI
 *     .antes(()  -> panel.mostrarCargando(true))
 *     .despues(() -> panel.mostrarCargando(false))
 *     .lanzar();
 * }</pre>
 *
 * <p><b>Los errores nunca se pierden:</b> cualquier excepción de {@code leer} (o de
 * {@code pintar}) se loguea a ERROR aunque no se haya declarado {@code siFalla}, y se
 * rutea al manejador en el hilo de UI. {@code pintar} no se ejecuta si hubo fallo.
 *
 * <p><b>Cancelación:</b> {@link #lanzar()} devuelve una {@link Ejecucion} cancelable.
 * La cancelación es de <i>aplicación</i>, no de <i>ejecución</i>: una query JDBC ya
 * lanzada termina igual, pero su resultado se descarta. Una tarea cancelada no ejecuta
 * {@code pintar}, {@code siFalla} ni {@code despues}: quien cancela es responsable de
 * lo que quede en pantalla (típicamente porque lanza otra tarea en su lugar).
 *
 * @param <T> tipo del resultado que viaja de {@code leer} a {@code pintar}
 */
public final class TareaUI<T> {

    private static final Logger log = LoggerFactory.getLogger(TareaUI.class);

    private static final String NOMBRE_POR_DEFECTO = "tarea-ui";

    /** Handle de una tarea lanzada. */
    public interface Ejecucion {
        /** Descarta el resultado de la tarea; nada se pintará. Idempotente. */
        void cancelar();

        boolean estaCancelada();
    }

    private Callable<T>         leer;
    private Consumer<T>         pintar   = resultado -> { };
    private Consumer<Throwable> siFalla;
    private Runnable            antes    = () -> { };
    private Runnable            despues  = () -> { };
    private String              nombre   = NOMBRE_POR_DEFECTO;

    private TareaUI() { }

    public static <T> TareaUI<T> nueva() {
        return new TareaUI<>();
    }

    /** Nombre del hilo mientras corre {@code leer}, para que los logs sean legibles. */
    public TareaUI<T> nombre(String nombre) {
        this.nombre = Objects.requireNonNull(nombre, "nombre no puede ser nulo");
        return this;
    }

    /** Trabajo pesado (BD, archivos). Corre fuera del hilo de UI. Obligatorio. */
    public TareaUI<T> leer(Callable<T> leer) {
        this.leer = Objects.requireNonNull(leer, "leer no puede ser nulo");
        return this;
    }

    /** Vuelca el resultado en la pantalla. Corre en el hilo de UI. */
    public TareaUI<T> pintar(Consumer<T> pintar) {
        this.pintar = Objects.requireNonNull(pintar, "pintar no puede ser nulo");
        return this;
    }

    /** Manejo de error de cara al usuario. Corre en el hilo de UI. */
    public TareaUI<T> siFalla(Consumer<Throwable> siFalla) {
        this.siFalla = Objects.requireNonNull(siFalla, "siFalla no puede ser nulo");
        return this;
    }

    /** Corre en el hilo de UI antes de arrancar (típicamente mostrar el indicador de carga). */
    public TareaUI<T> antes(Runnable antes) {
        this.antes = Objects.requireNonNull(antes, "antes no puede ser nulo");
        return this;
    }

    /** Corre en el hilo de UI al terminar, tanto en éxito como en error. */
    public TareaUI<T> despues(Runnable despues) {
        this.despues = Objects.requireNonNull(despues, "despues no puede ser nulo");
        return this;
    }

    /**
     * Arranca la tarea.
     *
     * @return handle para cancelarla
     * @throws IllegalStateException si no se declaró {@code leer}
     */
    public Ejecucion lanzar() {
        if (leer == null) {
            throw new IllegalStateException("Una TareaUI necesita un leer(...)");
        }

        Handle handle = new Handle();
        SwingWorker<T, Void> worker = crearWorker(handle);
        handle.asociar(worker);

        enHiloUi(antes);
        worker.execute();
        return handle;
    }

    private SwingWorker<T, Void> crearWorker(Handle handle) {
        return new SwingWorker<>() {

            @Override
            protected T doInBackground() throws Exception {
                Thread hilo = Thread.currentThread();
                String nombreOriginal = hilo.getName();
                hilo.setName(nombre);
                try {
                    return leer.call();
                } finally {
                    hilo.setName(nombreOriginal);
                }
            }

            @Override
            protected void done() {
                if (handle.estaCancelada()) {
                    return;
                }
                try {
                    pintar.accept(get());
                } catch (CancellationException e) {
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    manejarFallo(e);
                } catch (ExecutionException e) {
                    manejarFallo(e.getCause() != null ? e.getCause() : e);
                } catch (RuntimeException e) {
                    manejarFallo(e);
                } finally {
                    despues.run();
                }
            }
        };
    }

    private void manejarFallo(Throwable causa) {
        log.error("Fallo en la tarea de fondo '{}'", nombre, causa);
        if (siFalla == null) {
            return;
        }
        try {
            siFalla.accept(causa);
        } catch (RuntimeException e) {
            log.error("El manejador de error de la tarea '{}' también falló", nombre, e);
        }
    }

    private static void enHiloUi(Runnable accion) {
        if (EventQueue.isDispatchThread()) {
            accion.run();
        } else {
            EventQueue.invokeLater(accion);
        }
    }

    private static final class Handle implements Ejecucion {

        private final AtomicBoolean cancelada = new AtomicBoolean(false);
        private volatile SwingWorker<?, ?> worker;

        void asociar(SwingWorker<?, ?> worker) {
            this.worker = worker;
        }

        @Override
        public void cancelar() {
            cancelada.set(true);
            SwingWorker<?, ?> actual = worker;
            if (actual != null) {
                // Sin interrumpir: interrumpir una query JDBC en curso no es confiable.
                // Esto solo evita que arranque si todavía estaba encolada.
                actual.cancel(false);
            }
        }

        @Override
        public boolean estaCancelada() {
            return cancelada.get();
        }
    }
}
