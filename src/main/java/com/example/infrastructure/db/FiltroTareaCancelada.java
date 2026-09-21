package com.example.infrastructure.db;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Saca de {@code error.log} los errores que loguea una tarea de fondo <b>ya cancelada</b>.
 *
 * <p>Cancelar una tarea hace que su JDBC levante: {@link TareaCanceladaException} si la marca la
 * frenó antes de tocar el servidor, o {@code MySQLStatementCancelledException} si el
 * {@code KILL QUERY} la mató en vuelo. Los DAOs hacen {@code log.error} con stack antes de envolver
 * cualquier {@code SQLException}, así que cada F5 que reemplaza a otro escribía un error falso. El
 * resultado de esa tarea ya se descarta ({@code TareaUI.done()} no llama a {@code siFalla}); su
 * error tampoco le dice nada a quien lee {@code error.log}.
 *
 * <p><b>Por qué acá y no en los DAOs:</b> son ~120 {@code catch}, y el DAO que se escriba dentro de
 * un año no se va a acordar. Mismo argumento que {@link ConexionesSupervisadas}.
 *
 * <p><b>El criterio es el token del hilo, no la clase de la excepción.</b> Cubre las dos
 * excepciones de arriba sin nombrar la del driver, y no confunde el techo de consulta (30 s) —que sí
 * es un problema y sí tiene que llegar a {@code error.log}— con una cancelación: una tarea que
 * nadie canceló no tiene la marca prendida. El costo aceptado es que un error <em>genuino</em> de
 * una tarea ya cancelada tampoco llega a {@code error.log}.
 *
 * <p>⚠️ Depende de que el appender sea <b>sincrónico</b>: el filtro corre en el hilo que loguea, que
 * es el de la tarea. Detrás de un {@code AsyncAppender} correría en otro hilo, sin token, y no
 * filtraría nada. {@code app.log} no lleva el filtro: ahí la cancelación sigue a la vista.
 */
public class FiltroTareaCancelada extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent evento) {
        if (!evento.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return FilterReply.NEUTRAL;
        }
        TokenTarea token = TokenTarea.vigente();
        return token != null && token.estaCancelado() ? FilterReply.DENY : FilterReply.NEUTRAL;
    }
}
