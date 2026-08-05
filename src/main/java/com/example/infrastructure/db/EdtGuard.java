package com.example.infrastructure.db;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detecta accesos a la base de datos hechos desde el hilo de la interfaz.
 *
 * <p>Bloquear el hilo de UI con una query congela la aplicación entera. Este guard
 * convierte esa clase de error, que solo se nota como "la app se traba", en una
 * advertencia con stack trace apuntando al llamador culpable.
 *
 * <p>La capa de infraestructura no conoce Swing: quién es el hilo de UI se inyecta
 * como {@link BooleanSupplier} desde el arranque de la aplicación
 * ({@code EdtGuard.setDetectorHiloUi(EventQueue::isDispatchThread)}). Sin detector
 * inyectado el guard nunca dispara, así que los tests y cualquier uso headless no
 * se ven afectados.
 *
 * <p>Con la propiedad de sistema {@value #PROP_ESTRICTO} en {@code true} la violación
 * pasa de advertencia a excepción. Útil mientras se verifica un refactor; no debe
 * activarse en producción.
 */
public final class EdtGuard {

    private static final Logger log = LoggerFactory.getLogger(EdtGuard.class);

    /** Propiedad de sistema que convierte la advertencia en excepción. */
    public static final String PROP_ESTRICTO = "aptium.edt.strict";

    private static final String MENSAJE =
        "Acceso a base de datos desde el hilo de la UI. Debe ejecutarse en fondo (TareaUI).";

    /** Nunca detecta hasta que el arranque inyecte el detector real. */
    private static final BooleanSupplier SIN_DETECTOR = () -> false;

    private static volatile BooleanSupplier detectorHiloUi = SIN_DETECTOR;

    private EdtGuard() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

    /**
     * Instala el detector de hilo de UI. Se llama una vez desde el arranque.
     *
     * @param detector devuelve true si el hilo actual es el de la interfaz
     */
    public static void setDetectorHiloUi(BooleanSupplier detector) {
        detectorHiloUi = Objects.requireNonNull(detector, "detector no puede ser nulo");
    }

    /** Restablece el estado inicial (sin detector). Pensado para tests. */
    public static void resetear() {
        detectorHiloUi = SIN_DETECTOR;
    }

    /**
     * Verifica que el hilo actual no sea el de la interfaz.
     *
     * @throws IllegalStateException si lo es y el modo estricto está activo
     */
    public static void verificarFueraDelHiloUi() {
        if (!detectorHiloUi.getAsBoolean()) {
            return;
        }
        if (Boolean.getBoolean(PROP_ESTRICTO)) {
            throw new IllegalStateException(MENSAJE);
        }
        log.warn(MENSAJE, new Throwable("Origen del acceso a BD en el hilo de UI"));
    }
}
