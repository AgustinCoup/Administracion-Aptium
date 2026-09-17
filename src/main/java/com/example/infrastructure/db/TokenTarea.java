package com.example.infrastructure.db;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Identidad opaca de una tarea de fondo, y el {@link ThreadLocal} que la transporta hasta el
 * JDBC que esa tarea ejecuta.
 *
 * <p><b>Por qué una identidad propia y no el {@link Thread}.</b> {@code SwingWorker} corre sobre
 * un pool fijo de diez hilos que <em>reutiliza</em>, y
 * {@code RefrescadorPantallas.refrescarAhora()} cancela la ejecución anterior de forma
 * incondicional, aunque ya haya terminado (nunca pone el campo en {@code null}). Si el registro de
 * sentencias de {@link ConexionesSupervisadas} estuviera indexado por hilo, cancelar una tarea
 * muerta mataría la consulta <em>viva</em> de otra pantalla que heredó ese mismo hilo. Con un token
 * por tarea eso no puede pasar: el token de la tarea terminada ya no tiene sentencias asociadas.
 *
 * <p>La identidad es la del objeto — no se redefinen {@code equals} ni {@code hashCode} a
 * propósito: dos tareas distintas nunca son el mismo token aunque tengan el mismo nombre.
 *
 * <p>Vive en {@code infrastructure.db} y no en {@code ui.common} porque quien lo <b>lee</b> es el
 * proxy JDBC; {@code TareaUI} sólo lo crea, lo asocia al hilo y lo saca.
 */
public final class TokenTarea {

    private static final ThreadLocal<TokenTarea> VIGENTE = new ThreadLocal<>();

    private static final AtomicLong SECUENCIA = new AtomicLong();

    private final String descripcion;

    private TokenTarea(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * Crea un token nuevo, distinto de cualquier otro.
     *
     * @param nombreTarea nombre de la tarea, sólo para que los logs sean legibles
     */
    public static TokenTarea nuevo(String nombreTarea) {
        return new TokenTarea(nombreTarea + "#" + SECUENCIA.incrementAndGet());
    }

    /** Marca este token como el de la tarea que corre en el hilo actual. */
    public static void asociarAlHiloActual(TokenTarea token) {
        VIGENTE.set(token);
    }

    /**
     * Quita la marca del hilo actual. Obligatorio en un {@code finally}: el hilo vuelve al pool de
     * {@code SwingWorker} y lo va a reutilizar otra tarea.
     */
    public static void desasociarDelHiloActual() {
        VIGENTE.remove();
    }

    /** El token de la tarea que corre en este hilo, o {@code null} si el hilo no es de una tarea. */
    public static TokenTarea vigente() {
        return VIGENTE.get();
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
