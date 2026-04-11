package com.example.app.ui;

import com.example.app.AppModel;
import com.example.common.constants.Constantes;
import com.example.features.equipos.ortopedias.controller.CDEViewController;
import com.example.features.equipos.ortopedias.controller.CorreccionsController;
import com.example.features.equipos.ortopedias.controller.EquiposParaEntregarController;
import com.example.features.equipos.ortopedias.controller.OrthopediaInputController;
import com.example.features.equipos.ortopedias.controller.RegistrarEstadoController;
import com.example.features.equipos.otros.controller.OtrosInputController;
import com.example.features.lotes.controller.LotesController;
import com.example.features.lotes.controller.VerLotesController;
import com.example.features.lotes.service.LoteReporteService;
import com.example.ui.events.OnEquipoGuardadoListener;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.ui.shell.PantallaPrincipal;

/**
 * Coordina la inicialización de todos los controladores y la conexión entre pantallas.
 *
 * Responsabilidades:
 * - Instanciar cada controller con su vista y modelo correspondientes.
 * - Delegar inyecciones que un controller no puede hacerse a sí mismo
 *   (p. ej. inicializar PantallaAuditoria desde CorreccionsController).
 * - Cablear los listeners de navegación entre pantallas.
 * - Construir el Runnable de refresco global que cada controller dispara al modificar datos.
 */
public class UiCoordinator {

    private final AppModel        model;
    private final PantallaPrincipal vista;

    public UiCoordinator(AppModel model, PantallaPrincipal vista) {
        if (model == null || vista == null) {
            throw new IllegalArgumentException("Model y vista no pueden ser nulos");
        }
        this.model = model;
        this.vista = vista;
    }

    public void inicializar() {

        // ── Controllers ──────────────────────────────────────────────────────
        CDEViewController cdeViewController = new CDEViewController(
            vista.getPantallaVerCDEv2(), model);

        RegistrarEstadoController registrarEstadoController = new RegistrarEstadoController(
            vista.getPantallaRegistrarEstado(), model, null);

        EquiposParaEntregarController equiposParaEntregarController =
            new EquiposParaEntregarController(
                vista.getPantallaEquiposParaEntregar(), model, null);

        CorreccionsController correccionesController = new CorreccionsController(
            vista.getPantallaCorrecciones(), model);

        LotesController lotesController = new LotesController(
            vista.getPantallaLotes(), model, null);

        LoteReporteService loteReporteService = new LoteReporteService(model);
        VerLotesController verLotesController = new VerLotesController(
            vista.getPantallaVerLotes(), model, loteReporteService);

        // ── Inyección en PantallaAuditoria ───────────────────────────────────
        // CorreccionsController conoce el servicio; el coordinator no necesita conocerlo.
        correccionesController.inicializarPantallaAuditoria(vista.getPantallaAuditoria());

        // ── Navegación: botón "Ver Auditoría" en PantallaCorrecciones ────────
        correccionesController.setOnVerAuditoria(() ->
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.AUDITORIA));

        // ── Refresco global de pantallas ─────────────────────────────────────
        Runnable refrescarPantallas = crearRefrescador(
            cdeViewController,
            registrarEstadoController,
            equiposParaEntregarController,
            lotesController,
            verLotesController
        );

        OnEstadosActualizadosListener refrescarEstados = refrescarPantallas::run;
        OnEquipoGuardadoListener      refrescarEquipos = refrescarPantallas::run;

        registrarEstadoController.setOnEstadosActualizados(refrescarEstados);
        equiposParaEntregarController.setOnEstadosActualizados(refrescarEstados);
        lotesController.setOnEstadosActualizados(refrescarEstados);
        correccionesController.setOnCambiosAplicados(refrescarPantallas);

        new OrthopediaInputController(
            vista.getPanelIngresoOrtopedia(),
            model,
            vista.getNavegador(),
            vista.getContenedor(),
            refrescarEquipos
        );

        new OtrosInputController(
            vista.getPanelIngresoOtros(),
            model,
            vista.getNavegador(),
            vista.getContenedor(),
            refrescarEquipos
        );
    }

    private Runnable crearRefrescador(
        CDEViewController               cde,
        RegistrarEstadoController       registrar,
        EquiposParaEntregarController   entregar,
        LotesController                 lotes,
        VerLotesController              verLotes
    ) {
        return () -> {
            cde.cargarDatos();
            registrar.cargarEquipos();
            entregar.cargarDatos();
            lotes.cargarDatos();
            verLotes.cargarDatos();
        };
    }
}