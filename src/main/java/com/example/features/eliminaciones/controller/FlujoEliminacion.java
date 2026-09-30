package com.example.features.eliminaciones.controller;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.eliminacion.IngresoAEliminar;
import com.example.common.eliminacion.ResumenEliminacion;
import com.example.features.eliminaciones.controller.helpers.DecisionDialogoEliminacion;
import com.example.features.eliminaciones.controller.helpers.DecisionDialogoEliminacion.Paso;
import com.example.features.eliminaciones.service.EliminacionIngresosService;
import com.example.features.eliminaciones.service.SolicitudEliminacion;
import com.example.features.eliminaciones.view.EliminarIngresoDialog;
import com.example.ui.common.TareaUI;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.Objects;
import java.util.Optional;

/**
 * El flujo de eliminar un ingreso, compartido por las tres grillas desde las que se elimina (las dos
 * de Ver Equipos y la de Historial de Lavadero): resumen → diálogo → eliminación → relectura.
 *
 * <p><b>Orquesta, no decide.</b> Qué sigue después de leer el resumen y después de un error lo
 * decide {@link DecisionDialogoEliminacion}; el texto de la confirmación lo arma {@code
 * TextoEliminacion}. Acá cada {@link Paso} se traduce a una llamada de Swing y nada más.</p>
 *
 * <h2>Threading</h2>
 * Ninguna lectura ni escritura corre en el EDT: el resumen y la eliminación van por {@link TareaUI}
 * (con nombres fijos, sin datos del ingreso), y el diálogo se abre siempre desde el {@code pintar}
 * o el {@code siFalla} de la anterior. {@link #enCurso} se toca sólo en el EDT.
 *
 * <h2>La contraseña</h2>
 * Viaja como {@code char[]} dentro de {@link EliminarIngresoDialog.Datos} y se limpia en el
 * {@code finally} del {@code leer}: pase lo que pase con la eliminación, el array queda en ceros
 * apenas el service termina con él. No se convierte en {@code String} en ningún punto y no se loguea.
 *
 * <h2>Cuándo se relee</h2>
 * {@code alTerminar} se corre cuando <b>la realidad pudo haber cambiado</b>: después de eliminar, de
 * un conflicto y de un ingreso que ya no existe. No se corre si el operador cancela ni cuando un
 * bloqueo se avisa antes de pedir la contraseña —no pasó nada—. Corre <b>antes</b> del aviso: el
 * cartel es modal, y así la grilla se actualiza mientras el operador lo lee.
 */
public final class FlujoEliminacion {

    /** El resumen y el dato que lo acompaña: los dos se leen juntos, en el mismo {@code leer}. */
    private record ResumenLeido(ResumenEliminacion resumen, boolean passwordEsInicial) {}

    private final EliminacionIngresosService service;
    private final Component padre;
    private final Runnable alTerminar;

    /**
     * Hay un flujo en marcha. Sin esto, dos clics seguidos en el botón antes de que llegue el
     * resumen abrirían dos diálogos, uno detrás del otro. Sólo se toca en el EDT.
     */
    private boolean enCurso;

    /**
     * @param padre      el componente sobre el que se abren los diálogos
     * @param alTerminar qué hacer cuando la pantalla tiene que releer (publicar la consulta
     *                   recontando y pedir el refresco operativo, en los dos controllers)
     */
    public FlujoEliminacion(EliminacionIngresosService service, Component padre, Runnable alTerminar) {
        this.service = Objects.requireNonNull(service, "service");
        this.padre = Objects.requireNonNull(padre, "padre");
        this.alTerminar = Objects.requireNonNull(alTerminar, "alTerminar");
    }

    /** Arranca el flujo para un ingreso. Se ignora si ya hay uno en marcha. */
    public void iniciar(IngresoAEliminar ingreso) {
        Objects.requireNonNull(ingreso, "ingreso");
        if (enCurso) return;
        enCurso = true;

        TareaUI.<ResumenLeido>nueva()
            .nombre("eliminar-resumen")
            // Dos lecturas secuenciales, sin conexiones anidadas (invariante del semáforo del pool).
            .leer(() -> new ResumenLeido(service.resumir(ingreso), service.passwordEsInicial()))
            .pintar(leido -> abrir(leido, "", null))
            .siFalla(error -> aplicar(DecisionDialogoEliminacion.trasError(error, ""), null))
            .lanzar();
    }

    private void abrir(ResumenLeido leido, String motivoPrevio, String mensajeError) {
        Paso paso = DecisionDialogoEliminacion.siguientePaso(leido.resumen(), leido.passwordEsInicial());
        if (paso instanceof Paso.MostrarBloqueos bloqueos) {
            terminar(false);
            avisar(bloqueos.texto(), JOptionPane.WARNING_MESSAGE);
        } else if (paso instanceof Paso.PedirConfirmacion confirmacion) {
            pedirYEliminar(leido, confirmacion, motivoPrevio, mensajeError);
        } else {
            throw new IllegalStateException("Paso inesperado tras leer el resumen: " + paso);
        }
    }

    private void pedirYEliminar(ResumenLeido leido, Paso.PedirConfirmacion confirmacion,
                                String motivoPrevio, String mensajeError) {
        Optional<EliminarIngresoDialog.Datos> respuesta = EliminarIngresoDialog.mostrar(
            padre, confirmacion.texto(), confirmacion.avisoPasswordInicial(), motivoPrevio, mensajeError);
        if (respuesta.isEmpty()) {
            terminar(false);
            return;
        }
        EliminarIngresoDialog.Datos datos = respuesta.get();
        SolicitudEliminacion solicitud = new SolicitudEliminacion(leido.resumen(), datos.motivo());

        TareaUI.<Void>nueva()
            .nombre("eliminar-ingreso")
            .leer(() -> {
                try {
                    service.eliminar(solicitud, datos.password());
                    return null;
                } finally {
                    datos.limpiar();
                }
            })
            .pintar(nada -> {
                terminar(true);
                avisar(Mensajes.ELIMINACION_EXITO, JOptionPane.INFORMATION_MESSAGE);
            })
            .siFalla(error -> aplicar(DecisionDialogoEliminacion.trasError(error, datos.motivo()), leido))
            .lanzar();
    }

    /**
     * Traduce a Swing lo que {@link DecisionDialogoEliminacion#trasError} decidió.
     *
     * @param leido el resumen ya leído, o {@code null} si el error fue al leerlo (en cuyo caso no hay
     *              diálogo que reabrir)
     */
    private void aplicar(Paso paso, ResumenLeido leido) {
        if (paso instanceof Paso.ReabrirDialogo reabrir && leido != null) {
            abrir(leido, reabrir.motivo(), reabrir.mensaje());
        } else if (paso instanceof Paso.MostrarBloqueos bloqueos) {
            terminar(true);
            avisar(bloqueos.texto(), JOptionPane.WARNING_MESSAGE);
        } else if (paso instanceof Paso.AvisarYRecargar recargar) {
            terminar(true);
            avisar(recargar.mensaje(), JOptionPane.WARNING_MESSAGE);
        } else if (paso instanceof Paso.MostrarError error) {
            terminar(false);
            avisar(error.mensaje(), JOptionPane.ERROR_MESSAGE);
        } else {
            // Un ReabrirDialogo sin resumen: no hay diálogo al que volver.
            terminar(false);
            avisar(Mensajes.ELIMINACION_ERROR_TECNICO, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void terminar(boolean releer) {
        enCurso = false;
        if (releer) {
            alTerminar.run();
        }
    }

    private void avisar(String mensaje, int tipo) {
        JOptionPane.showMessageDialog(padre, mensaje, Mensajes.TITULO_ELIMINAR_INGRESO, tipo);
    }
}
