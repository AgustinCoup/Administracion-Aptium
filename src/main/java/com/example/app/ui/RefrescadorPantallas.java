package com.example.app.ui;

import com.example.ui.common.TareaUI;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Timer;

/**
 * Único camino por el que las pantallas se enteran de que cambiaron los datos.
 *
 * <p>Cada guardado dispara {@link #solicitar()}; el refresco real se hace una
 * sola vez, en un hilo de fondo, y se reparte a todas las pantallas dentro del
 * mismo bloque del hilo de UI — así quedan coherentes entre sí, cosa que antes
 * no estaba garantizada porque cada una leía por su cuenta.
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
 */
public class RefrescadorPantallas {

    /** Ventana de coalescencia. Corta para no verse, larga para juntar la ráfaga. */
    public static final int DEBOUNCE_MS = 150;

    private final LectorDatosRefresco     lector;
    private final Consumer<DatosRefresco> repartir;
    private final Consumer<Throwable>     alFallar;
    private final Timer                   temporizador;

    private TareaUI.Ejecucion enVuelo;

    /**
     * @param lector   lee el snapshot completo; corre fuera del hilo de UI
     * @param repartir vuelca el snapshot a todas las pantallas; corre en el hilo de UI
     * @param alFallar qué mostrar si la lectura falla; corre en el hilo de UI
     */
    public RefrescadorPantallas(LectorDatosRefresco lector,
                                Consumer<DatosRefresco> repartir,
                                Consumer<Throwable> alFallar) {
        this(lector, repartir, alFallar, DEBOUNCE_MS);
    }

    /** Variante con ventana de debounce explícita; pensada para tests. */
    RefrescadorPantallas(LectorDatosRefresco lector,
                         Consumer<DatosRefresco> repartir,
                         Consumer<Throwable> alFallar,
                         int debounceMs) {
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
        enVuelo = TareaUI.<DatosRefresco>nueva()
            .nombre("refresco-pantallas")
            .leer(lector::leer)
            .pintar(repartir)
            .siFalla(alFallar)
            .lanzar();
    }
}
