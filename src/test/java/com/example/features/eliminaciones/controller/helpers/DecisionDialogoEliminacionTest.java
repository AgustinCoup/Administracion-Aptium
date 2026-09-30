package com.example.features.eliminaciones.controller.helpers;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.eliminacion.Bloqueo;
import com.example.common.eliminacion.EliminacionBloqueadaException;
import com.example.common.eliminacion.IngresoAEliminar;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import com.example.common.exception.PasswordIncorrectaException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.exception.ValidationException;
import com.example.features.eliminaciones.controller.helpers.DecisionDialogoEliminacion.Paso;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionDialogoEliminacionTest {

    private static ResumenEquipo resumen(List<Bloqueo> bloqueos) {
        return new ResumenEquipo(new IngresoAEliminar(ModuloIngreso.OTROS, 5), "Cliente", null, null,
            null, "NUEVO", 1, List.of(), bloqueos, List.of());
    }

    // ── siguientePaso ────────────────────────────────────────────────────────

    @Test
    void siguientePaso_conBloqueos_muestraBloqueosYNoPidePassword() {
        Paso paso = DecisionDialogoEliminacion.siguientePaso(
            resumen(List.of(new Bloqueo.LoteEnCurso("L-2"))), true);

        Paso.MostrarBloqueos m = assertInstanceOf(Paso.MostrarBloqueos.class, paso);
        assertTrue(m.texto().contains(String.format(Mensajes.ELIMINAR_BLOQUEO_LOTE_EN_CURSO, "L-2")));
    }

    @Test
    void siguientePaso_sinBloqueos_pideConfirmacionConElTextoDelResumen() {
        ResumenEquipo r = resumen(List.of());

        Paso.PedirConfirmacion p = assertInstanceOf(Paso.PedirConfirmacion.class,
            DecisionDialogoEliminacion.siguientePaso(r, false));

        assertEquals(TextoEliminacion.confirmacion(r), p.texto());
        assertFalse(p.avisoPasswordInicial());
    }

    @Test
    void siguientePaso_passwordInicial_pideConfirmacionConAviso() {
        Paso.PedirConfirmacion p = assertInstanceOf(Paso.PedirConfirmacion.class,
            DecisionDialogoEliminacion.siguientePaso(resumen(List.of()), true));

        assertTrue(p.avisoPasswordInicial());
    }

    // ── trasError ────────────────────────────────────────────────────────────

    @Test
    void passwordIncorrecta_conservaElMotivo() {
        Paso paso = DecisionDialogoEliminacion.trasError(
            new PasswordIncorrectaException(Mensajes.PASSWORD_INCORRECTA), "lo cargué dos veces");

        Paso.ReabrirDialogo r = assertInstanceOf(Paso.ReabrirDialogo.class, paso);
        assertEquals("lo cargué dos veces", r.motivo());
        assertEquals(Mensajes.PASSWORD_INCORRECTA, r.mensaje());
    }

    @Test
    void passwordIncorrecta_conMotivoNulo_reabreConMotivoVacio() {
        Paso.ReabrirDialogo r = assertInstanceOf(Paso.ReabrirDialogo.class,
            DecisionDialogoEliminacion.trasError(new PasswordIncorrectaException("x"), null));

        assertEquals("", r.motivo());
    }

    @Test
    void validacion_reabreConservandoElMotivoYJuntandoLosErrores() {
        ValidationException e = ValidationException.builder()
            .addError("uno").addError("dos").build();

        Paso.ReabrirDialogo r = assertInstanceOf(Paso.ReabrirDialogo.class,
            DecisionDialogoEliminacion.trasError(e, "motivo largo"));

        assertEquals("motivo largo", r.motivo());
        assertEquals("uno\ndos", r.mensaje());
    }

    @Test
    void bloqueada_muestraBloqueosConElTextoDeLaExcepcion() {
        EliminacionBloqueadaException e = new EliminacionBloqueadaException(
            List.of(new Bloqueo.CicloEnCurso(2)));

        Paso.MostrarBloqueos m = assertInstanceOf(Paso.MostrarBloqueos.class,
            DecisionDialogoEliminacion.trasError(e, "motivo"));

        assertEquals(e.getMessage(), m.texto());
    }

    @Test
    void conflicto_avisaYRecarga() {
        Paso.AvisarYRecargar a = assertInstanceOf(Paso.AvisarYRecargar.class,
            DecisionDialogoEliminacion.trasError(
                new ConflictoConcurrenciaException(Mensajes.CONFLICTO_ELIMINACION), "m"));

        assertEquals(Mensajes.CONFLICTO_ELIMINACION, a.mensaje());
    }

    @Test
    void ingresoYaNoExiste_avisaYRecarga() {
        Paso.AvisarYRecargar a = assertInstanceOf(Paso.AvisarYRecargar.class,
            DecisionDialogoEliminacion.trasError(
                new ResourceNotFoundException(Mensajes.INGRESO_YA_ELIMINADO), "m"));

        assertEquals(Mensajes.INGRESO_YA_ELIMINADO, a.mensaje());
    }

    @Test
    void errorTecnico_muestraTextoGenericoSinFiltrarElDetalle() {
        Paso.MostrarError m = assertInstanceOf(Paso.MostrarError.class,
            DecisionDialogoEliminacion.trasError(new DatabaseException("SQL roto en tabla x"), "m"));

        assertEquals(Mensajes.ELIMINACION_ERROR_TECNICO, m.mensaje());
    }

    @Test
    void cualquierOtraExcepcion_muestraError() {
        assertInstanceOf(Paso.MostrarError.class,
            DecisionDialogoEliminacion.trasError(new IllegalStateException("bug"), "m"));
    }
}
