package com.example.app.ui;

import com.example.ui.common.TareaUI;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Timer;

/**
 * Único camino por el que un grupo de pantallas se entera de que cambiaron los datos.
 *
 * <p>Quien tiene que refrescarse llama a {@link #solicitar()}; la lectura real se
 * hace una sola vez, en un hilo de fondo, y se reparte a todas las pantallas del
 * grupo dentro del mismo bloque del hilo de UI — así quedan coherentes entre sí,
 * cosa que antes no estaba garantizada porque cada una leía por su cuenta.
 *
 * <p><b>Un refrescador por grupo.</b> No todas las pantallas quieren lo mismo ni
 * en el mismo momento: las operativas necesitan la cola activa en cada guardado,
 * las de consulta necesitan el histórico completo solo cuando el usuario las abre.
 * Por eso el tipo del snapshot es un parámetro: {@link DatosOperativos},
 * {@link HistorialEquipos} e {@link HistorialLotes} usan el mismo mecanismo con
 * disparadores y costos distintos.
 *
 * <p><b>Debounce.</b> Las ráfagas son la norma, no la excepción: una corrección
 * dispara el refresco de equipos <i>y</i> el de cambios aplicados. Un
 * {@link Timer} de Swing no repetitivo las colapsa en una sola lectura. Dispara
 * en el hilo de UI, así que no agrega un modelo de concurrencia más.
 *
 * <p><b>Resultados fuera de orden.</b> Antes de lanzar una lectura se cancela la
 * anterior. La cancelación de {@link TareaUI} es de aplicación: la query vieja
 * termina igual, pero su resultado se descarta en vez de pisar al nuevo. Es el
 * token de generación — sin él, dos refrescos rápidos pueden pintar al revés.
 *
 * @param <T> tipo del snapshot que este grupo de pantallas consume
 */
public class RefrescadorPantallas<T> {

    /** Ventana de coalescencia. Corta para no verse, larga para juntar la ráfaga. */
    public static final int DEBOUNCE_MS = 150;

    private final Supplier<T>         lector;
    private final Consumer<T>         repartir;
    private final Consumer<Throwable> alFallar;
    private final String              nombre;
    private final Timer               temporizador;

    private TareaUI.Ejecucion enVuelo;

    /**
     * @param nombre   identifica el grupo en los logs (p. ej. {@code "refresco-operativo"})
     * @param lector   lee el snapshot del grupo; corre fuera del hilo de UI
     * @param repartir vuelca el snapshot a las pantallas del grupo; corre en el hilo de UI
     * @param alFallar qué mostrar si la lectura falla; corre en el hilo de UI
     */
    public RefrescadorPantallas(String nombre,
                                Supplier<T> lector,
                                Consumer<T> repartir,
                                Consumer<Throwable> alFallar) {
        this(nombre, lector, repartir, alFallar, DEBOUNCE_MS);
    }

    /** Variante con ventana de debounce explícita; pensada para tests. */
    RefrescadorPantallas(String nombre,
                         Supplier<T> lector,
                         Consumer<T> repartir,
                         Consumer<Throwable> alFallar,
                         int debounceMs) {
        this.nombre   = Objects.requireNonNull(nombre, "nombre");
        this.lector   = Objects.requireNonNull(lector, "lector");
        this.repartir = Objects.requireNonNull(repartir, "repartir");
        this.alFallar = Objects.requireNonNull(alFallar, "alFallar");

        this.temporizador = new Timer(debounceMs, e -> refrescarAhora());
        this.temporizador.setRepeats(false);
    }

    /**
     * Pide un refresco. Llamadas seguidas dentro de la ventana de debounce
     * colapsan en una sola lectura, siempre posterior a todas ellas.
     */
    public void solicitar() {
        temporizador.restart();
    }

    private void refrescarAhora() {
        if (enVuelo != null) {
            enVuelo.cancelar();
        }
        enVuelo = TareaUI.<T>nueva()
            .nombre(nombre)
            .leer(lector::get)
            .pintar(repartir)
            .siFalla(alFallar)
            .lanzar();
    }
}
