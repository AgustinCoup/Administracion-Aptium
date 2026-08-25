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
import com.example.ui.common.dnd.LocalObjectFlavors;
import com.example.ui.common.dnd.MultiRowTableTransferHandler;

import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
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
 *
 * <p><b>Arrastre y botones son el mismo código.</b> Soltar filas en la otra tabla y apretar el
 * botón con esas mismas filas seleccionadas entran los dos por {@link #marcarListo(List)} /
 * {@link #volverALavado(List)}: si divergieran, el bug aparecería en el camino que nadie
 * prueba. Y cada tanda va en <b>un solo</b> llamado al service, para que el todo-o-nada lo
 * garantice la transacción del DAO y no un bucle de acá.</p>
 */
public class SalidasLavaderoController {

    /**
     * De qué tabla salió el arrastre en curso; {@code null} si no hay ninguno.
     *
     * <p>Hace falta porque las dos tablas comparten el {@link DataFlavor} — todos los que
     * devuelve {@code LocalObjectFlavors.forList()} son iguales entre sí — y, a diferencia de
     * Lotes y Ciclos, acá están las dos visibles a la vez. Sin el flag una tabla aceptaría lo
     * que acaba de salir de ella misma.</p>
     */
    private enum TablaOrigen { LAVADOS, LISTOS }

    private static final DataFlavor FLAVOR_SALIDAS = LocalObjectFlavors.forList();

    private final PantallaSalidasLavadero pantalla;
    private final SalidaLavaderoService   salidaLavaderoService;
    private final Runnable                refrescoOperativo;

    private TablaOrigen arrastreOrigen = null;

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

        pantalla.getBtnMarcarListo().addActionListener(e -> marcarListo(pantalla.getSeleccionLavados()));
        pantalla.getBtnVolverALavado().addActionListener(e -> volverALavado(pantalla.getSeleccionListos()));
        pantalla.getBtnSaleDelFlujo().addActionListener(e -> saleDelFlujo());
        pantalla.getBtnIngresarACde().addActionListener(e -> ingresarACde());

        configurarDnD();
    }

    // ── Arrastre entre las dos tablas ────────────────────────────────────────

    /**
     * Instala los handlers desde acá y no desde la vista, igual que en Ciclos: la pantalla ya
     * declaró que sus tablas arrastran y reciben, pero qué significa soltar algo en cada una
     * es una decisión de este controller.
     */
    private void configurarDnD() {
        if (FLAVOR_SALIDAS == null) return;   // el flavor no se pudo registrar: queda sólo el botón
        pantalla.getTablaLavados().setTransferHandler(handlerLavados());
        pantalla.getTablaListos().setTransferHandler(handlerListos());
    }

    /** Lavados: origen de lo que se va a marcar Listo, destino de lo que vuelve de Listos. */
    private MultiRowTableTransferHandler<Object> handlerLavados() {
        return new MultiRowTableTransferHandler.Builder<Object>(FLAVOR_SALIDAS)
            .sourceActions(TransferHandler.COPY)
            .selectionSupplier(() -> registrarArrastre(TablaOrigen.LAVADOS,
                                                       pantalla.getSeleccionLavados()))
            .canImportExtra(support -> arrastreOrigen == TablaOrigen.LISTOS)
            // invokeLater: los diálogos no deben abrirse dentro del drop, que corre en el EDT.
            .onImport(items -> SwingUtilities.invokeLater(
                () -> volverALavado(soloDeTipo(items, SalidaLista.class))))
            // Reset incondicional: un arrastre abortado dejaría el flag pegado y bloquearía
            // todos los drops siguientes.
            .onExportDone(action -> arrastreOrigen = null)
            .build();
    }

    /** Listos: destino de lo que se marca Listo, origen de lo que se devuelve a Lavados. */
    private MultiRowTableTransferHandler<Object> handlerListos() {
        return new MultiRowTableTransferHandler.Builder<Object>(FLAVOR_SALIDAS)
            .sourceActions(TransferHandler.COPY)
            .selectionSupplier(() -> registrarArrastre(TablaOrigen.LISTOS,
                                                       pantalla.getSeleccionListos()))
            .canImportExtra(support -> arrastreOrigen == TablaOrigen.LAVADOS)
            .onImport(items -> SwingUtilities.invokeLater(
                () -> marcarListo(soloDeTipo(items, ElementoLavadoPendiente.class))))
            .onExportDone(action -> arrastreOrigen = null)
            .build();
    }

    /** Anota de dónde salió el arrastre, sólo si efectivamente hay algo que arrastrar. */
    private List<Object> registrarArrastre(TablaOrigen origen, List<?> seleccion) {
        if (!seleccion.isEmpty()) arrastreOrigen = origen;
        return List.copyOf(seleccion);
    }

    /**
     * Las dos tablas comparten flavor, así que lo que llega es {@code List<?>}: el filtro por
     * tipo es la segunda línea de defensa del flag de origen, y deja pasar sólo lo que la
     * acción sabe procesar en vez de reventar con un {@code ClassCastException}.
     */
    private static <T> List<T> soloDeTipo(List<?> items, Class<T> tipo) {
        if (items == null) return List.of();
        return items.stream().filter(tipo::isInstance).map(tipo::cast).toList();
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    /** Lee las dos tablas de una sola vez, fuera del hilo de la interfaz. */
    public void cargarDatos() {
        TareaUI.<DatosSalidas>nueva()
            .nombre("carga-salidas-lavadero")
            .leer(() -> new DatosSalidas(
                salidaLavaderoService.obtenerLavadosPendientesDeListo(),
                salidaLavaderoService.obtenerListasSinDestino()))
            .pintar(datos -> pantalla.refrescar(datos.lavados(), datos.listos()))
            .siFalla(e -> pantalla.mostrarError(e.getMessage()))
            .lanzar();
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    /**
     * Marca Listo una tanda de filas, vengan del botón o de un arrastre.
     *
     * <p>Se pregunta la cantidad fila por fila, salvo que quede una sola unidad pendiente:
     * preguntar "¿cuántas de 1?" es ruido. Cancelar un diálogo <b>saltea esa fila y sigue con
     * la siguiente</b>, mismo criterio que el reparto de Ciclos; si no quedó ninguna marca no
     * se llama al service.</p>
     *
     * <p>Los diálogos se abren acá, en el hilo de la interfaz, y recién con la lista completa
     * se escribe: así la tanda entera entra en una sola transacción.</p>
     *
     * <p>Visible en el paquete porque es el punto de entrada del arrastre, que no se puede
     * simular sin entorno gráfico: el test lo ejercita llamándolo directamente.</p>
     */
    void marcarListo(List<ElementoLavadoPendiente> seleccion) {
        if (seleccion.isEmpty()) {
            pantalla.mostrarError(Constantes.Mensajes.SIN_SELECCION_LAVADOS);
            return;
        }

        List<MarcaListo> marcas = new ArrayList<>();
        for (ElementoLavadoPendiente item : seleccion) {
            int pendiente = item.cantidadPendiente();
            if (pendiente <= 0) continue;
            int cantidad = pendiente == 1 ? 1 : pantalla.pedirCantidadListo(item);
            if (cantidad <= 0) continue;
            marcas.add(new MarcaListo(item, cantidad));
        }
        if (marcas.isEmpty()) return;

        // Sin diálogo de éxito: es una acción de alta frecuencia y el feedback es que la fila
        // se mueve de tabla.
        List<MarcaListo> tanda = List.copyOf(marcas);
        ejecutar("marcar-listo", () -> salidaLavaderoService.marcarListo(tanda), () -> { });
    }

    /**
     * Deshace el marcado de una tanda de salidas. Sin diálogos: la salida vuelve entera, que
     * es lo mismo que hacía la versión de a una. Visible en el paquete por el mismo motivo
     * que {@link #marcarListo(List)}.
     */
    void volverALavado(List<SalidaLista> seleccion) {
        if (seleccion.isEmpty()) {
            pantalla.mostrarError(Constantes.Mensajes.SIN_SELECCION_LISTOS);
            return;
        }
        ejecutar("volver-a-lavado", () -> salidaLavaderoService.volverALavado(seleccion), () -> { });
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
