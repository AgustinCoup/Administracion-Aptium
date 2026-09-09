package com.example.app;

import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.model.ReleaseInfo;
import com.example.features.actualizaciones.service.ActualizacionService;
import com.example.ui.common.TareaUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Recuperación cuando el arranque detecta que este build quedó atrás del esquema de la base
 * compartida (ver {@code DatabaseInitializer.verificarEsquemaNoAdelantado}). Antes de rendirse
 * con el error de esquema, ofrece la actualización automática — mismo flujo chequeo →
 * confirmación → descarga → confirmación → instalación que {@code AjustesController}, pero sin
 * depender de que {@code AppContext}/{@code UiCoordinator} ya existan: sólo usa
 * {@link ActualizacionService}, que no necesita conexión a la base.
 *
 * <p>Cualquier paso de este flujo que falle (sin red, descarga interrumpida, instalación
 * rechazada) cae en {@code siNoHayActualizacion}, que es quien deja al usuario exactamente donde
 * estaba antes de este cambio: el diálogo de "esquema desactualizado" de {@link App}.
 */
final class OfertaActualizacionAlArrancar {

    private static final Logger log = LoggerFactory.getLogger(OfertaActualizacionAlArrancar.class);

    private final ActualizacionService actualizacionService;
    private final Runnable siNoHayActualizacion;

    OfertaActualizacionAlArrancar(ActualizacionService actualizacionService, Runnable siNoHayActualizacion) {
        this.actualizacionService = actualizacionService;
        this.siNoHayActualizacion = siNoHayActualizacion;
    }

    void intentar() {
        TareaUI.<Optional<ReleaseInfo>>nueva()
            .nombre("arranque-chequear-actualizacion")
            .leer(actualizacionService::hayActualizacionDisponible)
            .pintar(this::ofrecerOFallar)
            .siFalla(e -> {
                log.warn("No se pudo chequear actualizaciones tras detectar esquema desactualizado: {}",
                    e.getMessage());
                siNoHayActualizacion.run();
            })
            .lanzar();
    }

    private void ofrecerOFallar(Optional<ReleaseInfo> release) {
        if (release.isEmpty()) {
            siNoHayActualizacion.run();
            return;
        }
        ofrecerInstalar(release.get());
    }

    private void ofrecerInstalar(ReleaseInfo release) {
        Object[] opciones = { Constantes.Mensajes.ACTUALIZAR_AHORA, Constantes.Mensajes.MAS_TARDE };
        int resp = JOptionPane.showOptionDialog(null,
            String.format(Constantes.Mensajes.ACTUALIZACION_REQUERIDA_POR_ESQUEMA, release.tag(), release.changelog()),
            Constantes.Mensajes.TITULO_ACTUALIZACION_DISPONIBLE,
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
            null, opciones, opciones[0]);
        if (resp != 0) {
            siNoHayActualizacion.run();
            return;
        }
        descargar(release);
    }

    private void descargar(ReleaseInfo release) {
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        barra.setStringPainted(true);
        barra.setString(Constantes.Mensajes.TITULO_DESCARGANDO_ACTUALIZACION);

        JDialog dialogoProgreso = new JDialog((java.awt.Frame) null, Constantes.Mensajes.TITULO_DESCARGANDO_ACTUALIZACION);
        dialogoProgreso.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialogoProgreso.getContentPane().add(barra);
        dialogoProgreso.setSize(320, 80);
        dialogoProgreso.setLocationRelativeTo(null);
        dialogoProgreso.setVisible(true);

        TareaUI.<Path>nueva()
            .nombre("arranque-descargar-actualizacion")
            .leer(() -> actualizacionService.descargarActualizacion(release, bytesIgnorados -> { }))
            .pintar(jarVerificado -> {
                // Igual que en AjustesController: cerrar el progreso antes de abrir el modal
                // siguiente, no en despues(), que corre recién cuando pintar() termina.
                dialogoProgreso.dispose();
                confirmarInstalacion(jarVerificado);
            })
            .siFalla(e -> {
                log.warn("Falló la descarga de la actualización ofrecida al arrancar: {}", e.getMessage());
                siNoHayActualizacion.run();
            })
            .despues(dialogoProgreso::dispose)
            .lanzar();
    }

    private void confirmarInstalacion(Path jarVerificado) {
        int resp = JOptionPane.showConfirmDialog(null,
            Constantes.Mensajes.CONFIRMAR_INSTALAR_ACTUALIZACION,
            Constantes.Mensajes.TITULO_INSTALAR_ACTUALIZACION,
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (resp != JOptionPane.YES_OPTION) {
            siNoHayActualizacion.run();
            return;
        }

        TareaUI.<Void>nueva()
            .nombre("arranque-instalar-actualizacion")
            .leer(() -> { actualizacionService.instalarActualizacion(jarVerificado); return null; })
            .siFalla(e -> {
                log.warn("Falló la instalación de la actualización ofrecida al arrancar: {}", e.getMessage());
                siNoHayActualizacion.run();
            })
            .lanzar();
    }
}
