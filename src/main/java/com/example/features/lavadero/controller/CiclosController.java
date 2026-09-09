package com.example.features.lavadero.controller;

import com.example.common.constants.Constantes;
import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.LavarropasOcupadoException;
import com.example.common.exception.SaldoConsumidoException;
import com.example.common.exception.ValidationException;
import com.example.features.lavadero.controller.helpers.ConstructorVistaCiclos;
import com.example.features.lavadero.controller.helpers.ConstructorVistaCiclos.VistaCard;
import com.example.features.lavadero.controller.helpers.ConstructorVistaCiclos.VistaCiclos;
import com.example.features.lavadero.controller.helpers.DatosCiclos;
import com.example.features.lavadero.controller.helpers.StagingCiclos;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.JabonCatalogo;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;
import com.example.features.lavadero.model.TipoLavado;
import com.example.features.lavadero.service.CatalogoJabonesService;
import com.example.features.lavadero.service.CicloLavaderoService;
import com.example.features.lavadero.service.LavarropasService;
import com.example.features.lavadero.view.DistribucionUnidadesDialog;
import com.example.features.lavadero.view.EquipoSubdivisionDialog;
import com.example.features.lavadero.view.LavarropasCard;
import com.example.features.lavadero.view.PantallaCiclos;
import com.example.features.lavadero.view.helpers.LavarropasItem;
import com.example.ui.common.TareaUI;
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
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Cablea la pantalla de Ciclos: qué se reparte en cada lavarropas y cuándo se lanza o
 * finaliza un ciclo.
 *
 * <p><b>Todo acceso a la base va por {@link TareaUI}</b>, lecturas y escrituras por igual:
 * el hilo de la interfaz no toca JDBC (ver {@code EdtGuard}).
 *
 * <p><b>Regla dura:</b> el estado en memoria de este controller —{@link #staging},
 * {@link #ciclosActivos}, {@link #elementosDisponibles}, {@link #lavarropasItems},
 * {@link #nextInstanciaId}, {@link #lavarropasArrastre}— se lee y se escribe <b>sólo</b> en
 * el hilo de la interfaz. El arrastre y los diálogos de subdivisión lo tocan, así que nada
 * de eso puede moverse al hilo de fondo: a fondo van únicamente los llamados al service, y
 * lo que devuelven se aplica en {@code pintar}.
 */
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

    /** El catálogo de jabones no cambia en runtime: se lee una sola vez, al abrir la pantalla. */
    private boolean jabonesCargados = false;

    /**
     * Carga en vuelo, para descartar su resultado si se dispara otra. Dos refrescos rápidos
     * —dos arrastres seguidos— no deben pintar fuera de orden.
     */
    private TareaUI.Ejecucion cargaEnCurso = null;

    /**
     * Hay una escritura en vuelo, así que los botones tienen que seguir apagados aunque una
     * lectura falle. Lo lee {@link #rehabilitarAcciones()}: sin esta bandera, un F5 fallido
     * lanzado mientras se escribe volvería a encender "Lanzar todos" con el staging intacto, que
     * es exactamente el doble envío que {@link #deshabilitarAcciones()} existe para impedir.
     */
    private boolean escrituraEnVuelo = false;

    /**
     * Lavarropas cuyo descarte el operador <b>ya</b> vio explicado en el cartel de un choque, así
     * que la relectura que viene detrás no tiene que abrir un segundo modal para contarle lo
     * mismo.
     *
     * <p>Es un conjunto y no una bandera porque los dos carteles no dicen lo mismo cuando lo
     * descartado era la fracción de un equipo repartido: ahí la relectura vacía también cards que
     * <b>siguen libres</b>, y eso el cartel del choque no lo menciona. Guardando cuáles se
     * avisaron, el aviso se saltea sólo si el descarte no se salió de ahí.</p>
     *
     * <p>Se consume en la primera relectura que termine, haya habido descarte o no (ver
     * {@link #avisarStagingDescartado}), y también si esa relectura falla: si quedara puesto, se
     * tragaría el próximo aviso legítimo. El descarte por ocupación también pasa sin que nadie
     * haya apretado Lanzar —un F5, un arrastre— y ahí el aviso es lo único que hay.</p>
     */
    private Set<Integer> lavarropasYaAvisados = Set.of();

    public static final DataFlavor ELEMENTO_CICLO_FLAVOR = LocalObjectFlavors.forList();

    /**
     * Qué ciclos quedaron efectivamente finalizados y cuántos fallaron. Finalizar de a varios no
     * corta ante el primer fallo —cada ciclo se cierra solo, sin invariantes compartidos— así que
     * lo que ya se escribió está en la base y hay que reflejarlo. Lanzar no usa esto: una tanda
     * de lanzamiento es todo o nada (ver {@link #lanzar}).
     */
    private record ResultadoEscritura(List<Integer> exitosos, int fallidos, int conflictos) {

        boolean huboFallos() {
            return fallidos > 0;
        }

        /**
         * Si <b>todo</b> lo que falló fue un choque, el mensaje del choque es el que corresponde:
         * dice que otro ya lo finalizó y que la ropa está en Salidas. El genérico
         * ("Intente nuevamente") sería falso — reintentar va a fallar igual.
         */
        boolean soloHuboConflictos() {
            return fallidos > 0 && conflictos == fallidos;
        }
    }

    public CiclosController(PantallaCiclos pantalla, CicloLavaderoService cicloLavaderoService,
                             LavarropasService lavarropasService,
                             CatalogoJabonesService catalogoJabonesService) {
        this.pantalla = pantalla;
        this.cicloLavaderoService   = cicloLavaderoService;
        this.lavarropasService      = lavarropasService;
        this.catalogoJabonesService = catalogoJabonesService;
        this.cards    = pantalla.getAllCards();
        // Sin I/O en el constructor: la pantalla no está visible al arrancar y
        // abrirPantalla() carga todo cuando el operador entra (patrón de la Fase 2).
        inicializarEventos();
    }

    private void inicializarEventos() {
        for (Map.Entry<Integer, LavarropasCard> entry : cards.entrySet()) {
            int num = entry.getKey();
            LavarropasCard card = entry.getValue();
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

        // Botón "Actualizar" / F5: recargar() (no abrirPantalla()) para no pisar la config
        // que el operador está tipeando. El guard avisa sin descartar: la config se conserva.
        pantalla.setGuardRefresco(this::tienePendientes, Constantes.Mensajes.REFRESCO_CICLOS, null);
        pantalla.setAccionRefrescar(this::recargar);

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
     * lavarropas) y recién después carga los datos. {@link #recargar()} solo no
     * alcanza porque también se llama tras lanzar/finalizar/devolver, y resetear ahí
     * borraría lo que el operador está tipeando en otra card.
     */
    public void abrirPantalla() {
        for (Map.Entry<Integer, LavarropasCard> entry : cards.entrySet()) {
            if (!ciclosActivos.containsKey(entry.getKey())) {
                entry.getValue().resetConfiguracion();
            }
        }
        recargar();
    }

    // ── Carga ─────────────────────────────────────────────────────────────────

    /**
     * Único camino de lectura de la pantalla: ciclos activos, disponibles, lavarropas, los
     * elementos de cada ciclo en curso y —la primera vez— el catálogo de jabones salen de
     * una sola tarea de fondo. El armado de la vista queda entero en el hilo de la interfaz
     * porque necesita el staging (ver {@link ConstructorVistaCiclos}).
     *
     * <p><b>Es lo que dispara el botón "Actualizar" / F5</b>, no {@link #abrirPantalla()}:
     * {@code recargar()} respeta la config que el operador está tipeando en las cards libres,
     * {@code abrirPantalla()} la resetea. Cablear el botón a {@code abrirPantalla()} le borraría
     * ese trabajo — es el error clásico de este flujo.
     */
    public void recargar() {
        cancelarCargaEnCurso();
        // La decisión de traer los jabones se toma acá, en el EDT: si esta carga se cancela,
        // jabonesCargados sigue en false y la siguiente los vuelve a pedir.
        boolean conJabones = !jabonesCargados;
        cargaEnCurso = TareaUI.<DatosCiclos>nueva()
            .nombre("carga-ciclos-lavadero")
            .leer(() -> leerDatos(conJabones))
            .pintar(this::pintar)
            .siFalla(e -> {
                pantalla.mostrarError(Constantes.Mensajes.ERROR_CARGAR_DATOS);
                // Esta relectura no va a llegar a avisarStagingDescartado: si el pendiente
                // quedara puesto, se tragaría el próximo aviso legítimo.
                lavarropasYaAvisados = Set.of();
                rehabilitarAcciones();
            })
            .lanzar();
    }

    /**
     * Descarta la lectura en vuelo, si hay una.
     *
     * <p>Además de encadenar refrescos sin pintar fuera de orden, es lo que hay que hacer
     * <b>antes de abrir cualquier modal</b> de esta pantalla: un modal corre un bucle de eventos
     * anidado, así que un {@code pintar()} pendiente se despacha con el diálogo abierto y toca el
     * staging que ese mismo diálogo está por escribir —o le apila un cartel encima. Ninguna
     * lectura nueva arranca hasta el {@code recargar()} con que terminan esos flujos.</p>
     */
    private void cancelarCargaEnCurso() {
        if (cargaEnCurso != null) cargaEnCurso.cancelar();
    }

    /** Fuera del hilo de la interfaz. No toca ningún campo del controller. */
    private DatosCiclos leerDatos(boolean conJabones) {
        Map<Integer, CicloLavadero> activos = cicloLavaderoService.obtenerCiclosActivosPorLavarropas();

        Map<Integer, List<ElementoCicloItem>> itemsActivos = new HashMap<>();
        for (Map.Entry<Integer, CicloLavadero> entry : activos.entrySet()) {
            itemsActivos.put(entry.getKey(),
                cicloLavaderoService.obtenerElementosDeCiclo(entry.getValue().getId()));
        }

        return new DatosCiclos(
            activos,
            cicloLavaderoService.obtenerElementosDisponiblesParaCiclo(),
            lavarropasService.obtenerTodos(),
            itemsActivos,
            conJabones ? catalogoJabonesService.obtenerTodos() : List.of()
        );
    }

    private void pintar(DatosCiclos datos) {
        if (!datos.jabones().isEmpty()) {
            cards.values().forEach(card -> card.setJabones(datos.jabones()));
            jabonesCargados = true;
        }

        ciclosActivos = datos.ciclosActivos();
        VistaCiclos vista = ConstructorVistaCiclos.construir(datos, cards.keySet(), staging);
        elementosDisponibles = vista.disponibles();
        lavarropasItems      = vista.lavarropas();

        pantalla.setElementosDisponibles(elementosDisponibles);
        for (VistaCard vistaCard : vista.cards()) {
            LavarropasCard card = cards.get(vistaCard.lavarropasNumero());
            if (vistaCard.esActivo()) card.setModoActivo(vistaCard.cicloActivoId());
            else                      card.setModoStaging();
            card.setItems(vistaCard.items(), vistaCard.fracciones());
            card.actualizarBtnAccion();
        }
        pantalla.getBtnLanzarTodos().setEnabled(vista.hayPendientes());
        pantalla.getBtnDescartarTodos().setEnabled(vista.hayPendientes());
        pantalla.getBtnFinalizarTodos().setEnabled(vista.hayActivos());
        // Una escritura en vuelo manda sobre lo que acabamos de encender. La lectura que llega en
        // el medio no es sólo la fallida: F5 no está deshabilitado, así que un refresco exitoso
        // durante el guardado volvería a encender "Lanzar todos" con el staging intacto y la
        // misma tanda se podría mandar dos veces. Los reenciende el `despues` de esa escritura.
        if (escrituraEnVuelo) deshabilitarAcciones();
        pantalla.marcarActualizado();
        avisarStagingDescartado(vista.stagingDescartadoDe());
    }

    /**
     * El único caso en que esta pantalla pierde staging sin que el operador lo haya pedido: otro
     * operador ocupó un lavarropas que él tenía cargado.
     *
     * <p><b>El modal se difiere y no se abre dentro de {@code pintar}.</b> Un modal arranca un
     * bucle de eventos anidado, así que abrirlo a mitad del repintado deja la pantalla a medio
     * pintar debajo del cartel y, peor, se apila arriba de un diálogo que el operador ya tenía
     * abierto. Difiriéndolo, {@code pintar} termina —staging consistente, tablas al día— y el
     * cartel aparece sobre la pantalla ya corregida, con la ropa de vuelta en disponibles.</p>
     */
    private void avisarStagingDescartado(List<Integer> lavarropas) {
        Set<Integer> yaAvisados = lavarropasYaAvisados;
        lavarropasYaAvisados = Set.of();                 // se consume acá, haya descarte o no
        if (lavarropas.isEmpty()) return;
        if (yaAvisados.containsAll(lavarropas)) return;  // el cartel del choque ya los nombró
        String lista = lavarropas.stream().map(n -> "#" + n).collect(Collectors.joining(", "));
        SwingUtilities.invokeLater(() -> pantalla.mostrarAdvertencia(
            String.format(Constantes.Mensajes.STAGING_DESCARTADO_POR_OCUPACION, lista)));
    }

    // ── DnD ───────────────────────────────────────────────────────────────────

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
        cancelarCargaEnCurso();
        for (ElementoCicloItem item : items) {
            if (item == null) continue;
            if (item.isEquipo()) procesarDropEquipo(item, lavarropasNum);
            else                 procesarDropRegular(item, lavarropasNum);
        }
        recargar();
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
        DistribucionUnidadesDialog dlg = DistribucionUnidadesDialog.paraCiclos(
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
        cancelarCargaEnCurso();
        // Si el operador dice que no, hay que relanzar igual: la lectura que se canceló recién
        // podía ser el refresco de una escritura, y TareaUI saltea `despues` en una tarea
        // cancelada — o sea que nadie volvería a encender los botones y la pantalla quedaría
        // muerta, con las tablas viejas y sin un solo cartel. Es la misma pantalla muerta que
        // rehabilitarAcciones() vino a cerrar, entrando por otra puerta.
        if (!confirmarDeshacerSubdivisiones(items)) { recargar(); return; }
        staging.quitar(items, lavarropasOrigen);
        recargar();
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

    // ── Lanzar ────────────────────────────────────────────────────────────────

    private void lanzarCiclo(int num) {
        String bloqueo = motivoBloqueoPorInstanciaRepartida(num);
        if (bloqueo != null) { pantalla.mostrarError(bloqueo); return; }
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_LANZAR_CICLO,
                Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;
        lanzar(List.of(num));
    }

    private void lanzarTodos() {
        List<Integer> conPendientes = staging.lavarropasConPendientes();
        if (conPendientes.isEmpty()) return;
        String faltaConfig = validarConfigDeGruposRepartidos(conPendientes);
        if (faltaConfig != null) { pantalla.mostrarError(faltaConfig); return; }
        if (!pantalla.confirmar(
                "¿Lanzar " + conPendientes.size() + " ciclo(s) de lavado?",
                Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;
        lanzar(conPendientes);
    }

    /**
     * Arma en el hilo de la interfaz lo que cada lavarropas lleva al ciclo —la config de su
     * card y sus líneas de staging— y manda la tanda entera a <b>una sola</b> tarea de fondo.
     * Los lavarropas a los que les falta config se saltean con su aviso, igual que antes.
     *
     * <p>La tanda que llega al service es todo o nada: si falla, no se limpia nada del staging
     * y el operador puede reintentarla tal cual (ver {@code CicloLavaderoDAO.lanzarTanda}).
     *
     * <p><b>Salvo que el fallo sea un choque:</b> ahí el staging de la tanda se descarta entero.
     * Reintentarlo tal cual no sirve —parte de esa ropa ya se la llevó otro operador y el
     * operador no tiene cómo saber qué parte—, así que se arranca de nuevo desde lo que el
     * refresco traiga de la base. Mismo criterio que en Lotes.
     *
     * <p><b>Menos si el choque es un lavarropas ocupado</b>: ahí la ropa sigue estando, lo único
     * que se perdió es el destino. Descartar la tanda entera le borraría el reparto de los
     * lavarropas que nadie tocó. La relectura descarta sólo el del ocupado, y lo avisa (ver
     * {@link ConstructorVistaCiclos} y {@link #avisarStagingDescartado}).
     */
    private void lanzar(List<Integer> lavarropas) {
        List<String> faltantes = new ArrayList<>();
        List<LanzamientoCiclo> tanda = new ArrayList<>();
        for (int num : lavarropas) {
            prepararLanzamiento(num, faltantes).ifPresent(tanda::add);
        }
        if (!faltantes.isEmpty()) pantalla.mostrarError(String.join("\n", faltantes));
        if (tanda.isEmpty()) return;

        ejecutar("lanzar-ciclos",
            () -> escribirTanda(tanda),
            // La card se vacía acá y no en el refresco, por el mismo motivo que en finalizar: si
            // la relectura falla, una card que todavía muestra los ítems que ya se lanzaron deja
            // su botón "Lanzar" encendido (lo enciende tieneItems()), y volver a apretarlo
            // confirmaría y no haría nada. Queda vacía y con el botón apagado —no dice "OCUPADO"
            // porque el id del ciclo recién creado no vuelve hasta el refresco—, que es el estado
            // honesto: no ofrece una acción que no existe.
            lanzados -> lanzados.forEach(num -> {
                staging.limpiarLavarropas(num);
                LavarropasCard card = cards.get(num);
                card.setItems(List.of(), Map.of());
                card.actualizarBtnAccion();
            }),
            choque -> {
                // Sólo el saldo consumido invalida el trabajo en curso. El marcador es positivo
                // a propósito: ver SaldoConsumidoException.
                if (choque instanceof SaldoConsumidoException) {
                    tanda.forEach(ciclo -> staging.limpiarLavarropas(ciclo.lavarropasNumero()));
                }
                // Este cartel ya explica qué va a pasar con lo cargado en los lavarropas de la
                // tanda; el aviso del descarte que trae la relectura sería el mismo modal por
                // segunda vez. Si el descarte se sale de este conjunto —una fracción que vacía
                // cards libres— el aviso igual sale, porque eso el cartel del choque no lo dice.
                if (choque instanceof LavarropasOcupadoException) {
                    lavarropasYaAvisados = tanda.stream()
                        .map(LanzamientoCiclo::lavarropasNumero).collect(Collectors.toSet());
                }
            });
    }

    /**
     * Fuera del hilo de la interfaz. Devuelve los lavarropas que quedaron lanzados, que son
     * todos: si la tanda no se escribió entera, propaga la excepción y no devuelve nada.
     */
    private List<Integer> escribirTanda(List<LanzamientoCiclo> tanda) {
        cicloLavaderoService.lanzarTanda(tanda);
        return tanda.stream().map(LanzamientoCiclo::lavarropasNumero).toList();
    }

    /**
     * {@code Optional.empty()} si el lavarropas no tiene nada que lanzar o le falta config;
     * en el segundo caso además deja el motivo en {@code faltantes}. Los cuatro campos que se
     * piden acá son los mismos que enciende {@link LavarropasCard#tieneConfiguracionCompleta()}:
     * lo que cambia es que ahí se decide si el botón se prende y acá se dice qué falta.
     */
    private Optional<LanzamientoCiclo> prepararLanzamiento(int num, List<String> faltantes) {
        List<ElementoCicloItem> pendientes = staging.pendientesDe(num);
        if (pendientes.isEmpty()) return Optional.empty();

        LavarropasCard card = cards.get(num);
        TipoLavado tipoLavado = card.getTipoLavado();
        if (tipoLavado == null) {
            faltantes.add("Lavarropas #" + num + ": seleccione el tipo de lavado.");
            return Optional.empty();
        }
        JabonCatalogo jabon = card.getJabon();
        if (jabon == null) {
            faltantes.add("Lavarropas #" + num + ": seleccione el jabón.");
            return Optional.empty();
        }
        BigDecimal litrosJabon = card.getLitrosJabon();
        if (litrosJabon == null) {
            faltantes.add("Lavarropas #" + num + ": ingrese los mililitros de jabón.");
            return Optional.empty();
        }
        BigDecimal litrosTotales = card.getLitrosTotales();
        if (litrosTotales == null) {
            faltantes.add("Lavarropas #" + num + ": ingrese los litros totales.");
            return Optional.empty();
        }

        ConfiguracionCiclo config = new ConfiguracionCiclo(tipoLavado, jabon, litrosJabon,
            card.isSuavizante(), card.isPotenciador(), litrosTotales);

        Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
        List<LineaLanzamiento> lineas = new ArrayList<>();
        for (ElementoCicloItem item : pendientes) {
            Integer instancia = esFraccionDeEquipo(item) ? item.getInstanciaId() : null;
            lineas.add(new LineaLanzamiento(
                item.getElementoClasificacionId(), item.getCantidadEnCiclo(),
                instancia, instancia == null ? 1 : fracciones.getOrDefault(instancia, 1)));
        }
        return Optional.of(new LanzamientoCiclo(num, config, lineas));
    }

    /**
     * {@code null} si el lavarropas se puede lanzar solo; mensaje accionable si alguna de sus
     * fracciones pertenece a un equipo repartido en más de un lavarropas: esos grupos sólo
     * lanzan con "Lanzar Todos", para poder validar que todas sus cards tengan config completa
     * antes de armar la tanda (y para que la tanda las lleve juntas, que es lo que las hace
     * atómicas entre sí).
     */
    private String motivoBloqueoPorInstanciaRepartida(int num) {
        Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
        for (ElementoCicloItem item : staging.pendientesDe(num)) {
            if (esFraccionDeEquipo(item)
                    && fracciones.getOrDefault(item.getInstanciaId(), 1) > 1) {
                return String.format(Constantes.Mensajes.BLOQUEO_LANZAR_INSTANCIA_REPARTIDA,
                    num, item.getElementoNombre());
            }
        }
        return null;
    }

    /**
     * Ninguna fracción de un grupo repartido lanza si a alguna de sus cards le falta config: no
     * tiene sentido crear la instancia y lanzar la mitad del grupo mientras el resto espera.
     * Lavarropas sin fracciones repartidas no entran en esta validación (conservan el
     * comportamiento actual: se saltean individualmente si les falta config).
     */
    private String validarConfigDeGruposRepartidos(List<Integer> conPendientes) {
        Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
        for (int num : conPendientes) {
            LavarropasCard card = cards.get(num);
            for (ElementoCicloItem item : staging.pendientesDe(num)) {
                if (!esFraccionDeEquipo(item)) continue;
                if (fracciones.getOrDefault(item.getInstanciaId(), 1) <= 1) continue;
                if (!card.tieneConfiguracionCompleta()) {
                    return String.format(Constantes.Mensajes.FALTA_CONFIG_GRUPO_REPARTIDO,
                        num, item.getElementoNombre());
                }
            }
        }
        return null;
    }

    // ── Finalizar ─────────────────────────────────────────────────────────────

    private void finalizarCiclo(int num) {
        if (ciclosActivos.get(num) == null) return;
        if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_FINALIZAR_CICLO,
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;
        finalizar(List.of(num));
    }

    private void finalizarTodos() {
        List<Integer> conActivos = ciclosActivos.keySet().stream()
            .sorted().collect(Collectors.toList());
        if (conActivos.isEmpty()) return;
        if (!pantalla.confirmar(
                "¿Finalizar " + conActivos.size() + " ciclo(s) activo(s)?",
                Constantes.Mensajes.TITULO_FINALIZAR_LOTE)) return;
        finalizar(conActivos);
    }

    /** Los ids de ciclo se leen del EDT; la tanda entera se escribe en una sola tarea de fondo. */
    private void finalizar(List<Integer> lavarropas) {
        Map<Integer, Integer> ciclosPorLavarropas = new LinkedHashMap<>();
        for (int num : lavarropas) {
            CicloLavadero ciclo = ciclosActivos.get(num);
            if (ciclo != null) ciclosPorLavarropas.put(num, ciclo.getId());
        }
        if (ciclosPorLavarropas.isEmpty()) return;

        ejecutar("finalizar-ciclos",
            () -> escribirFinalizaciones(ciclosPorLavarropas),
            resultado -> {
                // El ciclo se cerró de verdad: la card vuelve a LIBRE acá y no en el refresco.
                // Si la relectura falla, dejarla en OCUPADO mostraría un ciclo que ya no existe y
                // su botón Finalizar no haría nada (finalizarCiclo corta si el ciclo no está en
                // ciclosActivos), que es la peor forma de fallar: sin efecto y sin aviso.
                resultado.exitosos().forEach(num -> {
                    ciclosActivos.remove(num);
                    LavarropasCard card = cards.get(num);
                    card.setModoStaging();
                    card.setItems(staging.pendientesDe(num), staging.fraccionesPorInstancia());
                    card.resetConfiguracion();
                });
                if (resultado.huboFallos()) {
                    pantalla.mostrarError(resultado.soloHuboConflictos()
                        ? Constantes.Mensajes.CONFLICTO_CICLO_FINALIZADO
                        : Constantes.Mensajes.ERROR_FINALIZAR_CICLO);
                }
            },
            // Finalizar no se apoya en ningún staging: no hay estado en memoria que descartar.
            // (Y hoy tampoco llega acá: el choque de finalizar se cuenta en escribirFinalizaciones.)
            choque -> { });
    }

    /**
     * Fuera del hilo de la interfaz.
     *
     * <p>El choque se cuenta aparte de los demás fallos y no se deja propagar: finalizar de a
     * varios no corta ante el primero (cada ciclo se cierra solo), pero "otro ya lo finalizó" y
     * "no se pudo finalizar" son dos cosas distintas para el operador y el cartel tiene que
     * decir cuál fue. Sin este {@code catch} aparte, {@code CONFLICTO_CICLO_FINALIZADO} sería
     * inalcanzable.</p>
     */
    private ResultadoEscritura escribirFinalizaciones(Map<Integer, Integer> ciclosPorLavarropas) {
        List<Integer> exitosos = new ArrayList<>();
        int fallidos = 0;
        int conflictos = 0;
        for (Map.Entry<Integer, Integer> entry : ciclosPorLavarropas.entrySet()) {
            try {
                cicloLavaderoService.finalizarCiclo(entry.getValue());
                exitosos.add(entry.getKey());
            } catch (ConflictoConcurrenciaException e) {
                log.warn("El ciclo {} ya estaba finalizado: {}", entry.getValue(), e.getMessage());
                fallidos++;
                conflictos++;
            } catch (Exception e) {
                log.error("Error al finalizar ciclo {}", entry.getValue(), e);
                fallidos++;
            }
        }
        return new ResultadoEscritura(exitosos, fallidos, conflictos);
    }

    // ── Mecánica común de las escrituras ──────────────────────────────────────

    /**
     * Toda escritura de esta pantalla tiene la misma forma: se escribe fuera del hilo de la
     * interfaz y lo que devolvió se aplica al estado en memoria en el EDT.
     *
     * <p>Los botones de acción se apagan mientras la escritura está en vuelo —si no, un
     * segundo click lanzaría de nuevo el mismo staging— y el refresco va en {@code despues}
     * para que también corra si falló: es el que los vuelve a encender.
     *
     * @param alConflicto qué hacer con el estado en memoria si la escritura chocó con otro
     *                    operador (típicamente descartar el staging); además siempre se muestra
     *                    el mensaje del conflicto y se recarga. Recibe el choque porque no todos
     *                    piden lo mismo: ver {@link #lanzar}
     */
    private <T> void ejecutar(String nombre, Callable<T> escritura, Consumer<T> alTerminar,
                              Consumer<ConflictoConcurrenciaException> alConflicto) {
        TareaUI.<T>nueva()
            .nombre(nombre)
            .antes(() -> { escrituraEnVuelo = true; deshabilitarAcciones(); })
            .leer(escritura)
            .pintar(alTerminar)
            .siFalla(causa -> {
                if (causa instanceof ConflictoConcurrenciaException choque) alConflicto.accept(choque);
                mostrarFallo(causa);
            })
            .despues(() -> { escrituraEnVuelo = false; recargar(); })
            .lanzar();
    }

    private void deshabilitarAcciones() {
        pantalla.getBtnLanzarTodos().setEnabled(false);
        pantalla.getBtnFinalizarTodos().setEnabled(false);
        pantalla.getBtnDescartarTodos().setEnabled(false);
        cards.values().forEach(LavarropasCard::deshabilitarAccion);
    }

    /**
     * Vuelve a encender los botones a partir del estado en memoria, sin releer nada.
     *
     * <p>Va en el fallo de {@link #recargar()}, que es el único camino por el que
     * {@link #deshabilitarAcciones()} puede quedar sin deshacerse: los apaga {@code antes} de
     * cada escritura y quien los reenciende es {@link #pintar}, que en una lectura fallida no
     * llega a correr. Sin esto la pantalla queda muerta —ni lanzar, ni finalizar, ni descartar—
     * hasta salir y volver a entrar.</p>
     *
     * <p>No corre con una escritura en vuelo: ahí los botones están apagados a propósito y el que
     * los tiene que volver a encender es el {@code despues} de esa escritura, no una lectura
     * ajena que falló (ver {@link #escrituraEnVuelo}).</p>
     */
    private void rehabilitarAcciones() {
        if (escrituraEnVuelo) return;
        boolean hayPendientes = staging.hayPendientes();
        pantalla.getBtnLanzarTodos().setEnabled(hayPendientes);
        pantalla.getBtnDescartarTodos().setEnabled(hayPendientes);
        pantalla.getBtnFinalizarTodos().setEnabled(!ciclosActivos.isEmpty());
        cards.values().forEach(LavarropasCard::actualizarBtnAccion);
    }

    private void mostrarFallo(Throwable causa) {
        if (causa instanceof ValidationException validacion) {
            pantalla.mostrarError(String.join("\n", validacion.getValidationErrors()));
            return;
        }
        pantalla.mostrarError(causa.getMessage());
    }

    /** Un equipo sin {@code instanciaId} no se repartió por subdivisión: cuenta como regular. */
    private static boolean esFraccionDeEquipo(ElementoCicloItem item) {
        return item.isEquipo() && item.getInstanciaId() != null;
    }

    // ── Pendientes ────────────────────────────────────────────────────────────

    public boolean tienePendientes() {
        return staging.hayPendientes();
    }

    public void descartarPendientes() {
        staging.limpiar();
        recargar();
    }
}
