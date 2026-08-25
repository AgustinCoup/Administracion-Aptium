package com.example.features.lavadero.controller;

import com.example.common.constants.Constantes;
import com.example.features.lavadero.controller.helpers.StagingCiclos;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.ElementoCicloMovimiento;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lavadero.service.CatalogoJabonesService;
import com.example.features.lavadero.service.CicloLavaderoService;
import com.example.features.lavadero.service.LavarropasService;
import com.example.features.lavadero.view.DistribucionUnidadesDialog;
import com.example.features.lavadero.view.EquipoSubdivisionDialog;
import com.example.features.lavadero.view.LavarropasCard;
import com.example.features.lavadero.view.PantallaCiclos;
import com.example.features.lavadero.view.helpers.LavarropasItem;
import com.example.ui.common.dnd.LocalObjectFlavors;
import com.example.ui.common.dnd.MultiRowTableTransferHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CiclosController {

    private static final Logger log = LoggerFactory.getLogger(CiclosController.class);

    private final PantallaCiclos pantalla;
    private final CicloLavaderoService  cicloLavaderoService;
    private final LavarropasService     lavarropasService;
    private final CatalogoJabonesService catalogoJabonesService;
    private final Map<Integer, LavarropasCard> cards;

    private final StagingCiclos staging = new StagingCiclos();
    private final AtomicInteger nextInstanciaId = new AtomicInteger(1);
    private Map<Integer, CicloLavadero> ciclosActivos        = new HashMap<>();
    private List<ElementoCicloItem>     elementosDisponibles  = new ArrayList<>();
    private List<LavarropasItem>        lavarropasItems       = new ArrayList<>();

    /**
     * Lavarropas del que salió el arrastre en curso; {@code null} si no se está
     * arrastrando desde una card. Un solo campo cubre las dos necesidades: sirve de
     * flag anti-rebote (una card no acepta lo que salió de otra card) y de origen para
     * la baja, sin que puedan desincronizarse entre sí.
     */
    private Integer lavarropasArrastre = null;

    public static final DataFlavor ELEMENTO_CICLO_FLAVOR = LocalObjectFlavors.forList();

    public CiclosController(PantallaCiclos pantalla, CicloLavaderoService cicloLavaderoService,
                             LavarropasService lavarropasService,
                             CatalogoJabonesService catalogoJabonesService) {
        this.pantalla = pantalla;
        this.cicloLavaderoService   = cicloLavaderoService;
        this.lavarropasService      = lavarropasService;
        this.catalogoJabonesService = catalogoJabonesService;
        this.cards    = pantalla.getAllCards();
        inicializarEventos();
        cargarDatos();
    }

    private void inicializarEventos() {
        // El catálogo de jabones no cambia en runtime: se puebla una sola vez
        // para no resetear la selección del usuario en cada refresh.
        List<JabonCatalogo> jabones = catalogoJabonesService.obtenerTodos();
        for (Map.Entry<Integer, LavarropasCard> entry : cards.entrySet()) {
            int num = entry.getKey();
            LavarropasCard card = entry.getValue();
            card.setJabones(jabones);
            configurarDnDCard(num, card);
            card.setOnAccion(() -> {
                if (card.estaActivo()) finalizarCiclo(num);
                else lanzarCiclo(num);
            });
            card.setOnConfiguracionChanged(() -> card.actualizarBtnAccion());
        }

        pantalla.getBtnLanzarTodos().addActionListener(e -> lanzarTodos());
        pantalla.getBtnFinalizarTodos().addActionListener(e -> finalizarTodos());
        pantalla.getBtnDescartarTodos().addActionListener(e -> {
            if (tienePendientes() && pantalla.confirmar(
                    Constantes.Mensajes.GUARD_CICLOS_CAMBIOS,
                    Constantes.Mensajes.TITULO_CAMBIOS_SIN_CONFIRMAR)) {
                descartarPendientes();
            }
        });

        pantalla.setGuardVolver(
            this::tienePendientes,
            Constantes.Mensajes.GUARD_CICLOS_CAMBIOS,
            this::descartarPendientes
        );

        pantalla.addComponentListener(new ComponentAdapter() {
            private boolean dndConfigurado = false;

            @Override
            public void componentResized(ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
                }
            }

            @Override
            public void componentShown(ComponentEvent e) {
                if (!dndConfigurado) {
                    SwingUtilities.invokeLater(() -> { configurarDnD(); dndConfigurado = true; });
                }
                cards.values().forEach(LavarropasCard::colapsarSiPuede);
            }
        });
    }

    /**
     * Punto de entrada al abrir la pantalla: resetea la configuración de las cards
     * libres (una card con ciclo activo no se toca, muestra lo que hay adentro del
     * lavarropas) y recién después carga los datos. {@link #cargarDatos()} solo no
     * alcanza porque también se llama tras lanzar/finalizar/devolver, y resetear ahí
     * borraría lo que el operador está tipeando en otra card.
     */
    public void abrirPantalla() {
        for (Map.Entry<Integer, LavarropasCard> entry : cards.entrySet()) {
            if (!ciclosActivos.containsKey(entry.getKey())) {
                entry.getValue().resetConfiguracion();
            }
        }
        cargarDatos();
    }

    public void cargarDatos() {
        ciclosActivos = cicloLavaderoService.obtenerCiclosActivosPorLavarropas();

        List<ElementoCicloItem> dbDisponibles = cicloLavaderoService.obtenerElementosDisponiblesParaCiclo();
        elementosDisponibles = staging.aplicarSobreDisponibles(dbDisponibles);

        List<Lavarropas> lavarropasLista = lavarropasService.obtenerTodos();
        lavarropasItems = new ArrayList<>();
        for (Lavarropas lv : lavarropasLista) {
            CicloLavadero activo = ciclosActivos.get(lv.getNumero());
            boolean ocupado = activo != null;
            Integer cicloId = ocupado ? activo.getId() : null;
            lavarropasItems.add(new LavarropasItem(lv.getNumero(), lv.getCapacidadLitros(), ocupado, cicloId));
        }

        pantalla.setElementosDisponibles(elementosDisponibles);
        actualizarTodasLasCards();
    }

    private void actualizarTodasLasCards() {
        Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
        for (Map.Entry<Integer, LavarropasCard> entry : cards.entrySet()) {
            int num = entry.getKey();
            LavarropasCard card = entry.getValue();
            if (ciclosActivos.containsKey(num)) {
                List<ElementoCicloItem> items =
                    cicloLavaderoService.obtenerElementosDeCiclo(ciclosActivos.get(num).getId());
                card.setModoActivo(ciclosActivos.get(num).getId());
                card.setItems(items, Collections.emptyMap());
            } else {
                List<ElementoCicloItem> pending = staging.pendientesDe(num);
                card.setModoStaging();
                card.setItems(pending, fracciones);
            }
            card.actualizarBtnAccion();
        }
        boolean hayPendientes = tienePendientes();
        pantalla.getBtnLanzarTodos().setEnabled(hayPendientes);
        pantalla.getBtnDescartarTodos().setEnabled(hayPendientes);
        pantalla.getBtnFinalizarTodos().setEnabled(!ciclosActivos.isEmpty());
    }

    private void configurarDnD() {
        JTable tablaDisponibles = pantalla.getTablaDisponibles();
        if (tablaDisponibles == null || ELEMENTO_CICLO_FLAVOR == null) return;
        tablaDisponibles.setDragEnabled(true);
        tablaDisponibles.setDropMode(DropMode.ON);
        tablaDisponibles.setFillsViewportHeight(true);
        tablaDisponibles.setTransferHandler(crearHandlerDisponibles());
    }

    private void configurarDnDCard(int num, LavarropasCard card) {
        if (ELEMENTO_CICLO_FLAVOR == null) return;
        JTable tabla = card.getTabla();
        tabla.setDragEnabled(true);
        tabla.setDropMode(DropMode.ON);
        tabla.setFillsViewportHeight(true);
        tabla.setTransferHandler(crearHandlerCard(num));
    }

    // ── Tabla Disponibles: ORIGEN para drag (COPY), DESTINO para devolver ─────

    private MultiRowTableTransferHandler<ElementoCicloItem> crearHandlerDisponibles() {
        return new MultiRowTableTransferHandler.Builder<ElementoCicloItem>(ELEMENTO_CICLO_FLAVOR)
            .sourceActions(TransferHandler.COPY)
            .selectionSupplier(pantalla::getElementosDisponiblesSeleccionados)
            // Sólo acepta lo que salió de una card: un arrastre de disponibles a sí misma no es nada.
            .canImportExtra(support -> lavarropasArrastre != null)
            .onImport(items -> {
                // exportDone corre antes que el runnable diferido: leer el origen adentro daría null.
                Integer origen = lavarropasArrastre;
                if (origen == null) return;
                SwingUtilities.invokeLater(() -> devolverADisponibles(items, origen));
            })
            .build();
    }

    // ── Tabla de cada card: DESTINO para drop, ORIGEN para devolver (MOVE) ────

    private MultiRowTableTransferHandler<ElementoCicloItem> crearHandlerCard(int lavarropasNum) {
        return new MultiRowTableTransferHandler.Builder<ElementoCicloItem>(ELEMENTO_CICLO_FLAVOR)
            .sourceActions(TransferHandler.MOVE)
            .selectionSupplier(() -> seleccionCardParaArrastre(lavarropasNum))
            // El arrastre card → card está bloqueado: para mover algo hay que devolverlo primero.
            .canImportExtra(support -> !ciclosActivos.containsKey(lavarropasNum)
                                        && lavarropasArrastre == null)
            // invokeLater: los diálogos de cantidad no deben bloquear el EDT del drop.
            .onImport(items -> SwingUtilities.invokeLater(() -> procesarDrop(items, lavarropasNum)))
            // Reset incondicional: si sólo se reseteara en MOVE, un arrastre abortado
            // dejaría el flag pegado y bloquearía todos los drops siguientes.
            .onExportDone(action -> lavarropasArrastre = null)
            .build();
    }

    /**
     * Ítems que una card entrega al arrastre, y registro del lavarropas de origen.
     *
     * <p>Una card con ciclo activo devuelve lista vacía: sus ítems vienen de la BD y no
     * hay staging que dar de baja, así que el arrastre no debe ni arrancar (permitirlo
     * daría un arrastre que "funciona" visualmente y no quita nada).
     */
    private List<ElementoCicloItem> seleccionCardParaArrastre(int lavarropasNum) {
        if (ciclosActivos.containsKey(lavarropasNum)) return List.of();
        List<ElementoCicloItem> seleccion = cards.get(lavarropasNum).getItemsSeleccionados();
        if (!seleccion.isEmpty()) lavarropasArrastre = lavarropasNum;
        return seleccion;
    }

    // ── Agregar elementos ─────────────────────────────────────────────────────

    /**
     * Alta de una tanda de elementos en un lavarropas. Por cada ítem se abre su
     * diálogo <b>en secuencia</b>; cancelar uno saltea sólo ese ítem y sigue con el
     * resto.
     *
     * <p>El refresco va una sola vez al final, no dentro de cada alta: recalcula la
     * {@code cantidadEnCiclo} de los ítems que el propio bucle todavía está usando.
     */
    private void procesarDrop(List<ElementoCicloItem> items, int lavarropasNum) {
        if (items == null || items.isEmpty()) return;
        for (ElementoCicloItem item : items) {
            if (item == null) continue;
            if (item.isEquipo()) procesarDropEquipo(item, lavarropasNum);
            else                 procesarDropRegular(item, lavarropasNum);
        }
        refrescarDisponiblesYCards();
    }

    private void procesarDropRegular(ElementoCicloItem item, int lavarropasNum) {
        int max = disponibleParaRepartir(item);
        if (max <= 0) return;
        int k = (max == 1) ? 1 : seleccionarSubcantidad(item, max);
        if (k <= 0) return;
        staging.agregarRegular(lavarropasNum, item, k);
    }

    private void procesarDropEquipo(ElementoCicloItem item, int lavarropasNum) {
        int max = disponibleParaRepartir(item);
        if (max <= 0) return;
        int k = (max == 1) ? 1 : seleccionarSubcantidad(item, max);
        if (k <= 0) return;
        for (int unidad = 1; unidad <= k; unidad++) {
            abrirDialogoSubdivisionUnidad(item, lavarropasNum, unidad, k);
        }
    }

    /**
     * Unidades del elemento que todavía se pueden repartir. Se calcula contra el
     * staging y no contra el {@code cantidadEnCiclo} del ítem arrastrado, que sólo
     * se actualiza en el refresco: dentro de una tanda de drops ya está viejo.
     */
    private int disponibleParaRepartir(ElementoCicloItem item) {
        return item.getCantidadDisponible() - staging.cantidadStaged(item);
    }

    private int seleccionarSubcantidad(ElementoCicloItem item, int max) {
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(pantalla);
        DistribucionUnidadesDialog dlg = new DistribucionUnidadesDialog(
            frame, item.getElementoNombre(), item.getClienteNombre(), max);
        dlg.setVisible(true);
        return dlg.getCantidad();
    }

    private void abrirDialogoSubdivisionUnidad(ElementoCicloItem equipo, int preSelected,
                                                int unidad, int totalUnidades) {
        List<LavarropasItem> candidatos = lavarropasItems.stream()
            .filter(lv -> !ciclosActivos.containsKey(lv.getNumero()))
            .collect(Collectors.toList());
        if (candidatos.isEmpty()) {
            pantalla.mostrarAdvertencia("No hay lavarropas libres para subdividir el equipo.");
            return;
        }
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(pantalla);
        EquipoSubdivisionDialog dlg =
            new EquipoSubdivisionDialog(frame, equipo, candidatos, preSelected, unidad, totalUnidades);
        dlg.setVisible(true);
        List<Integer> seleccionados = dlg.getSeleccionados();
        if (seleccionados.isEmpty()) return;
        int instanciaId = nextInstanciaId.getAndIncrement();
        for (int num : seleccionados) {
            ElementoCicloItem copia = new ElementoCicloItem(
                equipo.getElementoClasificacionId(), equipo.getIngresoId(),
                equipo.getElementoNombre(), equipo.getCantidadTotal(),
                equipo.getCantidadYaProcesada(), equipo.getClienteNombre(),
                ElementoCicloItem.CATEGORIA_EQUIPO
            );
            copia.setInstanciaId(instanciaId);
            copia.setCantidadEnCiclo(1);
            staging.agregarFraccionEquipo(num, copia);
        }
    }

    // ── Devolver elementos a disponibles ──────────────────────────────────────

    /**
     * Baja de una tanda de elementos arrastrados de una card a la tabla de disponibles.
     * La aritmética vive en {@link StagingCiclos}; acá sólo se confirma el alcance con
     * el usuario y se refresca una sola vez al final.
     */
    private void devolverADisponibles(List<ElementoCicloItem> items, int lavarropasOrigen) {
        if (items == null || items.isEmpty()) return;
        if (!confirmarDeshacerSubdivisiones(items)) return;
        staging.quitar(items, lavarropasOrigen);
        refrescarDisponiblesYCards();
    }

    /**
     * Devolver una fracción quita el equipo de <b>todos</b> los lavarropas en los que
     * se repartió. Si la selección toca alguna subdivisión de más de un lavarropas hay
     * que avisarlo antes de aplicarla; si no toca ninguna, no se pregunta nada.
     */
    private boolean confirmarDeshacerSubdivisiones(List<ElementoCicloItem> items) {
        Map<Integer, Integer> repartidos = new LinkedHashMap<>();
        for (ElementoCicloItem item : items) {
            if (item == null || !item.isEquipo() || item.getInstanciaId() == null) continue;
            int enCuantos = staging.lavarropasDeInstancia(item.getInstanciaId());
            if (enCuantos > 1) repartidos.put(item.getInstanciaId(), enCuantos);
        }
        if (repartidos.isEmpty()) return true;

        String mensaje = repartidos.size() == 1
            ? String.format(Constantes.Mensajes.CONFIRMAR_DESHACER_SUBDIVISION,
                            repartidos.values().iterator().next())
            : String.format(Constantes.Mensajes.CONFIRMAR_DESHACER_SUBDIVISIONES, repartidos.size());
        return pantalla.confirmar(mensaje, Constantes.Mensajes.TITULO_DESHACER_SUBDIVISION);
    }

    private void refrescarDisponiblesYCards() {
        List<ElementoCicloItem> dbDisponibles = cicloLavaderoService.obtenerElementosDisponiblesParaCiclo();
        elementosDisponibles = staging.aplicarSobreDisponibles(dbDisponibles);
        pantalla.setElementosDisponibles(elementosDisponibles);
        actualizarTodasLasCards();
    }

    // ── Lanzar / Finalizar ────────────────────────────────────────────────────

    private void lanzarCiclo(int num) {
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_LANZAR_CICLO,
                Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;
        ejecutarLanzamiento(num);
        cargarDatos();
    }

    private void ejecutarLanzamiento(int num) {
        LavarropasCard card = cards.get(num);
        List<ElementoCicloItem> pendientes = staging.pendientesDe(num);
        if (pendientes.isEmpty()) return;

        TipoLavado tipoLavado = card.getTipoLavado();
        if (tipoLavado == null) {
            pantalla.mostrarError("Lavarropas #" + num + ": seleccione el tipo de lavado.");
            return;
        }
        BigDecimal litrosJabon = card.getLitrosJabon();
        if (litrosJabon == null) {
            pantalla.mostrarError("Lavarropas #" + num + ": ingrese los mililitros de jabón.");
            return;
        }
        ConfiguracionCiclo config = new ConfiguracionCiclo(tipoLavado, card.getJabon(), litrosJabon,
            card.isSuavizante(), card.isPotenciador(), card.getLitrosTotales());

        List<ElementoCicloMovimiento> movimientos = new ArrayList<>();
        for (ElementoCicloItem item : pendientes) {
            movimientos.add(new ElementoCicloMovimiento(
                item.getElementoClasificacionId(), item.getCantidadEnCiclo()));
        }
        try {
            cicloLavaderoService.lanzarCiclo(num, config, movimientos);
            staging.limpiarLavarropas(num);
        } catch (Exception e) {
            log.error("Error al lanzar ciclo en lavarropas {}", num, e);
            pantalla.mostrarError(Constantes.Mensajes.ERROR_LANZAR_CICLO);
        }
    }

    private void lanzarTodos() {
        List<Integer> conPendientes = staging.lavarropasConPendientes();
        if (conPendientes.isEmpty()) return;
        if (!pantalla.confirmar(
                "¿Lanzar " + conPendientes.size() + " ciclo(s) de lavado?",
                Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;
        for (int num : conPendientes) {
            ejecutarLanzamiento(num);
        }
        cargarDatos();
    }

    private void finalizarCiclo(int num) {
        if (ciclosActivos.get(num) == null) return;
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_FINALIZAR_CICLO,
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;
        ejecutarFinalizacion(num);
        cargarDatos();
    }

    private void finalizarTodos() {
        List<Integer> conActivos = ciclosActivos.keySet().stream()
            .sorted().collect(Collectors.toList());
        if (conActivos.isEmpty()) return;
        if (!pantalla.confirmar(
                "¿Finalizar " + conActivos.size() + " ciclo(s) activo(s)?",
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;
        for (int num : conActivos) {
            ejecutarFinalizacion(num);
        }
        cargarDatos();
    }

    private void ejecutarFinalizacion(int num) {
        CicloLavadero ciclo = ciclosActivos.get(num);
        if (ciclo == null) return;
        try {
            cicloLavaderoService.finalizarCiclo(ciclo.getId());
        } catch (Exception e) {
            log.error("Error al finalizar ciclo {}", ciclo.getId(), e);
            pantalla.mostrarError(Constantes.Mensajes.ERROR_FINALIZAR_CICLO);
        }
    }

    // ── Pendientes ────────────────────────────────────────────────────────────

    public boolean tienePendientes() {
        return staging.hayPendientes();
    }

    public void descartarPendientes() {
        staging.limpiar();
        cargarDatos();
    }
}
