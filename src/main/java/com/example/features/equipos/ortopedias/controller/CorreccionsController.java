package com.example.features.equipos.ortopedias.controller;

import com.example.common.exception.DatabaseException;
import com.example.common.exception.ValidationException;
import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.features.catalogo.service.CatalogoOtrosService;
import com.example.features.equipos.ortopedias.controller.helpers.FiltroAuditorias;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EquipoAuditoria;
import com.example.features.equipos.ortopedias.service.EquipoCorreccionService;
import com.example.ui.common.TareaUI;
import com.example.features.equipos.ortopedias.view.PantallaAuditoria;
import com.example.features.equipos.ortopedias.view.PantallaCorrecciones;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosCorreccionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class CorreccionsController {

    private static final Logger log = LoggerFactory.getLogger(CorreccionsController.class);

    private final PantallaCorrecciones        panel;
    private final EquipoCorreccionService     correccionService;
    private final EquipoOtrosCorreccionService otrosService;
    private Runnable                           onCambiosAplicados;

    /** Se inyecta después de construir: la pantalla la crea PantallaPrincipal. */
    private PantallaAuditoria     pantallaAuditoria;
    private List<EquipoAuditoria> auditoriasCargadas = List.of();

    /**
     * Alcance: correcciones auditadas sobre equipos de ortopedia y "otros", más
     * el autocompletado del catálogo de "otros" en el formulario de corrección.
     */
    public CorreccionsController(PantallaCorrecciones panel,
                                 EquipoCorreccionService correccionService,
                                 EquipoOtrosCorreccionService otrosService,
                                 CatalogoOtrosService catalogoOtrosService) {
        this.panel             = panel;
        this.correccionService = correccionService;
        this.otrosService      = otrosService;

        // ── Callbacks ortopedia ───────────────────────────────────────────────
        panel.setOnModificarCantidad((equipoId, materialId, cantidad, motivo) ->
            modificarCantidadMaterial(equipoId, materialId, cantidad, motivo));

        panel.setOnModificarCodigo((equipoId, materialId, codigo, motivo) ->
            modificarCodigoMaterial(equipoId, materialId, codigo, motivo));

        panel.setOnAgregarMaterial((equipoId, codigoCatalogo, cantidad, motivo) ->
            agregarMaterial(equipoId, codigoCatalogo, cantidad, motivo));

        panel.setOnEliminarEquipo(this::eliminarEquipo);
        panel.setOnEliminarMaterial(this::eliminarMaterial);

        // ── Callbacks otros ───────────────────────────────────────────────────
        panel.setOnModificarCantidadRemito((equipoId, cantidad, motivo) ->
            modificarCantidadRemito(equipoId, cantidad, motivo));

        panel.setOnModificarCantidadMaterialOtros((equipoId, materialId, cantidad, motivo) ->
            modificarCantidadMaterialOtros(equipoId, materialId, cantidad, motivo));

        panel.setOnAgregarMaterialOtros((equipoId, descripcion, cantidad, motivo) ->
            agregarMaterialOtros(equipoId, descripcion, cantidad, motivo));

        panel.setOnEliminarMaterialOtros((equipoId, descripcion, motivo) ->
            eliminarMaterialOtros(equipoId, descripcion, motivo));

        panel.setOnEliminarEquipoOtros(this::eliminarEquipoOtros);

        panel.setBuscarMaterialesOtros(catalogoOtrosService::buscarPorDescripcionParcial);
        panel.setVerificarMaterialOtros(catalogoOtrosService::existeDescripcion);

        // ── General ───────────────────────────────────────────────────────────
        panel.setOnPantallaVisible(this::cargarEquiposNuevos);

        panel.setOnCodigoNuevoChanged((codigo, campoDescripcion) ->
            TareaUI.<String>nueva()
                .nombre("autocompletar-descripcion")
                .leer(() -> correccionService.obtenerDescripcionMaterial(codigo))
                .pintar(descripcion -> campoDescripcion.setText(descripcion != null
                    ? descripcion : Constantes.Mensajes.AUTOCOMPLETE_DESCONOCIDO))
                .lanzar());

        cargarEquiposNuevos();
        panel.limpiarPantalla();
    }

    // ── API pública ──────────────────────────────────────────────────────────

    public void setOnVerAuditoria(Runnable callback) {
        panel.setOnVerAuditoria(callback);
    }

    public void setOnCambiosAplicados(Runnable callback) {
        this.onCambiosAplicados = callback;
    }

    /**
     * Toma a su cargo la pantalla de auditoría: la lectura y el filtrado viven
     * acá, no en la view, que solo dibuja lo que se le pasa.
     */
    public void inicializarPantallaAuditoria(PantallaAuditoria pantalla) {
        this.pantallaAuditoria = pantalla;
        pantalla.setOnRecargar(this::cargarAuditoria);
        pantalla.setOnFiltrosChanged(this::pintarAuditoriasFiltradas);
        cargarAuditoria();
    }

    // ── Auditoría ────────────────────────────────────────────────────────────

    private void cargarAuditoria() {
        if (pantallaAuditoria == null) return;
        pantallaAuditoria.mostrarCargando();

        TareaUI.<List<EquipoAuditoria>>nueva()
            .nombre("carga-auditoria")
            .leer(correccionService::obtenerTodasAuditorias)
            .pintar(auditorias -> {
                auditoriasCargadas = auditorias;
                pintarAuditoriasFiltradas();
                log.info("Cargados {} registros de auditoría", auditorias.size());
            })
            .siFalla(e -> pantallaAuditoria.mostrarError(e.getMessage()))
            .lanzar();
    }

    /** Refiltra lo ya cargado: no vuelve a la base al mover un filtro. */
    private void pintarAuditoriasFiltradas() {
        if (pantallaAuditoria == null) return;
        List<EquipoAuditoria> filtradas =
            FiltroAuditorias.filtrar(auditoriasCargadas, pantallaAuditoria.getCriterio());
        pantallaAuditoria.mostrarAuditorias(filtradas, auditoriasCargadas.size());
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    private void cargarEquiposNuevos() {
        TareaUI.<List<EquipoRegistrableInterface>>nueva()
            .nombre("carga-equipos-nuevos")
            .leer(() -> {
                List<Equipo>      ortopedias = correccionService.obtenerEquiposNuevos();
                List<EquipoOtros> otros      = otrosService.obtenerEquiposOtrosNuevos();

                List<EquipoRegistrableInterface> combinada = new ArrayList<>(ortopedias);
                combinada.addAll(otros);
                log.info("Se cargaron {} equipos nuevos ({} ortopedia, {} otros)",
                    combinada.size(), ortopedias.size(), otros.size());
                return combinada;
            })
            .pintar(equipos -> {
                panel.actualizarListaEquiposUnificada(equipos);
                panel.limpiarPantalla();
                panel.mostrarMensaje("Se cargaron " + equipos.size() + " equipos en estado NUEVO");
            })
            .siFalla(e -> panel.mostrarError("Error al cargar equipos: " + e.getMessage()))
            .antes(()  -> panel.mostrarCargando(true))
            .despues(() -> panel.mostrarCargando(false))
            .lanzar();
    }

    // ── Operaciones ortopedia ────────────────────────────────────────────────

    private void modificarCantidadMaterial(Integer equipoId, Integer materialId,
                                           Integer cantidadNueva, String motivo) {
        aplicarCorreccion("modificar-cantidad",
            () -> correccionService.modificarCantidadMaterial(equipoId, materialId, cantidadNueva, motivo),
            "Cantidad modificada correctamente", "No se pudo modificar la cantidad");
    }

    private void modificarCodigoMaterial(Integer equipoId, Integer materialId,
                                         Integer codigoNuevo, String motivo) {
        aplicarCorreccion("modificar-codigo",
            () -> correccionService.modificarCodigoMaterial(equipoId, materialId, codigoNuevo, motivo),
            "Código modificado correctamente", "No se pudo modificar el código");
    }

    private void agregarMaterial(Integer equipoId, Integer codigoCatalogo,
                                 Integer cantidad, String motivo) {
        aplicarCorreccion("agregar-material",
            () -> correccionService.agregarMaterialAEquipo(equipoId, codigoCatalogo, cantidad, motivo),
            "Material agregado correctamente", "No se pudo agregar el material");
    }

    private void eliminarEquipo(Integer equipoId, String motivo) {
        if (!confirmarEliminacionDeEquipo()) return;
        aplicarCorreccion("eliminar-equipo",
            () -> correccionService.eliminarEquipo(equipoId, motivo),
            "Equipo eliminado correctamente", "No se pudo eliminar el equipo");
    }

    private void eliminarMaterial(Integer equipoId, Integer codigoCatalogo, String motivo) {
        aplicarCorreccion("eliminar-material",
            () -> correccionService.eliminarMaterial(equipoId, codigoCatalogo, motivo),
            "Material eliminado correctamente", "No se pudo eliminar el material");
    }

    // ── Operaciones otros ────────────────────────────────────────────────────

    private void modificarCantidadRemito(Integer equipoId, Integer cantidadNueva, String motivo) {
        aplicarCorreccion("modificar-cantidad-remito",
            () -> otrosService.modificarCantidadRemito(equipoId, cantidadNueva, motivo),
            "Cantidad del remito modificada correctamente",
            "No se pudo modificar la cantidad del remito");
    }

    private void modificarCantidadMaterialOtros(Integer equipoId, Integer materialId,
                                                Integer cantidadNueva, String motivo) {
        aplicarCorreccion("modificar-cantidad-otros",
            () -> otrosService.modificarCantidadMaterial(equipoId, materialId, cantidadNueva, motivo),
            "Cantidad modificada correctamente", "No se pudo modificar la cantidad");
    }

    private void agregarMaterialOtros(Integer equipoId, String descripcion,
                                      Integer cantidad, String motivo) {
        aplicarCorreccion("agregar-material-otros",
            () -> otrosService.agregarMaterial(equipoId, descripcion, cantidad, motivo),
            "Material agregado correctamente", "No se pudo agregar el material");
    }

    private void eliminarMaterialOtros(Integer equipoId, String descripcion, String motivo) {
        aplicarCorreccion("eliminar-material-otros",
            () -> otrosService.eliminarMaterial(equipoId, descripcion, motivo),
            "Material eliminado correctamente", "No se pudo eliminar el material");
    }

    private void eliminarEquipoOtros(Integer equipoId, String motivo) {
        if (!confirmarEliminacionDeEquipo()) return;
        aplicarCorreccion("eliminar-equipo-otros",
            () -> otrosService.eliminarEquipo(equipoId, motivo),
            "Equipo eliminado correctamente", "No se pudo eliminar el equipo");
    }

    // ── Mecánica común de las correcciones ───────────────────────────────────

    /**
     * Toda corrección tiene la misma forma: se escribe fuera del hilo de UI y, si el
     * service confirma el cambio, se recarga la lista y se avisa al resto de las
     * pantallas. Tenerlo en un solo lugar evita que las 9 operaciones se desincronicen.
     *
     * @param nombre   identifica la tarea en el log
     * @param operacion escritura contra el service; {@code false} = el cambio no se aplicó
     */
    private void aplicarCorreccion(String nombre, Callable<Boolean> operacion,
                                   String mensajeExito, String mensajeFallo) {
        TareaUI.<Boolean>nueva()
            .nombre(nombre)
            .leer(operacion)
            .pintar(aplicada -> {
                if (Boolean.TRUE.equals(aplicada)) {
                    panel.mostrarMensaje(mensajeExito);
                    cargarEquiposNuevos();
                    notificarCambiosAplicados();
                } else {
                    panel.mostrarError(mensajeFallo);
                }
            })
            .siFalla(e -> panel.mostrarError(describirError(e)))
            .antes(()  -> panel.mostrarCargando(true))
            .despues(() -> panel.mostrarCargando(false))
            .lanzar();
    }

    private boolean confirmarEliminacionDeEquipo() {
        return JOptionPane.showConfirmDialog(
            panel,
            "¿Está seguro de que desea eliminar este equipo?\n\nEsta acción no se puede deshacer.",
            "Confirmar eliminación",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static String describirError(Throwable e) {
        if (e instanceof ValidationException) return "Error de validación: " + e.getMessage();
        if (e instanceof DatabaseException)   return "Error en la base de datos: " + e.getMessage();
        return "Error inesperado: " + e.getMessage();
    }

    private void notificarCambiosAplicados() {
        if (onCambiosAplicados != null) onCambiosAplicados.run();
    }
}
