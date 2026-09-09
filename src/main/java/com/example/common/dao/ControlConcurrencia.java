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
     * Variante para escrituras de varias filas, donde se sabe de antemano cuántas tienen que
     * moverse.
     *
     * <p>Cualquier desvío es un conflicto, en los dos sentidos: menos filas significa que
     * alguna guarda no matcheó, y más filas significa que se alcanzó algo que no era suyo. No se
     * puede saber <em>cuál</em> de las filas falló, así que la operación se aborta completa y el
     * operador rehace con datos frescos.</p>
     *
     * <p><b>El total tiene que venir de {@code executeUpdate()}, no de {@code executeBatch()}</b>:
     * un batch puede devolver {@code SUCCESS_NO_INFO} ({@code -2}) por sentencia cuando el driver
     * no sabe cuántas filas tocó, y sumar eso da un total que no significa nada.</p>
     *
     * @param esperadas      cuántas filas tenía que tocar la operación
     * @param reales         la suma de lo que devolvieron los {@code executeUpdate()}
     * @param mensajeUsuario texto para el operador
     * @throws ConflictoConcurrenciaException si {@code reales != esperadas}
     */
    /**
     * ¿La base cortó esta transacción porque otro la tenía trabada?
     *
     * <p>Existe porque <b>no alcanza con {@code catch (SQLTransactionRollbackException)}</b>, que
     * es la trampa obvia: el driver de MySQL mapea a ese tipo únicamente los {@code SQLSTATE
     * 40xxx}, o sea el <em>deadlock</em> (error 1213). El <em>lock wait timeout</em> (error 1205)
     * viaja con {@code SQLSTATE HY000} y llega como una {@link SQLException} pelada,
     * indistinguible de un error técnico. Y con guardas que <b>bloquean y esperan</b> —los
     * {@code SELECT … FOR UPDATE} del lavadero— el timeout es el desenlace <b>más</b> probable de
     * los dos: para que haya deadlock hace falta un ciclo, para que haya timeout alcanza con que
     * el otro tarde.</p>
     *
     * <p>Quien la usa tiene que traducir el {@code true} a un {@link ConflictoConcurrenciaException}
     * con el mensaje de su flujo: para el operador es "alguien se te adelantó", no "error de base
     * de datos". Nada quedó escrito — la transacción ya se revirtió.</p>
     *
     * @param e lo que salió de la escritura
     */
    public static boolean esContencionDeLock(java.sql.SQLException e) {
        return e instanceof java.sql.SQLTransactionRollbackException  // deadlock: SQLSTATE 40xxx
            || e.getErrorCode() == MYSQL_LOCK_WAIT_TIMEOUT
            || e.getErrorCode() == H2_LOCK_TIMEOUT;
    }

    /** {@code ER_LOCK_WAIT_TIMEOUT} — SQLSTATE HY000, que el driver no mapea a ningún subtipo. */
    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;

    /** {@code LOCK_TIMEOUT_1} de H2, para que la traducción sea la misma en los dos motores. */
    private static final int H2_LOCK_TIMEOUT = 50200;

    public static void exigirFilasAfectadas(int esperadas, int reales, String mensajeUsuario) {
        if (reales != esperadas) {
            log.warn("Batch guardado por concurrencia: se esperaban {} filas y se afectaron {}.",
                    esperadas, reales);
            throw new ConflictoConcurrenciaException(mensajeUsuario);
        }
    }
}
