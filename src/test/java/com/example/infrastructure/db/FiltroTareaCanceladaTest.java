package com.example.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FiltroTareaCanceladaTest {

    private final FiltroTareaCancelada filtro = new FiltroTareaCancelada();

    @AfterEach
    void limpiarHilo() {
        TokenTarea.desasociarDelHiloActual();
    }

    @Test
    @DisplayName("un error logueado por una tarea cancelada no llega a error.log")
    void errorDeTareaCancelada_seDescarta() {
        TokenTarea token = TokenTarea.nuevo("refresco-operativo");
        TokenTarea.asociarAlHiloActual(token);
        token.marcarCancelado();

        assertEquals(FilterReply.DENY, filtro.decide(evento(Level.ERROR)));
    }

    @Test
    @DisplayName("un error de una tarea viva —por ejemplo el techo de 30 s— sí llega")
    void errorDeTareaViva_pasa() {
        TokenTarea.asociarAlHiloActual(TokenTarea.nuevo("refresco-operativo"));

        assertEquals(FilterReply.NEUTRAL, filtro.decide(evento(Level.ERROR)));
    }

    @Test
    @DisplayName("un error fuera de toda tarea (hilo de UI, arranque) sí llega")
    void errorSinTarea_pasa() {
        assertEquals(FilterReply.NEUTRAL, filtro.decide(evento(Level.ERROR)));
    }

    private static ILoggingEvent evento(Level nivel) {
        ILoggingEvent evento = mock(ILoggingEvent.class);
        when(evento.getLevel()).thenReturn(nivel);
        return evento;
    }
}
