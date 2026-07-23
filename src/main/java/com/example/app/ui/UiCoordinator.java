package com.example.app.ui;

import com.example.app.AppContext;
import com.example.common.constants.Constantes;
import com.example.features.equipos.ortopedias.controller.CDEViewController;
import com.example.features.equipos.ortopedias.controller.CorreccionsController;
import com.example.features.equipos.ortopedias.controller.EquiposParaEntregarController;
import com.example.features.equipos.ortopedias.controller.OrthopediaInputController;
import com.example.features.equipos.ortopedias.controller.RegistrarEstadoController;
import com.example.features.equipos.controller.VerEquiposController;
import com.example.features.equipos.otros.controller.OtrosInputController;
import com.example.features.ajustes.controller.AjustesController;
import com.example.features.lotes.controller.LotesController;
import com.example.features.lotes.controller.VerLotesController;
import com.example.ui.events.OnEquipoGuardadoListener;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.ui.shell.PantallaPrincipal;

/**
 * Coordina la inicialización de todos los controladores y la conexión entre pantallas.
 *
 * Responsabilidades:
 * - Instanciar cada controller con su vista y los servicios que necesita.
 * - Delegar inyecciones que un controller no puede hacerse a sí mismo
 *   (p. ej. inicializar PantallaAuditoria desde CorreccionsController).
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

        // ── Listener diferido: rompe el ciclo controller → runnable → controller ─
        Runnable[] refrescarRef = { null };
        OnEstadosActualizadosListener refrescarEstados = () -> { if (refrescarRef[0] != null) refrescarRef[0].run(); };
        OnEquipoGuardadoListener      refrescarEquipos = () -> { if (refrescarRef[0] != null) refrescarRef[0].run(); };

        // ── Controllers ──────────────────────────────────────────────────────
        Runnable solicitarRefresco = () -> { if (refrescarRef[0] != null) refrescarRef[0].run(); };

        CDEViewController cdeViewController = new CDEViewController(
            vista.getPantallaVerCDEv2(), solicitarRefresco);

        RegistrarEstadoController registrarEstadoController = new RegistrarEstadoController(
            vista.getPantallaRegistrarEstado(),
            context.getEquipoOtrosService(),
            context.getMaterialService(),
            context.getEstadoValidator(),
            refrescarEstados,
            solicitarRefresco);

        EquiposParaEntregarController equiposParaEntregarController =
            new EquiposParaEntregarController(
                vista.getPantallaEquiposParaEntregar(),
                context.getEquipoOtrosService(),
                context.getMaterialService(),
                context.getEstadoValidator(),
                refrescarEstados,
                solicitarRefresco);

        CorreccionsController correccionesController = new CorreccionsController(
            vista.getPantallaCorrecciones(),
            context.getEquipoCorreccionService(),
            context.getEquipoOtrosCorreccionService(),
            context.getCatalogoOtrosService());

        LotesController lotesController = new LotesController(
            vista.getPantallaLotes(),
            context.getCatalogoService(),
            context.getAutoclaveService(),
            context.getLoteService(),
            context.getEquipoService(),
            context.getEquipoOtrosService(),
            refrescarEstados);

        VerLotesController verLotesController = new VerLotesController(
            vista.getPantallaVerLotes(),
            context.getLoteReporteService(),
            solicitarRefresco);

        new VerEquiposController(
            vista.getPantallaVerEquipos(),
            context.getEquipoService(),
            context.getEquipoOtrosService(),
            context.getClienteService(),
            context.getInstitucionService(),
            context.getEquipoReporteService(),
            context.getEquipoOtrosReporteService());

        // ── Inyección en PantallaAuditoria ───────────────────────────────────
        correccionesController.inicializarPantallaAuditoria(vista.getPantallaAuditoria());

        // ── Navegación: botón "Ver Auditoría" en PantallaCorrecciones ────────
        correccionesController.setOnVerAuditoria(() ->
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.AUDITORIA));

        // ── Refresco global de pantallas ─────────────────────────────────────
        refrescarRef[0] = crearRefrescador(
            cdeViewController,
            registrarEstadoController,
            equiposParaEntregarController,
            lotesController,
            verLotesController
        );

        correccionesController.setOnCambiosAplicados(() -> refrescarRef[0].run());

        new OrthopediaInputController(
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

        AjustesController ajustesController = new AjustesController(
            vista.getPantallaAjustes(), context.getClienteService());
        ajustesController.setOnMutacion(solicitarRefresco);

        // Primer pintado: los controllers ya no leen en su constructor, la UI se
        // puebla con el snapshot compartido.
        refrescarRef[0].run();
    }

    private Runnable crearRefrescador(
        CDEViewController               cde,
        RegistrarEstadoController       registrar,
        EquiposParaEntregarController   entregar,
        LotesController                 lotes,
        VerLotesController              verLotes
    ) {
        LectorDatosRefresco lector = new LectorDatosRefresco(
            context.getEquipoService(),
            context.getEquipoOtrosService(),
            context.getAutoclaveService(),
            context.getCatalogoService(),
            context.getLoteService());

        return () -> {
            DatosRefresco datos = lector.leer();
            cde.pintar(datos);
            verLotes.pintar(datos);
            registrar.pintar(datos);
            entregar.pintar(datos);
            lotes.cargarDatos();
        };
    }
}
