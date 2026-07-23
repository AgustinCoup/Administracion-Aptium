package com.example.features.lotes.controller;

import com.example.app.ui.DatosRefresco;
import com.example.common.constants.Constantes;
import com.example.features.lotes.controller.helpers.AgrupadorIngresosLote;
import com.example.features.lotes.controller.helpers.ConstructorMaterialesDisponibles;
import com.example.features.lotes.controller.helpers.EstadoStaging;
import com.example.features.lotes.controller.helpers.ReconciliadorPendientes;
import com.example.ui.common.dnd.MultiRowTableTransferHandler;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.features.autoclaves.model.Autoclave;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lotes.model.Lote;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import com.example.features.lotes.model.OcupacionAutoclave;
import com.example.features.lotes.service.LoteService;
import com.example.features.lotes.view.PantallaLotes;
import com.example.ui.dialogs.CantidadDialogHelper;
import com.example.features.lotes.view.helpers.AutoclaveItem;
import com.example.features.lotes.view.helpers.DialogoVolumenesIngreso;
import com.example.features.lotes.view.helpers.IngresoInfo;
import com.example.features.lotes.view.helpers.IngresoTooltipFormatter;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import com.example.features.lotes.view.helpers.PanelLotesContenido;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LotesController {

    private static final Logger log = LoggerFactory.getLogger(LotesController.class);

    private final PanelLotesContenido panel;
    private final LoteService         loteService;
    private final Runnable            solicitarRefresco;
    private OnEstadosActualizadosListener onEstadosActualizadosListener;

    /** Arma la tabla de disponibles descontando lo ya cargado. Lógica pura. */
    private final ConstructorMaterialesDisponibles constructorDisponibles =
        new ConstructorMaterialesDisponibles();

    /**
     * Último snapshot recibido. El DnD y los tooltips leen los mapas de abajo de
     * forma perezosa, así que se repuebla todo desde acá y <b>solo en el hilo de
     * UI, dentro de {@link #repintar()}</b>. Nunca desde el hilo de fondo.
     */
    private DatosRefresco ultimoSnapshot = DatosRefresco.vacio();

    private final Map<String, List<MaterialLoteItem>> pendientesPorAutoclave = new HashMap<>();
    private final Map<String, Lote> lotesActivos = new HashMap<>();
    private List<MaterialLoteItem> materialesDisponibles = new ArrayList<>();
    private Map<Integer, Integer> volumenesCatalogo = new HashMap<>();
    private AutoclaveItem autoclaveSeleccionado;

    /** Mapa equipoId → clienteNombre para ortopedias. */
    private Map<Integer, String> clientesPorEquipo = new HashMap<>();
    /**
     * Equipos "otros" por id (tabla distinta a ortopedias, sin colisión de IDs):
     * aporta el nombre de cliente y los datos del ingreso para el diálogo de volúmenes.
     */
    private Map<Integer, EquipoOtros> equiposOtrosPorId = new HashMap<>();
    /**
     * Info del ingreso de ortopedias por equipoId, para el tooltip de las tablas de
     * materiales. Los "otros" no se pre-mapean: salen de {@link #equiposOtrosPorId}.
     */
    private final Map<Integer, IngresoInfo> ingresoOrtopediaPorEquipo = new HashMap<>();

    /** Lógica pura de reconciliación disponibles ↔ pendientes (sin Swing). */
    private final ReconciliadorPendientes reconciliador = new ReconciliadorPendientes();

    /** true mientras se arrastra desde la tabla del autoclave (para rechazar el drop sobre sí misma). */
    private boolean arrastrandoDesdeAutoclave = false;

    // DataFlavor que transporta la List<MaterialLoteItem> arrastrada en la misma JVM
    public static final DataFlavor MATERIAL_LOTE_FLAVOR;

    static {
        DataFlavor flavor = null;
        try {
            flavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType +
                    ";class=\"" + java.util.List.class.getName() + "\"");
        } catch (ClassNotFoundException e) {
            log.error("No se pudo registrar el DataFlavor para drag-and-drop", e);
        }
        MATERIAL_LOTE_FLAVOR = flavor;
    }

    /**
     * Alcance: ciclo de vida del lote (lanzar, finalizar, marcar fallo). Los
     * equipos, autoclaves, volúmenes y lotes activos llegan por el refresco
     * global; este controller no lee de la base.
     */
    public LotesController(PantallaLotes pantallaLotes,
                           LoteService loteService,
                           OnEstadosActualizadosListener listener,
                           Runnable solicitarRefresco) {
        this.panel             = pantallaLotes.getPanelContenido();
        this.loteService       = loteService;
        this.solicitarRefresco = Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");
        this.onEstadosActualizadosListener = listener;

        inicializarEventos();

        // Bloquear navegación si hay materiales cargados en algún autoclave sin lanzar
        pantallaLotes.setGuardVolver(
            this::tieneCambiosPendientes,
            Constantes.Mensajes.GUARD_LOTES_CAMBIOS,
            this::descartarCambiosPendientes
        );
    }

    public void setOnEstadosActualizados(OnEstadosActualizadosListener listener) {
        this.onEstadosActualizadosListener = listener;
    }

    private void inicializarEventos() {
        panel.setOnAutoclaveSeleccionado(this::onAutoclaveSeleccionado);
        panel.setOnLanzar(e -> lanzarLote());
        panel.setOnFinalizar(e -> finalizarLote());
        panel.setOnMarcarFallo(e -> marcarLoteFallo());
        panel.setOnQuitar(e -> quitarMaterial());

        // Actualizar el estado del botón Lanzar en tiempo real cuando el usuario
        // modifica el campo de volumen manual.
        panel.setOnVolumenManualChanged(this::actualizarBotonLanzarPorVolumen);

        // Tooltips con la info del ingreso. Los closures resuelven los mapas de forma
        // perezosa en cada hover, así sobreviven a los repintados que los repueblan.
        panel.setTooltipDisponibles(item -> IngresoTooltipFormatter.format(item, resolverIngreso(item)));
        panel.setTooltipAutoclave(item  -> IngresoTooltipFormatter.format(item, resolverIngreso(item)));

        // Configurar DnD después de que el componente esté visible y con tamaño
        panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean dndConfigurado = false;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
                }
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
                }
            }
        });
    }

    /** Recibe el snapshot compartido y repinta. Corre en el hilo de UI, sin I/O. */
    public void pintar(DatosRefresco datos) {
        this.ultimoSnapshot = datos;
        repintar();
    }

    /**
     * Vuelca el último snapshot al panel, descontando lo que el usuario ya
     * arrastró a los autoclaves. Es el <b>único</b> lugar donde se escriben los
     * mapas que leen el DnD y los tooltips, y siempre desde el hilo de UI.
     */
    private void repintar() {
        String autoclaveSeleccion = autoclaveSeleccionado != null ? autoclaveSeleccionado.getNombre() : null;
        volumenesCatalogo = ultimoSnapshot.volumenesCatalogo();
        lotesActivos.clear();
        lotesActivos.putAll(ultimoSnapshot.lotesActivos());

        clientesPorEquipo.clear();
        equiposOtrosPorId.clear();
        ingresoOrtopediaPorEquipo.clear();
        for (Equipo equipo : ultimoSnapshot.equipos()) {
            clientesPorEquipo.put(equipo.getId(), equipo.getClienteNombre());
            ingresoOrtopediaPorEquipo.put(equipo.getId(), ingresoDe(equipo));
        }
        for (EquipoOtros equipo : ultimoSnapshot.equiposOtros()) {
            equiposOtrosPorId.put(equipo.getId(), equipo);
        }

        materialesDisponibles = constructorDisponibles.construir(
            ultimoSnapshot.equipos(),
            ultimoSnapshot.equiposOtros(),
            volumenesCatalogo,
            pendientesPorAutoclave);

        List<AutoclaveItem> items = new ArrayList<>();
        for (Autoclave autoclave : ultimoSnapshot.autoclaves()) {
            Lote loteActivo = lotesActivos.get(autoclave.getNombre());
            boolean ocupado = loteActivo != null;
            int capacidadUsada = ocupado
                    ? loteActivo.getCapacidadUsada()
                    : calcularCapacidadPendiente(autoclave.getNombre());
            Integer loteId = ocupado ? loteActivo.getId() : null;
            items.add(new AutoclaveItem(
                    autoclave.getNombre(),
                    autoclave.getCapacidad(),
                    ocupado,
                    loteId,
                    capacidadUsada
            ));
        }

        // Ordenar: primero libres, luego alfabético
        items.sort((a, b) -> {
            if (a.isOcupado() != b.isOcupado())
                return Boolean.compare(a.isOcupado(), b.isOcupado());
            return a.getNombre().compareTo(b.getNombre());
        });

        panel.setAutoclaves(items);
        if (autoclaveSeleccion != null) panel.seleccionarAutoclave(autoclaveSeleccion);
        panel.setMaterialesDisponibles(materialesDisponibles);
    }

    private void onAutoclaveSeleccionado(AutoclaveItem autoclave) {
        autoclaveSeleccionado = autoclave;
        if (autoclave == null) {
            panel.setMaterialesAutoclave(List.of());
            panel.setCapacidadTexto(Constantes.Textos.CAPACIDAD_AUTOCLAVE);
            panel.setVolumenManualEnabled(false);
            panel.setLanzarEnabled(false);
            panel.setFinalizarEnabled(false);
            panel.setMarcarFalloEnabled(false);
            panel.setQuitarEnabled(false);
            return;
        }

        if (autoclave.isOcupado()) {
            Lote lote = lotesActivos.get(autoclave.getNombre());
            List<LoteMaterialInfo> materialesLote = lote != null ? lote.getMateriales() : new ArrayList<>();
            List<MaterialLoteItem> items = new ArrayList<>();
            for (LoteMaterialInfo info : materialesLote) {
                // codigoCatalogo == 0 indica material de equipo_otros_materiales
                String clienteNombre = info.getCodigoCatalogo() == 0
                        ? nombreClienteOtros(info.getEquipoId())
                        : clientesPorEquipo.getOrDefault(info.getEquipoId(), "");
                // codigoCatalogo == 0 discrimina materiales de equipo_otros_materiales.
                // Para "otros": volumen en DB es el total declarado → setVolumenOtros.
                // Para ortopedia: volumen es unitario (del catálogo) → volumenTotal = cantidad × volumen.
                boolean esOtros = info.getCodigoCatalogo() == 0;
                MaterialLoteItem nuevoItem;
                if (esOtros) {
                    nuevoItem = new MaterialLoteItem(
                            info.getMaterialId(), info.getEquipoId(), info.getDescripcion(),
                            info.getCantidad(), 1, clienteNombre, true);
                    // Sin setVolumenOtros: el volumen pertenece al ingreso
                    // (lote_otros_volumenes); la columna muestra "-".
                } else {
                    nuevoItem = new MaterialLoteItem(
                            info.getMaterialId(), info.getEquipoId(), info.getDescripcion(),
                            info.getCantidad(), info.getVolumen(), clienteNombre);
                }
                items.add(nuevoItem);
            }
            panel.setMaterialesAutoclave(items);
            panel.setCapacidad(autoclave.getCapacidadUsada(), autoclave.getCapacidad());
            panel.setVolumenManualEnabled(false);
            panel.setLanzarEnabled(false);
            panel.setFinalizarEnabled(true);
            panel.setMarcarFalloEnabled(true);
            panel.setQuitarEnabled(false);
        } else {
            List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(autoclave.getNombre(), List.of());
            panel.setMaterialesAutoclave(pendientes);
            int usada = reconciliador.capacidadUsada(pendientes);
            panel.setCapacidadTexto(String.format("Capacidad: %d/%d", usada, autoclave.getCapacidad()));

            panel.setVolumenCalculado(usada);

            boolean hayPendientes = !pendientes.isEmpty();
            boolean hayOtros = contieneOtros(pendientes);
            // Con materiales "otros" el volumen final se define en el diálogo de
            // lanzamiento: el campo del panel no aplica y no debe bloquear el botón.
            panel.setVolumenManualEnabled(hayPendientes && !hayOtros);
            panel.setLanzarEnabled(hayPendientes && (hayOtros || volumenManualDentroDeCapacidad(autoclave)));
            panel.setFinalizarEnabled(false);
            panel.setMarcarFalloEnabled(false);
            panel.setQuitarEnabled(hayPendientes);
        }
    }

    /**
     * Actualiza el estado del botón Lanzar según el volumen manual ingresado.
     * Se invoca en tiempo real via el listener del campo de texto.
     */
    private void actualizarBotonLanzarPorVolumen() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return;
        List<MaterialLoteItem> pendientes = pendientesPorAutoclave.getOrDefault(
                autoclaveSeleccionado.getNombre(), List.of());
        boolean hayPendientes = !pendientes.isEmpty();
        panel.setLanzarEnabled(hayPendientes &&
                (contieneOtros(pendientes) || volumenManualDentroDeCapacidad(autoclaveSeleccionado)));
    }

    /**
     * Retorna true si el volumen manual es válido (>= 0 y <= capacidad del autoclave).
     */
    private boolean volumenManualDentroDeCapacidad(AutoclaveItem autoclave) {
        int volumenManual = panel.getVolumenManual();
        if (volumenManual < 0) return false;
        return volumenManual <= autoclave.getCapacidad();
    }

    private void configurarDnD() {
        JTable tablaDisponibles = panel.getTablaDisponibles();
        JTable tablaAutoclave   = panel.getTablaAutoclave();

        if (tablaDisponibles == null || tablaAutoclave == null || MATERIAL_LOTE_FLAVOR == null) return;

        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setFillsViewportHeight(true);
        tablaDisponibles.setTransferHandler(crearHandlerDisponibles());

        tablaAutoclave.setDragEnabled(true);
        tablaAutoclave.setDropMode(DropMode.ON);
        tablaAutoclave.setFillsViewportHeight(true);
        tablaAutoclave.setTransferHandler(crearHandlerAutoclave());
    }

    // ── Tabla Disponibles: ORIGEN para drag (COPY), DESTINO para devolver ─────

    private MultiRowTableTransferHandler<MaterialLoteItem> crearHandlerDisponibles() {
        return new MultiRowTableTransferHandler.Builder<MaterialLoteItem>(MATERIAL_LOTE_FLAVOR)
                .sourceActions(TransferHandler.COPY)
                .selectionSupplier(panel::getMaterialesDisponiblesSeleccionados)
                .onImport(this::quitarMaterialesDePendientes) // ya refresca la vista
                .build();
    }

    // ── Tabla Autoclave: DESTINO para drop, ORIGEN para devolver (MOVE) ───────

    private MultiRowTableTransferHandler<MaterialLoteItem> crearHandlerAutoclave() {
        return new MultiRowTableTransferHandler.Builder<MaterialLoteItem>(MATERIAL_LOTE_FLAVOR)
                .sourceActions(TransferHandler.MOVE)
                .selectionSupplier(this::seleccionAutoclaveParaArrastre)
                .canImportExtra(support -> autoclaveSeleccionado != null
                        && !autoclaveSeleccionado.isOcupado()
                        && !arrastrandoDesdeAutoclave)
                // invokeLater: el diálogo de cantidad no debe bloquear el EDT del drop.
                .onImport(items -> SwingUtilities.invokeLater(() -> agregarMateriales(items)))
                .onExportDone(action -> {
                    arrastrandoDesdeAutoclave = false; // reset incondicional (aunque se aborte)
                    if (action == TransferHandler.MOVE) SwingUtilities.invokeLater(this::repintar);
                })
                .build();
    }

    private List<MaterialLoteItem> seleccionAutoclaveParaArrastre() {
        List<MaterialLoteItem> seleccion = panel.getMaterialesAutoclaveSeleccionados();
        if (!seleccion.isEmpty()) arrastrandoDesdeAutoclave = true;
        return seleccion;
    }

    /**
     * Alta de una tanda de materiales al autoclave seleccionado. Por cada ítem
     * pide la cantidad (un diálogo secuencial); cancelar uno saltea solo ese ítem
     * y continúa con el resto. La aritmética de reconciliación vive en
     * {@link ReconciliadorPendientes}; aquí solo se orquesta la UI.
     */
    private void agregarMateriales(List<MaterialLoteItem> items) {
        if (items == null || items.isEmpty() || autoclaveSeleccionado == null) return;
        String nombre = autoclaveSeleccionado.getNombre();

        for (MaterialLoteItem item : items) {
            if (item == null) continue;
            Integer cantidad = pedirCantidad(item);
            if (cantidad == null) continue; // cancelado → saltear solo este ítem

            EstadoStaging estado = reconciliador.alta(estadoStaging(nombre), item, cantidad);
            aplicarEstado(nombre, estado);

            OcupacionAutoclave ocupacion = new OcupacionAutoclave(
                    reconciliador.capacidadUsada(estado.getPendientes()),
                    autoclaveSeleccionado.getCapacidad());
            if (ocupacion.estaSobrecargado()) {
                panel.mostrarAdvertencia(
                        "El volumen calculado supera la capacidad del autoclave.\n" +
                        "Puede ajustar el volumen final en el campo \"Volumen final\" antes de lanzar.");
            }
        }
        repintar();
    }

    /** Diálogo de cantidad (Swing) con checkbox "Todos"; null = cancelado. */
    private Integer pedirCantidad(MaterialLoteItem item) {
        return CantidadDialogHelper.pedirCantidad(
                panel,
                item.getDescripcion(),
                item.getCantidad(),
                (chkTodos, spinner) -> chkTodos.addActionListener(e -> {
                    if (chkTodos.isSelected()) {
                        spinner.setValue(item.getCantidad());
                        spinner.setEnabled(false);
                    } else {
                        spinner.setEnabled(true);
                    }
                })
        );
    }

    private void quitarMaterial() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return;
        List<MaterialLoteItem> seleccionados = panel.getMaterialesAutoclaveSeleccionados();
        if (seleccionados.isEmpty()) {
            panel.mostrarAdvertencia("Seleccione al menos un material para quitar.");
            return;
        }
        quitarMaterialesDePendientes(seleccionados);
    }

    /** Baja de una tanda de pendientes: delega la aritmética y refresca una vez. */
    private void quitarMaterialesDePendientes(List<MaterialLoteItem> seleccionados) {
        if (autoclaveSeleccionado == null || seleccionados == null || seleccionados.isEmpty()) return;
        String nombre = autoclaveSeleccionado.getNombre();
        aplicarEstado(nombre, reconciliador.baja(estadoStaging(nombre), seleccionados));
        repintar();
    }

    private EstadoStaging estadoStaging(String autoclaveNombre) {
        return new EstadoStaging(
                materialesDisponibles,
                pendientesPorAutoclave.getOrDefault(autoclaveNombre, List.of()));
    }

    private void aplicarEstado(String autoclaveNombre, EstadoStaging estado) {
        materialesDisponibles = new ArrayList<>(estado.getDisponibles());
        pendientesPorAutoclave.put(autoclaveNombre, new ArrayList<>(estado.getPendientes()));
    }

    private void lanzarLote() {
        if (autoclaveSeleccionado == null || autoclaveSeleccionado.isOcupado()) return;

        List<MaterialLoteItem> pendientes = pendientesPorAutoclave
                .getOrDefault(autoclaveSeleccionado.getNombre(), List.of());
        if (pendientes.isEmpty()) {
            panel.mostrarAdvertencia("Debe cargar materiales antes de lanzar el lote.");
            return;
        }

        int capacidadTotal = autoclaveSeleccionado.getCapacidad();
        Map<Integer, Integer> volumenesPorIngreso;
        int volumenFinal;

        if (contieneOtros(pendientes)) {
            // Los litros por ingreso y el volumen final se definen en el diálogo.
            Optional<DialogoVolumenesIngreso.ResultadoLanzamiento> resultado =
                    DialogoVolumenesIngreso.mostrar(
                            panel,
                            AgrupadorIngresosLote.agrupar(pendientes, equiposOtrosPorId),
                            resumenMateriales(pendientes),
                            reconciliador.capacidadUsada(pendientes),
                            capacidadTotal);
            if (!resultado.isPresent()) return;
            volumenesPorIngreso = resultado.get().getLitrosPorIngreso();
            volumenFinal        = resultado.get().getVolumenFinal();
        } else {
            volumenesPorIngreso = Map.of();
            volumenFinal        = panel.getVolumenManual();
            if (!confirmarLanzamientoOrtopedia(pendientes, volumenFinal, capacidadTotal)) return;
        }

        List<LoteMovimiento> movimientos = new ArrayList<>();
        for (MaterialLoteItem item : pendientes) {
            movimientos.add(new LoteMovimiento(
                    item.getMaterialId(), item.getEquipoId(), item.getCantidad(), item.isEsOtros()));
        }

        Lote lote = loteService.lanzarLote(autoclaveSeleccionado.getNombre(),
                capacidadTotal, volumenFinal, movimientos, volumenesPorIngreso);

        if (lote == null) {
            panel.mostrarError("Error al lanzar el lote.");
            return;
        }

        pendientesPorAutoclave.remove(autoclaveSeleccionado.getNombre());
        solicitarRefresco.run();

        if (onEstadosActualizadosListener != null) onEstadosActualizadosListener.onEstadosActualizados();
    }

    /** Confirmación previa al refactor, vigente para lotes sin materiales "otros". */
    private boolean confirmarLanzamientoOrtopedia(List<MaterialLoteItem> pendientes,
                                                  int volumenManual, int capacidadTotal) {
        if (volumenManual < 0) {
            panel.mostrarError("El campo \"Volumen final\" contiene un valor inválido.\n" +
                    "Ingrese un número entero mayor a 0.");
            return false;
        }

        OcupacionAutoclave ocupacion = new OcupacionAutoclave(volumenManual, capacidadTotal);

        if (ocupacion.estaSobrecargado()) {
            panel.mostrarError(String.format(
                    "El volumen final (%d) supera la capacidad del autoclave (%d).\n" +
                    "Ajuste el valor en el campo \"Volumen final\" antes de lanzar.",
                    volumenManual, capacidadTotal));
            return false;
        }

        int volumenCalculado = reconciliador.capacidadUsada(pendientes);

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Se lanzará el lote con los siguientes materiales:\n\n");
        for (String linea : resumenMateriales(pendientes)) {
            mensaje.append("• ").append(linea).append("\n");
        }
        mensaje.append(String.format("\nVolumen calculado (catálogo): %d\n", volumenCalculado));
        mensaje.append(String.format("Volumen final confirmado:     %d/%d (%d%%)\n",
                volumenManual, capacidadTotal, ocupacion.porcentaje()));

        if (volumenManual != volumenCalculado)
            mensaje.append("\n⚠ El volumen fue ajustado manualmente respecto al catálogo.");
        if (ocupacion.estaPocoCargado())
            mensaje.append(String.format("\n⚠ El autoclave tiene menos del %d%% de capacidad.",
                    OcupacionAutoclave.UMBRAL_ADVERTENCIA));

        mensaje.append("\n\n¿Desea continuar?");

        return panel.confirmar(mensaje.toString(), "Confirmar Lanzamiento de Lote");
    }

    private List<String> resumenMateriales(List<MaterialLoteItem> pendientes) {
        List<String> lineas = new ArrayList<>();
        for (MaterialLoteItem item : pendientes) {
            lineas.add(String.format("%s (x%d)", item.getDescripcion(), item.getCantidad()));
        }
        return lineas;
    }

    private boolean contieneOtros(List<MaterialLoteItem> items) {
        for (MaterialLoteItem item : items) {
            if (item.isEsOtros()) return true;
        }
        return false;
    }

    private static IngresoInfo ingresoDe(Equipo equipo) {
        return IngresoInfo.deOrtopedia(
                equipo.getClienteNombre(),
                equipo.getProfesionalNombre(),
                equipo.getPacienteNombre(),
                equipo.getInstitucionNombre(),
                equipo.getFechaIngreso());
    }

    /** Ingreso de origen de una fila de material; null si el equipo ya no está cargado. */
    private IngresoInfo resolverIngreso(MaterialLoteItem item) {
        if (!item.isEsOtros()) return ingresoOrtopediaPorEquipo.get(item.getEquipoId());

        EquipoOtros equipo = equiposOtrosPorId.get(item.getEquipoId());
        if (equipo == null) return null;
        return IngresoInfo.deOtros(
                equipo.getClienteNombre(),
                equipo.getTipoIngreso() == TipoIngresoOtros.REMITO,
                equipo.getRemitoId(),
                equipo.getFechaIngreso());
    }

    private String nombreClienteOtros(int equipoOtrosId) {
        EquipoOtros equipo = equiposOtrosPorId.get(equipoOtrosId);
        return equipo != null && equipo.getClienteNombre() != null ? equipo.getClienteNombre() : "";
    }

    private void finalizarLote() {
        if (autoclaveSeleccionado == null || !autoclaveSeleccionado.isOcupado()) return;

        if (!panel.confirmar(Constantes.Mensajes.CONFIRMAR_FINALIZAR_LOTE,
                Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS)) return;

        boolean exitoso = loteService.finalizarLote(autoclaveSeleccionado.getLoteId());
        if (!exitoso) {
            panel.mostrarError(Constantes.Mensajes.ERROR_FINALIZAR_LOTE);
            return;
        }

        solicitarRefresco.run();
        if (onEstadosActualizadosListener != null) onEstadosActualizadosListener.onEstadosActualizados();
    }

    private void marcarLoteFallo() {
        if (autoclaveSeleccionado == null || !autoclaveSeleccionado.isOcupado()) return;

        if (!panel.confirmar(Constantes.Mensajes.CONFIRMAR_MARCAR_LOTE_FALLO,
                Constantes.Mensajes.TITULO_CONFIRMAR_CAMBIOS)) return;

        boolean exitoso = loteService.marcarLoteFallo(autoclaveSeleccionado.getLoteId());
        if (!exitoso) {
            panel.mostrarError(Constantes.Mensajes.ERROR_MARCAR_LOTE_FALLO);
            return;
        }

        panel.mostrarInfo(Constantes.Mensajes.LOTE_FALLO_OK);
        solicitarRefresco.run();
        if (onEstadosActualizadosListener != null) onEstadosActualizadosListener.onEstadosActualizados();
    }

    private int calcularCapacidadPendiente(String autoclaveNombre) {
        return reconciliador.capacidadUsada(pendientesPorAutoclave.getOrDefault(autoclaveNombre, List.of()));
    }

    /**
     * Retorna true si hay materiales cargados en al menos un autoclave que todavía
     * no fueron lanzados como lote. Se usa como guard del botón Volver.
     */
    public boolean tieneCambiosPendientes() {
        return pendientesPorAutoclave.values().stream().anyMatch(lista -> !lista.isEmpty());
    }

    public void descartarCambiosPendientes() {
        if (!tieneCambiosPendientes()) {
            return;
        }

        pendientesPorAutoclave.clear();
        repintar();
    }
}