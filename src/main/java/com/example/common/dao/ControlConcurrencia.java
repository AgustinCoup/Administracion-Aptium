package com.example.common.dao;

import com.example.common.exception.ConflictoConcurrenciaException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduce el resultado de un {@code executeUpdate()} guardado en un conflicto de concurrencia.
 *
 * <p>El patrón que este helper cierra es siempre el mismo: la escritura lleva en el
 * {@code WHERE} una condición que sólo es verdadera si nadie tocó la fila desde que la pantalla
 * la leyó, y el número de filas afectadas es la respuesta a "¿seguía como la viste?".</p>
 *
 * <pre>{@code
 * ps.setString(1, nuevoEstado);
 * ps.setInt(2, materialId);
 * ps.setString(3, estadoQueVioLaPantalla);   // ← la guarda
 * ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Mensajes.CONFLICTO_MATERIAL);
 * }</pre>
 *
 * <p>Existe para que las escrituras protegidas no inventen cada una su propio mensaje ni su
 * propia interpretación del {@code 0}. No abre conexiones ni ejecuta SQL: recibe el número que
 * el DAO ya tiene en la mano.</p>
 */
public final class ControlConcurrencia {

    private static final Logger log = LoggerFactory.getLogger(ControlConcurrencia.class);

    private ControlConcurrencia() {}

    /**
     * Exige que una escritura guardada por PK haya tocado exactamente una fila.
     *
     * <p>{@code 0} significa que la guarda no matcheó: la fila cambió, o desapareció, entre la
     * lectura y la escritura. Se lanza {@link ConflictoConcurrenciaException} para que la
     * transacción entera se revierta.</p>
     *
     * <p>Más de una fila <b>no</b> es un conflicto, pero es un bug: una guarda por clave
     * primaria jamás debería alcanzar dos filas. Se registra un {@code warn} — la condición
     * está mal escrita y hay que enterarse — y se deja seguir, porque abortar acá revertiría
     * una operación que sí se aplicó.</p>
     *
     * @param filasAfectadas lo que devolvió {@code executeUpdate()}
     * @param mensajeUsuario texto para el operador; tiene que decir qué cambió y qué hacer
     * @throws ConflictoConcurrenciaException si {@code filasAfectadas == 0}
     */
    public static void exigirFilaAfectada(int filasAfectadas, String mensajeUsuario) {
        if (filasAfectadas == 0) {
            throw new ConflictoConcurrenciaException(mensajeUsuario);
        }
        if (filasAfectadas > 1) {
            log.warn("Una guarda de concurrencia por PK afectó {} filas; la condición del WHERE "
                    + "está mal escrita. Mensaje asociado: {}", filasAfectadas, mensajeUsuario);
        }
    }

    /**
     * Variante para escrituras en batch, donde se sabe de antemano cuántas filas tienen que
     * moverse.
     *
     * <p>Cualquier desvío es un conflicto, en los dos sentidos: menos filas significa que
     * alguna guarda no matcheó, y más filas significa que el batch alcanzó algo que no era
     * suyo. En un batch no se puede saber <em>cuál</em> de las filas falló, así que la
     * operación se aborta completa y el operador rehace con datos frescos.</p>
     *
     * @param esperadas      cuántas filas tenía que tocar el batch
     * @param reales         la suma de lo que devolvió {@code executeBatch()}
     * @param mensajeUsuario texto para el operador
     * @throws ConflictoConcurrenciaException si {@code reales != esperadas}
     */
    public static void exigirFilasAfectadas(int esperadas, int reales, String mensajeUsuario) {
        if (reales != esperadas) {
            log.warn("Batch guardado por concurrencia: se esperaban {} filas y se afectaron {}.",
                    esperadas, reales);
            throw new ConflictoConcurrenciaException(mensajeUsuario);
        }
    }
}
