package com.example.features.ajustes.controller;

import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.model.ReleaseInfo;
import com.example.features.actualizaciones.service.ActualizacionService;
import com.example.features.ajustes.view.FusionarClienteDialog;
import com.example.features.ajustes.view.NuevoClienteDialog;
import com.example.features.ajustes.view.PanelGestionClientes;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.clientes.model.Cliente;
import com.example.features.clientes.service.ClienteService;
import com.example.ui.common.TareaUI;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AjustesController {

    private final PantallaAjustes      vista;
    private final PanelGestionClientes panel;
    private final ClienteService       clienteService;
    private final ActualizacionService actualizacionService;
    private       Runnable             onMutacion;

    /** Alcance: ABM y fusión de clientes, y auto-actualización de la app. */
    public AjustesController(
        PantallaAjustes vista, ClienteService clienteService, ActualizacionService actualizacionService
    ) {
        this.vista                = vista;
        this.panel                = vista.getPanelClientes();
        this.clienteService       = clienteService;
        this.actualizacionService = actualizacionService;

        panel.setOnAgregar(this::agregarCliente);
        panel.setOnEliminar(this::eliminarCliente);
        panel.setOnFusionar(this::fusionarCliente);
        vista.setOnBuscarActualizaciones(this::buscarActualizaciones);

        vista.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                panel.limpiarBusqueda();
                cargarDatos();
            }
        });
    }

    public void setOnMutacion(Runnable r) { this.onMutacion = r; }

    private void cargarDatos() {
        TareaUI.<List<Cliente>>nueva()
            .nombre("ajustes-cargar-clientes")
            .leer(clienteService::obtenerTodosLosClientes)
            .pintar(panel::setDatos)
            .siFalla(e -> mostrarError("Error al cargar clientes."))
            .lanzar();
    }

    private void notificarMutacion() {
        if (onMutacion != null) onMutacion.run();
    }

    private void agregarCliente() {
        NuevoClienteDialog dialog = new NuevoClienteDialog(SwingUtilities.getWindowAncestor(vista));
        dialog.setVisible(true);
        String nombre = dialog.obtenerNombre();
        if (nombre == null) return;

        mutar("ajustes-guardar",
            () -> clienteService.guardarCliente(new Cliente(0, nombre)),
            "Error al guardar el cliente: ", "Error");
    }

    private void eliminarCliente() {
        Cliente cliente = panel.getClienteSeleccionado();
        if (cliente == null) {
            JOptionPane.showMessageDialog(vista, "Seleccione un cliente para eliminar.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int resp = JOptionPane.showConfirmDialog(vista,
            "¿Eliminar el cliente \"" + cliente.getNombre() + "\"?",
            "Confirmar eliminación", JOptionPane.YES_NO_OPTION);
        if (resp != JOptionPane.YES_OPTION) return;

        mutar("ajustes-eliminar",
            () -> clienteService.eliminarCliente(cliente.getId()),
            "", "No se puede eliminar");
    }

    private void fusionarCliente() {
        Cliente origen = panel.getClienteSeleccionado();
        if (origen == null) {
            JOptionPane.showMessageDialog(vista, "Seleccione el cliente a fusionar (el que se eliminará).",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        TareaUI.<List<Cliente>>nueva()
            .nombre("ajustes-fusion-candidatos")
            .leer(() -> clienteService.obtenerTodosLosClientes().stream()
                .filter(c -> c.getId() != origen.getId())
                .collect(Collectors.toList()))
            .pintar(candidatos -> mostrarDialogoFusion(origen, candidatos))
            .siFalla(e -> mostrarError("Error al cargar clientes: " + e.getMessage(), "Error"))
            .lanzar();
    }

    private void mostrarDialogoFusion(Cliente origen, List<Cliente> candidatos) {
        if (candidatos.isEmpty()) {
            JOptionPane.showMessageDialog(vista, "No hay otros clientes disponibles para fusionar.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        FusionarClienteDialog dialog = new FusionarClienteDialog(
            SwingUtilities.getWindowAncestor(vista), origen, candidatos);
        dialog.setVisible(true);
        Cliente destino = dialog.obtenerClienteDestino();
        if (destino == null) return;

        int resp = JOptionPane.showConfirmDialog(vista,
            "Se eliminará \"" + origen.getNombre() + "\" y sus equipos pasarán a \""
                + destino.getNombre() + "\".\n¿Confirmar?",
            "Confirmar fusión", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (resp != JOptionPane.YES_OPTION) return;

        mutar("ajustes-fusion",
            () -> clienteService.fusionarClientes(origen.getId(), destino.getId()),
            "Error al fusionar clientes: ", "Error");
    }

    // ── Mecánica común ───────────────────────────────────────────────────────

    /**
     * Toda mutación de clientes sigue la misma forma: se escribe fuera del hilo de UI
     * y al terminar se recarga la lista y se avisa al resto de la aplicación.
     */
    private void mutar(String nombre, Runnable operacion, String prefijoError, String tituloError) {
        TareaUI.<Void>nueva()
            .nombre(nombre)
            .leer(() -> { operacion.run(); return null; })
            .pintar(sinResultado -> {
                cargarDatos();
                notificarMutacion();
            })
            .siFalla(e -> mostrarError(prefijoError + e.getMessage(), tituloError))
            .lanzar();
    }

    private void mostrarError(String mensaje) {
        mostrarError(mensaje, "Error");
    }

    private void mostrarError(String mensaje, String titulo) {
        JOptionPane.showMessageDialog(vista, mensaje, titulo, JOptionPane.ERROR_MESSAGE);
    }

    // ── Auto-actualización ──────────────────────────────────────────────────
    // Cada paso llama solo a ActualizacionService (nunca a sus colaboradores internos);
    // el orden chequeo → confirmación → descarga → confirmación → instalación vive acá
    // porque requiere diálogos intermedios que el service, al no ser código de UI, no puede mostrar.

    private void buscarActualizaciones() {
        TareaUI.<Optional<ReleaseInfo>>nueva()
            .nombre("ajustes-chequear-actualizacion")
            .leer(actualizacionService::hayActualizacionDisponible)
            .pintar(this::manejarResultadoChequeo)
            .siFalla(this::mostrarErrorActualizacion)
            .lanzar();
    }

    private void manejarResultadoChequeo(Optional<ReleaseInfo> releaseDisponible) {
        if (releaseDisponible.isEmpty()) {
            JOptionPane.showMessageDialog(vista, Constantes.Mensajes.NO_HAY_ACTUALIZACIONES,
                Constantes.Mensajes.TITULO_ACTUALIZACION_DISPONIBLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ReleaseInfo release = releaseDisponible.get();
        Object[] opciones = { Constantes.Mensajes.ACTUALIZAR_AHORA, Constantes.Mensajes.MAS_TARDE };
        int resp = JOptionPane.showOptionDialog(vista,
            String.format(Constantes.Mensajes.ACTUALIZACION_DISPONIBLE, release.tag(), release.changelog()),
            Constantes.Mensajes.TITULO_ACTUALIZACION_DISPONIBLE,
            JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
            null, opciones, opciones[1]);
        if (resp != 0) return;

        descargarActualizacion(release);
    }

    private void descargarActualizacion(ReleaseInfo release) {
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        barra.setStringPainted(true);
        barra.setString(String.format(Constantes.Mensajes.DESCARGANDO_ACTUALIZACION, 0));

        JDialog dialogoProgreso = new JDialog(
            SwingUtilities.getWindowAncestor(vista), Constantes.Mensajes.TITULO_DESCARGANDO_ACTUALIZACION);
        dialogoProgreso.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialogoProgreso.getContentPane().add(barra);
        dialogoProgreso.setSize(320, 80);
        dialogoProgreso.setLocationRelativeTo(vista);
        dialogoProgreso.setVisible(true);

        TareaUI.<Path>nueva()
            .nombre("ajustes-descargar-actualizacion")
            .leer(() -> actualizacionService.descargarActualizacion(release,
                bytes -> SwingUtilities.invokeLater(() ->
                    barra.setString(String.format(Constantes.Mensajes.DESCARGANDO_ACTUALIZACION, bytes / 1024)))))
            .pintar(this::confirmarInstalacion)
            .siFalla(this::mostrarErrorActualizacion)
            .despues(dialogoProgreso::dispose)
            .lanzar();
    }

    private void confirmarInstalacion(Path jarVerificado) {
        int resp = JOptionPane.showConfirmDialog(vista,
            Constantes.Mensajes.CONFIRMAR_INSTALAR_ACTUALIZACION,
            Constantes.Mensajes.TITULO_INSTALAR_ACTUALIZACION,
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (resp != JOptionPane.YES_OPTION) return;

        TareaUI.<Void>nueva()
            .nombre("ajustes-instalar-actualizacion")
            .leer(() -> { actualizacionService.instalarActualizacion(jarVerificado); return null; })
            .siFalla(this::mostrarErrorActualizacion)
            .lanzar();
    }

    private void mostrarErrorActualizacion(Throwable e) {
        mostrarError(String.format(Constantes.Mensajes.ERROR_ACTUALIZACION, e.getMessage()),
            Constantes.Mensajes.TITULO_ERROR_ACTUALIZACION);
    }
}
