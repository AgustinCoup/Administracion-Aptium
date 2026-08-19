package com.example.features.lavadero.controller;

import com.example.common.constants.Constantes;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ValidationException;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.model.ElementoLavadoPendiente;
import com.example.features.lavadero.model.MarcaListo;
import com.example.features.lavadero.model.SalidaLista;
import com.example.features.lavadero.service.SalidaLavaderoService;
import com.example.features.lavadero.view.PantallaSalidasLavadero;
import com.example.ui.common.TareaUI;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cablea la pantalla de Salidas de Lavadero: qué botón dispara qué operación y qué se hace
 * con el resultado.
 *
 * <p><b>Todo acceso a la base va por {@link TareaUI}</b>, lecturas y escrituras por igual:
 * el hilo de la interfaz no toca JDBC (ver {@code EdtGuard}). No copiar el acceso directo de
 * {@code CiclosController.cargarDatos()}, que es deuda registrada, no un patrón.</p>
 *
 * <p><b>Refresco:</b> derivar al CDE crea ingresos en la cola operativa de esterilización, así
 * que dispara el refresco que alimenta <i>Registrar estado</i>, <i>Para entregar</i> y
 * <i>Gestionar lotes</i>. Las demás acciones no lo disparan: marcar Listo, volver a Lavado y
 * sacar del flujo no cambian nada en el CDE.</p>
 */
public class SalidasLavaderoController {

    private final PantallaSalidasLavadero pantalla;
    private final SalidaLavaderoService   salidaLavaderoService;
    private final Runnable                refrescoOperativo;

    /** Las dos tablas se leen y se pintan juntas para que no queden desincronizadas. */
    private record DatosSalidas(List<ElementoLavadoPendiente> lavados, List<SalidaLista> listos) { }

    public SalidasLavaderoController(PantallaSalidasLavadero pantalla,
                                     SalidaLavaderoService salidaLavaderoService,
                                     Runnable refrescoOperativo) {
        this.pantalla              = Objects.requireNonNull(pantalla, "pantalla no puede ser null");
        this.salidaLavaderoService = Objects.requireNonNull(salidaLavaderoService,
                                         "salidaLavaderoService no puede ser null");
        this.refrescoOperativo     = Objects.requireNonNull(refrescoOperativo,
                                         "refrescoOperativo no puede ser null");

        pantalla.getBtnMarcarListo().addActionListener(e -> marcarListo());
        pantalla.getBtnVolverALavado().addActionListener(e -> volverALavado());
        pantalla.getBtnSaleDelFlujo().addActionListener(e -> saleDelFlujo());
        pantalla.getBtnIngresarACde().addActionListener(e -> ingresarACde());

        pantalla.getTablaLavados().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) sincronizarSpinner();
        });
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    /** Lee las dos tablas de una sola vez, fuera del hilo de la interfaz. */
    public void cargarDatos() {
        TareaUI.<DatosSalidas>nueva()
            .nombre("carga-salidas-lavadero")
            .leer(() -> new DatosSalidas(
                salidaLavaderoService.obtenerLavadosPendientesDeListo(),
                salidaLavaderoService.obtenerListasSinDestino()))
            .pintar(datos -> {
                pantalla.refrescar(datos.lavados(), datos.listos());
                // El repintado borra la selección: el spinner tiene que acompañarla.
                sincronizarSpinner();
            })
            .siFalla(e -> pantalla.mostrarError(e.getMessage()))
            .lanzar();
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    /**
     * Con una sola fila seleccionada se marca la cantidad del spinner; con varias, el pendiente
     * entero de cada una. Va en un solo llamado al service para que el todo-o-nada lo garantice
     * la transacción del DAO y no este controller.
     */
    private void marcarListo() {
        List<ElementoLavadoPendiente> seleccion = pantalla.getSeleccionLavados();
        if (seleccion.isEmpty()) {
            pantalla.mostrarError(Constantes.Mensajes.SIN_SELECCION_LAVADOS);
            return;
        }

        List<MarcaListo> marcas = seleccion.size() == 1
            ? List.of(new MarcaListo(seleccion.get(0), cantidadDelSpinner()))
            : seleccion.stream().map(item -> new MarcaListo(item, item.cantidadPendiente())).toList();

        // Sin diálogo de éxito: es una acción de alta frecuencia y el feedback es que la fila
        // se mueve de tabla.
        ejecutar("marcar-listo", () -> salidaLavaderoService.marcarListo(marcas), () -> { });
    }

    /** Deshace el marcado de una salida. De a una: el service devuelve la salida entera a Lavado. */
    private void volverALavado() {
        List<SalidaLista> seleccion = pantalla.getSeleccionListos();
        if (seleccion.isEmpty()) {
            pantalla.mostrarError(Constantes.Mensajes.SIN_SELECCION_LISTOS);
            return;
        }
        if (seleccion.size() > 1) {
            pantalla.mostrarError(Constantes.Mensajes.VOLVER_A_LAVADO_UNA_SOLA);
            return;
        }

        int salidaId = seleccion.get(0).salidaId();
        ejecutar("volver-a-lavado", () -> salidaLavaderoService.volverALavado(salidaId), () -> { });
    }

    /** La ropa se devuelve al cliente: no entra nada al CDE, así que no hay refresco que disparar. */
    private void saleDelFlujo() {
        List<SalidaLista> seleccion = pantalla.getSeleccionListos();
        if (seleccion.isEmpty()) {
            pantalla.mostrarError(Constantes.Mensajes.SIN_SELECCION_LISTOS);
            return;
        }
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_SALE_DEL_FLUJO)) return;

        ejecutar("sale-del-flujo",
            () -> salidaLavaderoService.derivar(AccionSalida.FUERA_DE_FLUJO, seleccion),
            () -> { });
    }

    /**
     * El diálogo de a nombre de quién ingresan <b>es</b> la confirmación de la derivación:
     * elegir una de las dos opciones ya es aceptar y cancelar ya es no derivar. No se pide una
     * segunda confirmación.
     */
    private void ingresarACde() {
        List<SalidaLista> seleccion = pantalla.getSeleccionListos();
        if (seleccion.isEmpty()) {
            pantalla.mostrarError(Constantes.Mensajes.SIN_SELECCION_LISTOS);
            return;
        }

        AccionSalida accion = pantalla.elegirAccionCde(seleccion.size());
        if (accion == null) return;

        ejecutar("ingresar-a-cde",
            () -> salidaLavaderoService.derivar(accion, seleccion),
            () -> {
                // Lo que acaba de entrar tiene que verse ya en Registrar estado.
                refrescoOperativo.run();
                pantalla.mostrarInfo(resumenCde(accion, seleccion));
            });
    }

    // ── Mecánica común ───────────────────────────────────────────────────────

    /**
     * Toda acción de esta pantalla tiene la misma forma: se escribe fuera del hilo de la
     * interfaz, se recarga lo que quedó y recién entonces se hace lo específico de la acción.
     * Tenerlo en un solo lugar evita que las cuatro se desincronicen.
     */
    private void ejecutar(String nombre, Runnable escritura, Runnable alTerminar) {
        TareaUI.<Void>nueva()
            .nombre(nombre)
            .leer(() -> { escritura.run(); return null; })
            .pintar(sinResultado -> {
                cargarDatos();
                alTerminar.run();
            })
            .siFalla(this::mostrarFallo)
            .lanzar();
    }

    /**
     * Una {@link BusinessException} de esta feature siempre significa lo mismo — la pantalla
     * quedó vieja respecto de la base —, así que recargar es parte de la respuesta y no un extra.
     */
    private void mostrarFallo(Throwable causa) {
        if (causa instanceof ValidationException validacion) {
            pantalla.mostrarError(String.join("\n", validacion.getValidationErrors()));
            return;
        }
        pantalla.mostrarError(causa.getMessage());
        if (causa instanceof BusinessException) cargarDatos();
    }

    /** Habilita el spinner sólo con exactamente una fila: es el modo de marcado parcial. */
    private void sincronizarSpinner() {
        List<ElementoLavadoPendiente> seleccion = pantalla.getSeleccionLavados();
        boolean unaSola = seleccion.size() == 1;
        if (unaSola) pantalla.setMaximoCantidad(seleccion.get(0).cantidadPendiente());
        pantalla.setSpinnerHabilitado(unaSola);
    }

    private int cantidadDelSpinner() {
        return (Integer) pantalla.getSpnCantidad().getValue();
    }

    /**
     * Cuántos ingresos se crearon y a nombre de quién: un {@code EquipoOtros} por cliente
     * asignado, y con APTIUM todas las salidas caen bajo el mismo cliente vengan de donde vengan.
     */
    private String resumenCde(AccionSalida accion, List<SalidaLista> seleccion) {
        if (accion == AccionSalida.CDE_APTIUM) {
            return String.format(Constantes.Mensajes.RESUMEN_CDE_UN_INGRESO,
                                 Constantes.Lavadero.CLIENTE_APTIUM);
        }
        int clientes = seleccion.stream().map(SalidaLista::clienteId).collect(Collectors.toSet()).size();
        return clientes == 1
            ? String.format(Constantes.Mensajes.RESUMEN_CDE_UN_INGRESO, seleccion.get(0).clienteNombre())
            : String.format(Constantes.Mensajes.RESUMEN_CDE_VARIOS_INGRESOS, clientes);
    }
}
