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
 *
 * <h2>La marca de cancelado</h2>
 *
 * {@code ConexionesSupervisadas.cancelarDe} sólo alcanza a las sentencias <em>ya registradas</em>.
 * Una tarea que todavía espera permiso en {@code ConnectionPool}, o que está entre dos sentencias,
 * no tiene nada que cancelar, y la sentencia siguiente correría entera. La marca cierra ese hueco
 * desde el otro lado: quien cancela la prende <b>antes</b> de recorrer el registro, y quien abre
 * una sentencia la mira <b>después</b> de registrarla. Con ese orden no hay sentencia que se escape
 * de los dos: o ya estaba registrada cuando se recorrió el registro, o ve la marca prendida.
 *
 * <p>Prenderla no hace I/O, así que se prende sincrónicamente en el hilo de la interfaz.
 */
public final class TokenTarea {

    private static final ThreadLocal<TokenTarea> VIGENTE = new ThreadLocal<>();

    private static final AtomicLong SECUENCIA = new AtomicLong();

    private final String descripcion;

    private volatile boolean cancelado;

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

    /** Prende la marca de cancelado. Idempotente y sin I/O: ver el javadoc de la clase. */
    public void marcarCancelado() {
        cancelado = true;
    }

    public boolean estaCancelado() {
        return cancelado;
    }

    /**
     * Lanza {@link TareaCanceladaException} si la tarea fue cancelada.
     *
     * @param momento dónde se detectó, sólo para el mensaje
     */
    void exigirNoCancelado(String momento) throws TareaCanceladaException {
        if (cancelado) {
            throw new TareaCanceladaException(this, momento);
        }
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
