package com.example.app.ui;

import com.example.app.AppContext;
import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.features.equipos.controller.helpers.PaginasEquipos;
import com.example.features.equipos.ortopedias.controller.EstadoProcesosController;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.equipos.service.CdeConsultaService;
import com.example.features.equipos.ortopedias.controller.CorreccionesController;
import com.example.features.equipos.common.controller.EquiposParaEntregarController;
import com.example.features.equipos.ortopedias.controller.IngresoOrtopediaController;
import com.example.features.equipos.common.controller.RegistrarEstadoController;
import com.example.features.equipos.controller.VerEquiposController;
import com.example.features.equipos.otros.controller.OtrosInputController;
import com.example.features.lavadero.controller.CiclosController;
import com.example.features.lavadero.controller.ClasificacionController;
import com.example.features.lavadero.controller.HistorialLavaderoController;
import com.example.features.lavadero.controller.LavaderoController;
import com.example.features.lavadero.controller.SalidasLavaderoController;
import com.example.features.lavadero.controller.VerCiclosController;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.service.HistorialLavaderoService;
import com.example.common.paginacion.Pagina;
import com.example.features.ajustes.controller.AjustesController;
import com.example.features.lotes.controller.LotesController;
import com.example.features.lotes.controller.VerLotesController;
import com.example.ui.events.OnEquipoGuardadoListener;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.ui.shell.PantallaPrincipal;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JOptionPane;

/**
 * Coordina la inicialización de todos los controladores y la conexión entre pantallas.
 *
 * Responsabilidades:
 * - Instanciar cada controller con su vista y los servicios que necesita.
 * - Delegar inyecciones que un controller no puede hacerse a sí mismo
 *   (p. ej. inicializar PantallaAuditoria desde CorreccionesController).
 * - Cablear los listeners de navegación entre pantallas.
 * - Construir el Runnable de refresco global que cada controller dispara al modificar datos.
 *
 * <p>Es el único punto de la UI que ve el {@link AppContext} completo: cada controller
 * recibe solo los servicios de su alcance, declarados en su constructor.
 */
public class UiCoordinator {

    private final AppContext        context;
    private final PantallaPrincipal vista;

    public UiCoordinator(AppContext context, PantallaPrincipal vista) {
        if (context == null || vista == null) {
            throw new IllegalArgumentException("Context y vista no pueden ser nulos");
        }
        this.context = context;
        this.vista = vista;
    }

    public void inicializar() {

        // ── Referencias diferidas: rompen el ciclo controller → refrescador → controller.
        //    Cada refrescador necesita a sus controllers para repartirles el snapshot,
        //    y ellos necesitan poder pedirle una lectura. Se cablean después de crear
        //    ambos; hasta entonces solicitar() es un no-op.
        //
        //    Son seis grupos con disparadores distintos, no un refresco global:
        //      · operativo          → cada guardado; la cola activa, sin histórico.
        //      · ver equipos        → al abrir "Ver Equipos"; dos páginas, una por grilla.
        //      · cde                → al abrir "Estado de procesos"; una página.
        //      · historial lotes    → al abrir "Ver Lotes".
        //      · historial ciclos   → al abrir "Ver Ciclos".
        //      · historial lavadero → al abrir "Historial".
        //    Las pantallas de consulta se releen cuando el usuario las mira; antes
        //    se releían en cada guardado incluso estando ocultas.
        //
        //    Los dos primeros de consulta eran UNO solo ("historial equipos"), que repartía el
        //    mismo snapshot a las dos pantallas del CDE. Con paginación no hay snapshot común: cada
        //    una pide su página con sus propios filtros, así que un grupo que repartiera dos
        //    páginas distintas a dos pantallas distintas no sería un grupo, sería dos. Se pierde la
        //    coherencia entre ellas y está aceptado: son dos cards del CardLayout y nunca se miran
        //    juntas, así que esa coherencia era invisible.
        Disparador operativo         = new Disparador();
        Disparador verEquipos        = new Disparador();
        Disparador cde               = new Disparador();
        Disparador historialLotes    = new Disparador();
        Disparador historialCiclos   = new Disparador();
        Disparador historialLavadero = new Disparador();

        OnEstadosActualizadosListener refrescarEstados = operativo::solicitar;
        OnEquipoGuardadoListener      refrescarEquipos = operativo::solicitar;

        // ── Controllers ──────────────────────────────────────────────────────

        EstadoProcesosController cdeViewController = new EstadoProcesosController(
            vista.getPantallaVerCDEv2(), cde);

        RegistrarEstadoController registrarEstadoController = new RegistrarEstadoController(
            vista.getPantallaRegistrarEstado(),
            context.getEquipoOtrosService(),
            context.getMaterialService(),
            context.getEstadoValidator(),
            refrescarEstados,
            operativo);

        EquiposParaEntregarController equiposParaEntregarController =
            new EquiposParaEntregarController(
                vista.getPantallaEquiposParaEntregar(),
                context.getEquipoOtrosService(),
                context.getMaterialService(),
                context.getEstadoValidator(),
                refrescarEstados,
                operativo);

        CorreccionesController correccionesController = new CorreccionesController(
            vista.getPantallaCorrecciones(),
            context.getEquipoCorreccionService(),
            context.getEquipoOtrosCorreccionService(),
            context.getCatalogoOtrosService());

        LotesController lotesController = new LotesController(
            vista.getPantallaLotes(),
            context.getLoteService(),
            refrescarEstados,
            operativo);

        VerLotesController verLotesController = new VerLotesController(
            vista.getPantallaVerLotes(),
            context.getLoteReporteService(),
            historialLotes);

        VerCiclosController verCiclosController = new VerCiclosController(
            vista.getPantallaVerCiclos(), historialCiclos);

        HistorialLavaderoController historialLavaderoController = new HistorialLavaderoController(
            vista.getPantallaHistorialLavadero(),
            context.getHistorialLavaderoService(),
            historialLavadero);

        VerEquiposController verEquiposController = new VerEquiposController(
            vista.getPantallaVerEquipos(),
            context.getEquipoOtrosService(),
            context.getClienteService(),
            context.getInstitucionService(),
            context.getEquipoReporteService(),
            context.getEquipoOtrosReporteService(),
            verEquipos);

        // ── Inyección en PantallaAuditoria ───────────────────────────────────
        correccionesController.inicializarPantallaAuditoria(vista.getPantallaAuditoria());

        // ── Navegación: botón "Ver Auditoría" en PantallaCorrecciones ────────
        correccionesController.setOnVerAuditoria(() ->
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.AUDITORIA));

        // ── Refresco por grupo ───────────────────────────────────────────────
        operativo.cablear(crearRefrescadorOperativo(
            registrarEstadoController, equiposParaEntregarController, lotesController));

        verEquipos.cablear(crearRefrescadorVerEquipos(verEquiposController));

        cde.cablear(crearRefrescadorCde(cdeViewController));

        historialLotes.cablear(crearRefrescadorHistorialLotes(verLotesController));

        historialCiclos.cablear(crearRefrescadorHistorialCiclos(verCiclosController));

        historialLavadero.cablear(crearRefrescadorHistorialLavadero(historialLavaderoController));

        correccionesController.setOnCambiosAplicados(operativo);

        new IngresoOrtopediaController(
            vista.getPanelIngresoOrtopedia(),
            context.getClienteService(),
            context.getCatalogoService(),
            context.getProfesionalService(),
            context.getInstitucionService(),
            context.getEquipoService(),
            vista.getNavegador(),
            vista.getContenedor(),
            refrescarEquipos
        );

        new OtrosInputController(
            vista.getPanelIngresoOtros(),
            context.getClienteService(),
            context.getCatalogoOtrosService(),
            context.getEquipoOtrosService(),
            vista.getNavegador(),
            vista.getContenedor(),
            refrescarEquipos
        );

        new LavaderoController(
            vista.getPantallaIngresoLavadero(),
            context.getClienteService(),
            context.getLavaderoService(),
            vista.getNavegador(),
            vista.getContenedor(),
            refrescarEquipos
        );

        ClasificacionController clasificacionController = new ClasificacionController(
            vista.getPantallaClasificacionLavadero(),
            context.getLavaderoService(),
            context.getClasificacionLavaderoService(),
            vista.getNavegador(),
            vista.getContenedor(),
            refrescarEquipos
        );

        vista.getPantallaLavadero().getBtnClasificar().addActionListener(e -> {
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.CLASIFICACION_LAVADERO);
            clasificacionController.cargarIngresosSinClasificar();
        });

        CiclosController ciclosController = new CiclosController(
            vista.getPantallaCiclos(),
            context.getCicloLavaderoService(),
            context.getLavarropasService(),
            context.getCatalogoJabonesService());

        vista.getPantallaLavadero().getBtnCiclos().addActionListener(e -> {
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.CICLOS_LAVADERO);
            ciclosController.abrirPantalla();
        });

        vista.getPantallaLavadero().getBtnVerCiclos().addActionListener(e ->
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.VER_CICLOS_LAVADERO));

        // Sin llamada de carga extra: el componentShown del controller restablece los filtros
        // y pide el refresco, igual que "Ver Ciclos".
        vista.getPantallaLavadero().getBtnHistorial().addActionListener(e ->
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.HISTORIAL_LAVADERO));

        // Derivar al CDE crea ingresos en la cola operativa: por eso este controller recibe
        // el disparador `operativo` y no `refrescarEquipos`.
        SalidasLavaderoController salidasController = new SalidasLavaderoController(
            vista.getPantallaSalidasLavadero(), context.getSalidaLavaderoService(), operativo);

        vista.getPantallaLavadero().getBtnSalidas().addActionListener(e -> {
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.SALIDAS_LAVADERO);
            salidasController.cargarDatos();
        });

        AjustesController ajustesController = new AjustesController(
            vista.getPantallaAjustes(), context.getClienteService(), context.getActualizacionService());
        ajustesController.setOnMutacion(operativo);
        ajustesController.chequearActualizacionesAlIniciar();

        // Primer pintado: los controllers ya no leen en su constructor, así que la
        // UI aparece vacía y se puebla cuando llega esta primera lectura. Solo el
        // grupo operativo; las pantallas de consulta leen al abrirse, y ninguna
        // está visible al arrancar (el CardLayout muestra el menú).
        operativo.solicitar();
    }

    /** Las tres pantallas de la cola de trabajo, coherentes dentro del mismo bloque de UI. */
    private RefrescadorPantallas<DatosOperativos> crearRefrescadorOperativo(
        RegistrarEstadoController     registrar,
        EquiposParaEntregarController entregar,
        LotesController               lotes
    ) {
        LectorDatosOperativos lector = new LectorDatosOperativos(
            context.getEquipoService(),
            context.getEquipoOtrosService(),
            context.getAutoclaveService(),
            context.getCatalogoService(),
            context.getLoteService());

        Consumer<DatosOperativos> repartir = datos -> {
            registrar.pintar(datos);
            entregar.pintar(datos);
            lotes.pintar(datos);
        };

        return new RefrescadorPantallas<>(
            "refresco-operativo", lector, repartir, this::mostrarErrorDeRefresco);
    }

    /**
     * "Ver Equipos", <b>de a una página por grilla</b>.
     *
     * <p>Era la mitad del grupo {@code refresco-historial-equipos}, que alimentaba también a
     * "Estado de procesos" desde un snapshot común. Con paginación ese snapshot no existe: cada
     * pantalla pide su página con sus filtros, así que el grupo se partió en dos. <b>Las dos dejan
     * de estar garantizadamente coherentes entre sí, y está aceptado</b>: son dos cards del
     * {@code CardLayout}, sólo una está visible, y nunca se miran juntas.
     *
     * <p>El lector corre en el hilo de fondo y el filtro y las páginas son estado del controller,
     * que sólo se toca en el EDT. Por eso no lee campos del controller: lee la
     * {@code ConsultaEquipos} inmutable que el controller publicó — <b>en el momento de lanzar</b>,
     * no capturada al construir el lector, que congelaría la primera página para siempre.
     */
    private RefrescadorPantallas<PaginasEquipos> crearRefrescadorVerEquipos(
        VerEquiposController verEquipos
    ) {
        EquipoService      ortopedias = context.getEquipoService();
        EquipoOtrosService otros      = context.getEquipoOtrosService();

        return new RefrescadorPantallas<>(
            "refresco-ver-equipos",
            () -> verEquipos.consultaActual().leer(ortopedias, otros),
            verEquipos::pintar,
            this::mostrarErrorDeRefresco);
    }

    /**
     * "Estado de procesos", <b>de a una página</b>. La otra mitad del grupo disuelto; ver
     * {@link #crearRefrescadorVerEquipos} para por qué son dos y qué se perdió al separarlos.
     *
     * <p>Su página sale de {@code CdeConsultaDAO}, que es el que une las dos tablas: por eso este
     * grupo lee de un service propio y no de los dos de equipos.
     */
    private RefrescadorPantallas<Pagina<EquipoRegistrableInterface>> crearRefrescadorCde(
        EstadoProcesosController cde
    ) {
        CdeConsultaService service = context.getCdeConsultaService();

        return new RefrescadorPantallas<>(
            "refresco-cde",
            () -> cde.consultaActual().leer(service),
            cde::pintar,
            this::mostrarErrorDeRefresco);
    }

    /** La pantalla que consulta el histórico de lotes. */
    private RefrescadorPantallas<HistorialLotes> crearRefrescadorHistorialLotes(
        VerLotesController verLotes
    ) {
        LectorHistorialLotes lector = new LectorHistorialLotes(
            context.getAutoclaveService(),
            context.getLoteService());

        return new RefrescadorPantallas<>(
            "refresco-historial-lotes", lector, verLotes::pintar, this::mostrarErrorDeRefresco);
    }

    /** La pantalla que consulta el histórico de ciclos de lavado. */
    private RefrescadorPantallas<List<CicloLavadero>> crearRefrescadorHistorialCiclos(
        VerCiclosController verCiclos
    ) {
        return new RefrescadorPantallas<>(
            "refresco-historial-ciclos",
            context.getCicloLavaderoService()::obtenerTodosLosCiclos,
            verCiclos::pintar,
            this::mostrarErrorDeRefresco);
    }

    /**
     * La pantalla que consulta el historial del lavadero, <b>de a una página</b>.
     *
     * <p>El lector corre en el hilo de fondo y el filtro y la página son estado del controller,
     * que sólo se toca en el EDT. Por eso no lee campos del controller: lee la
     * {@code ConsultaHistorial} inmutable que el controller publicó —leerla en el momento de
     * lanzar, y no capturarla al construir el lector, es lo que hace que el botón de página
     * funcione: capturada, la primera página quedaría congelada para siempre.</p>
     */
    private RefrescadorPantallas<Pagina<IngresoHistorial>> crearRefrescadorHistorialLavadero(
        HistorialLavaderoController historial
    ) {
        HistorialLavaderoService service = context.getHistorialLavaderoService();

        return new RefrescadorPantallas<>(
            "refresco-historial-lavadero",
            () -> historial.consultaActual().leer(service),
            historial::pintar,
            this::mostrarErrorDeRefresco);
    }

    /**
     * Handle que los controllers reciben para pedir un refresco, cableado al
     * refrescador real recién cuando este existe.
     *
     * <p>Existe solo para romper el ciclo de construcción: un controller no puede
     * recibir por constructor un refrescador que a su vez lo necesita a él.
     */
    private static final class Disparador implements Runnable {

        private RefrescadorPantallas<?> refrescador;

        void cablear(RefrescadorPantallas<?> refrescador) {
            this.refrescador = refrescador;
        }

        void solicitar() {
            if (refrescador != null) refrescador.solicitar();
        }

        @Override public void run() { solicitar(); }
    }

    /**
     * Un refresco fallido deja las pantallas con datos viejos sin que se note.
     * Se avisa: {@link com.example.ui.common.TareaUI} ya lo dejó en el log a ERROR.
     */
    private void mostrarErrorDeRefresco(Throwable causa) {
        JOptionPane.showMessageDialog(
            vista,
            "No se pudieron actualizar los datos en pantalla.\n" +
            "Lo que ves puede estar desactualizado.\n\n" + causa.getMessage(),
            "Error al actualizar",
            JOptionPane.ERROR_MESSAGE);
    }
}
