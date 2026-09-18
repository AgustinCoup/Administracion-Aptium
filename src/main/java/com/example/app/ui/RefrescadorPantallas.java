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
 * Por eso el tipo del snapshot es un parámetro: {@link DatosOperativos} y
 * {@link HistorialLotes} usan el mismo mecanismo con disparadores y costos
 * distintos.
 *
 * <p><b>Y desde la paginación, ese tipo no siempre es un snapshot completo.</b> Las
 * pantallas paginadas leen una {@link com.example.common.paginacion.Pagina} —o un
 * par de ellas—, con el filtro y la página que el controller publicó. El mecanismo
 * es el mismo; lo que cambia es que el lector ya no dice "traé todo" sino "traé
 * esto", y por eso esas pantallas no comparten grupo con nadie: dos pantallas con
 * filtros propios no pueden repartirse una misma lectura.
 *
 * <p><b>Debounce.</b> Las ráfagas son la norma, no la excepción: una corrección
 * dispara el refresco de equipos <i>y</i> el de cambios aplicados. Un
 * {@link Timer} de Swing no repetitivo las colapsa en una sola lectura. Dispara
 * en el hilo de UI, así que no agrega un modelo de concurrencia más.
 *
 * <p><b>Resultados fuera de orden.</b> Al pedir una lectura se cancela la
 * anterior: su resultado se descarta en vez de pisar al nuevo. Es el token de
 * generación — sin él, dos refrescos rápidos pueden pintar al revés.
 *
 * <p><b>Y se cancela en {@link #solicitar()}, no cuando el temporizador dispara.</b> Es la
 * diferencia entre descartar la lectura vieja y descartarla <em>a tiempo</em>, y para las pantallas
 * paginadas no es un detalle. El controller publica su consulta nueva y llama a {@code solicitar()}
 * seguido, los dos en el hilo de UI; si la cancelación esperara los 150 ms del debounce, una lectura
 * de la consulta <em>anterior</em> que termine dentro de esa ventana todavía correría su
 * {@code pintar} — y {@code pintar} arrastra el total leído a la consulta actual, que ya es otra.
 * El resultado es un total que no corresponde al filtro que se está mirando y que, por ser un total
 * ya conocido, <b>nunca se vuelve a contar</b>: páginas fantasma, o filas inalcanzables, de forma
 * permanente. Cancelando acá no hay ventana: {@code done()} de {@link TareaUI} también corre en el
 * hilo de UI, así que o ya pintó —con la consulta que le correspondía— o ve la cancelación y no
 * pinta.
 *
 * <p>La cancelación de {@link TareaUI} <b>también cancela la consulta en el servidor</b>. Importa
 * acá más que en ningún otro lado, porque se cancela {@code enVuelo} de forma
 * incondicional, aunque esa tarea ya haya terminado: el campo nunca se pone en {@code null}. Eso es
 * seguro porque el registro de sentencias de {@code ConexionesSupervisadas} va por token de tarea y
 * no por hilo — con un registro por hilo, cancelar acá una tarea muerta mataría la consulta viva de
 * otra pantalla que heredó ese hilo del pool de {@code SwingWorker}.
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
        // Acá y no en refrescarAhora(): ver "Resultados fuera de orden" en el javadoc de la clase.
        // Esperar el debounce para cancelar deja pintar a una lectura cuya consulta ya no es la
        // que la pantalla está mirando.
        if (enVuelo != null) {
            enVuelo.cancelar();
        }
        temporizador.restart();
    }

    private void refrescarAhora() {
        enVuelo = TareaUI.<T>nueva()
            .nombre(nombre)
            .leer(lector::get)
            .pintar(repartir)
            .siFalla(alFallar)
            .lanzar();
    }
}
