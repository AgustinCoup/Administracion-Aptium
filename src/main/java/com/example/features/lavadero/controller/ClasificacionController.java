package com.example.features.lavadero.controller;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.lavadero.model.ElementoCatalogo;
import com.example.features.lavadero.model.ElementoClasificacion;
import com.example.features.lavadero.view.PantallaClasificacionLavadero.NuevoElementoCatalogo;
import com.example.features.lavadero.model.IngresoLavaderoResumen;
import com.example.features.lavadero.service.ClasificacionLavaderoService;
import com.example.features.lavadero.service.LavaderoService;
import com.example.features.lavadero.view.PanelElementosClasificacion.ElementoFila;
import com.example.features.lavadero.view.PantallaClasificacionLavadero;
import com.example.ui.common.TareaUI;
import com.example.ui.events.OnEquipoGuardadoListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Cablea la pantalla de clasificación de un ingreso de lavadero.
 *
 * <p><b>Todo acceso a la base va por {@link TareaUI}</b>: la validación del formulario y el
 * armado de los elementos quedan en el hilo de la interfaz, el guardado va a fondo y el
 * éxito —mensaje, limpieza, navegación y refresco— se aplica en {@code pintar}.
 */
public class ClasificacionController {

    private final PantallaClasificacionLavadero panel;
    private final LavaderoService               lavaderoService;
    private final ClasificacionLavaderoService   clasificacionLavaderoService;
    private final CardLayout                    navegador;
    private final JPanel                        contenedor;
    private final OnEquipoGuardadoListener      onGuardado;

    /** El combo de ingresos y el catálogo se pintan juntos: la vista los reemplaza de una. */
    private record DatosClasificacion(List<IngresoLavaderoResumen> ingresos,
                                      List<ElementoCatalogo> catalogo) { }

    public ClasificacionController(PantallaClasificacionLavadero panel,
                                   LavaderoService lavaderoService,
                                   ClasificacionLavaderoService clasificacionLavaderoService,
                                   CardLayout navegador,
                                   JPanel contenedor,
                                   OnEquipoGuardadoListener onGuardado) {
        this.panel      = panel;
        this.lavaderoService = lavaderoService;
        this.clasificacionLavaderoService = clasificacionLavaderoService;
        this.navegador  = navegador;
        this.contenedor = contenedor;
        this.onGuardado = onGuardado;

        panel.getBtnGuardar().addActionListener(e -> guardar());
        panel.getBtnCancelar().addActionListener(e -> cancelar());
        panel.getBtnNuevoCatalogo().addActionListener(e -> agregarElementoCatalogo());
    }

    public void cargarIngresosSinClasificar() {
        TareaUI.<DatosClasificacion>nueva()
            .nombre("carga-clasificacion-lavadero")
            .leer(() -> new DatosClasificacion(
                lavaderoService.obtenerIngresosSinClasificar(),
                clasificacionLavaderoService.obtenerCatalogo()))
            .pintar(datos -> panel.refrescar(datos.ingresos(), datos.catalogo()))
            .siFalla(e -> panel.mostrarError(Constantes.Mensajes.ERROR_CARGAR_DATOS))
            .lanzar();
    }

    private void guardar() {
        IngresoLavaderoResumen ingreso = panel.getSelectedIngreso();
        if (ingreso == null) {
            panel.mostrarError("Debe seleccionar un ingreso.");
            return;
        }

        List<ElementoFila> filas = panel.getPanelElementos().getFilas();
        if (filas.isEmpty()) {
            panel.mostrarError("Debe agregar al menos un elemento.");
            return;
        }

        if (panel.getPanelElementos().tieneDuplicados()) {
            panel.mostrarError("Hay elementos repetidos.\nUnifique las filas marcadas en rojo antes de guardar.");
            return;
        }

        List<ElementoClasificacion> elementos = new ArrayList<>();
        for (ElementoFila fila : filas) {
            int elementoId = fila.cmbElemento.getItemAt(fila.cmbElemento.getSelectedIndex()).getId();
            int cantidad   = (Integer) fila.spnCantidad.getValue();
            elementos.add(new ElementoClasificacion(elementoId, cantidad));
        }

        // Copia para el hilo de fondo: el formulario se limpia en el EDT ni bien vuelve.
        int ingresoId = ingreso.getId();
        List<ElementoClasificacion> aGuardar = List.copyOf(elementos);

        TareaUI.<Boolean>nueva()
            .nombre("guardar-clasificacion-lavadero")
            .antes(() -> panel.getBtnGuardar().setEnabled(false))
            .despues(() -> panel.getBtnGuardar().setEnabled(true))
            .leer(() -> clasificacionLavaderoService.guardar(ingresoId, aGuardar))
            .pintar(this::finalizarGuardado)
            .siFalla(this::mostrarFallo)
            .lanzar();
    }

    private void finalizarGuardado(boolean guardado) {
        if (!guardado) {
            panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_DATOS);
            return;
        }
        panel.mostrarInfo(Constantes.Mensajes.DATOS_GUARDADOS);
        panel.limpiarFormulario();
        cargarIngresosSinClasificar();
        navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
        onGuardado.onEquipoGuardado();
    }

    private void mostrarFallo(Throwable causa) {
        if (causa instanceof ValidationException validacion) {
            panel.mostrarError(String.join("\n", validacion.getValidationErrors()));
            return;
        }
        panel.mostrarError(Constantes.Mensajes.ERROR_GUARDAR_DATOS);
    }

    private void agregarElementoCatalogo() {
        NuevoElementoCatalogo pedido = panel.pedirNuevoElementoCatalogo();
        if (pedido == null) return;

        TareaUI.<ElementoCatalogo>nueva()
            .nombre("agregar-elemento-catalogo-lavadero")
            .antes(() -> panel.getBtnNuevoCatalogo().setEnabled(false))
            .despues(() -> panel.getBtnNuevoCatalogo().setEnabled(true))
            .leer(() -> clasificacionLavaderoService.agregarElementoCatalogo(
                pedido.nombre(), pedido.categoria()))
            .pintar(nuevo -> {
                panel.getPanelElementos().registrarElemento(nuevo);
                panel.mostrarInfo(String.format(
                    Constantes.Mensajes.ELEMENTO_CATALOGO_AGREGADO, nuevo.getNombre()));
            })
            .siFalla(this::mostrarFallo)
            .lanzar();
    }

    private void cancelar() {
        panel.limpiarFormulario();
        navegador.show(contenedor, Constantes.Pantallas.LAVADERO);
    }
}
