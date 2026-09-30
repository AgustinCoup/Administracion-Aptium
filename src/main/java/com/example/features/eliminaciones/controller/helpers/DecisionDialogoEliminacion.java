package com.example.features.eliminaciones.controller.helpers;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.eliminacion.EliminacionBloqueadaException;
import com.example.common.eliminacion.ResumenEliminacion;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.PasswordIncorrectaException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.exception.ValidationException;

import java.util.Objects;

/**
 * La máquina de decisiones del flujo de eliminación: qué hace el diálogo después de leer el
 * resumen y después de un error. Clase plana, sin Swing y sin estado (misma razón que
 * {@code GuardaRefresco}: {@code JOptionPane} tira {@code HeadlessException} en los tests).
 *
 * <p>El controller sólo traduce cada {@link Paso} a una llamada de Swing; no decide nada.</p>
 *
 * <p><b>Una password incorrecta reabre el diálogo conservando el motivo, y nunca la password.</b>
 * El operador que ya escribió el motivo no lo pierde por un error de tipeo; la password, en
 * cambio, ni siquiera llega hasta acá: la limpió quien la creó, y este código no la recibe.</p>
 */
public final class DecisionDialogoEliminacion {

    private DecisionDialogoEliminacion() {}

    /** Lo que sigue en el flujo. Cerrado: el controller cubre todos los casos o no compila. */
    public sealed interface Paso {

        /** Hay algo en curso que lo impide: se avisa qué finalizar y no se pide la password. */
        record MostrarBloqueos(String texto) implements Paso {}

        /** Se puede eliminar: pedir motivo y password. */
        record PedirConfirmacion(String texto, boolean avisoPasswordInicial) implements Paso {}

        /** Volver a pedir motivo y password, con el motivo ya tipeado y el mensaje del error. */
        record ReabrirDialogo(String motivo, String mensaje) implements Paso {}

        /** El ingreso cambió o desapareció: avisar y releer la pantalla. */
        record AvisarYRecargar(String mensaje) implements Paso {}

        /** Error que el operador no puede accionar. */
        record MostrarError(String mensaje) implements Paso {}
    }

    /**
     * @param passwordEsInicial se lee junto con el resumen (es un acceso a base) y viaja como dato:
     *                          esta clase no toca la base
     */
    public static Paso siguientePaso(ResumenEliminacion resumen, boolean passwordEsInicial) {
        Objects.requireNonNull(resumen, "resumen");
        if (resumen.estaBloqueado()) {
            return new Paso.MostrarBloqueos(TextoEliminacion.bloqueos(resumen));
        }
        return new Paso.PedirConfirmacion(TextoEliminacion.confirmacion(resumen), passwordEsInicial);
    }

    /**
     * El orden de las ramas importa: {@link PasswordIncorrectaException},
     * {@link EliminacionBloqueadaException} y {@link ConflictoConcurrenciaException} son todas
     * {@code BusinessException}, y {@link ValidationException} y {@link ResourceNotFoundException}
     * son hermanas de ella; cada una se decide por su tipo exacto.
     *
     * @param motivoTipeado lo que el operador había escrito; {@code null} se trata como vacío
     */
    public static Paso trasError(Throwable error, String motivoTipeado) {
        String motivo = motivoTipeado == null ? "" : motivoTipeado;
        if (error instanceof PasswordIncorrectaException) {
            return new Paso.ReabrirDialogo(motivo, error.getMessage());
        }
        if (error instanceof ValidationException v) {
            return new Paso.ReabrirDialogo(motivo, String.join("\n", v.getValidationErrors()));
        }
        if (error instanceof EliminacionBloqueadaException) {
            return new Paso.MostrarBloqueos(error.getMessage());
        }
        if (error instanceof ConflictoConcurrenciaException || error instanceof ResourceNotFoundException) {
            return new Paso.AvisarYRecargar(error.getMessage());
        }
        return new Paso.MostrarError(Mensajes.ELIMINACION_ERROR_TECNICO);
    }
}
