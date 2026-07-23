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
import java.util.function.Consumer;
import javax.swing.JOptionPane;

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

        // ── Referencias diferidas: rompen el ciclo controller → refrescador → controller.
        //    Cada refrescador necesita a sus controllers para repartirles el snapshot,
        //    y ellos necesitan poder pedirle una lectura. Se cablean después de crear
        //    ambos; hasta entonces solicitar() es un no-op.
        //
        //    Son tres grupos con disparadores distintos, no un refresco global:
        //      · operativo         → cada guardado; la cola activa, sin histórico.
        //      · historial equipos → al abrir "Ver Equipos" o "Estado de procesos".
        //      · historial lotes   → al abrir "Ver Lotes".
        //    Las pantallas de consulta se releen cuando el usuario las mira; antes
        //    se releían en cada guardado incluso estando ocultas.
        Disparador operativo         = new Disparador();
        Disparador historialEquipos  = new Disparador();
        Disparador historialLotes    = new Disparador();

        OnEstadosActualizadosListener refrescarEstados = operativo::solicitar;
        OnEquipoGuardadoListener      refrescarEquipos = operativo::solicitar;

        // ── Controllers ──────────────────────────────────────────────────────

        CDEViewController cdeViewController = new CDEViewController(
            vista.getPantallaVerCDEv2(), historialEquipos);

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

        CorreccionsController correccionesController = new CorreccionsController(
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

        VerEquiposController verEquiposController = new VerEquiposController(
            vista.getPantallaVerEquipos(),
            context.getEquipoOtrosService(),
            context.getClienteService(),
            context.getInstitucionService(),
            context.getEquipoReporteService(),
            context.getEquipoOtrosReporteService(),
            historialEquipos);

        // ── Inyección en PantallaAuditoria ───────────────────────────────────
        correccionesController.inicializarPantallaAuditoria(vista.getPantallaAuditoria());

        // ── Navegación: botón "Ver Auditoría" en PantallaCorrecciones ────────
        correccionesController.setOnVerAuditoria(() ->
            vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.AUDITORIA));

        // ── Refresco por grupo ───────────────────────────────────────────────
        operativo.cablear(crearRefrescadorOperativo(
            registrarEstadoController, equiposParaEntregarController, lotesController));

        historialEquipos.cablear(crearRefrescadorHistorialEquipos(
            cdeViewController, verEquiposController));

        historialLotes.cablear(crearRefrescadorHistorialLotes(verLotesController));

        correccionesController.setOnCambiosAplicados(operativo);

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
            vista.getPantallaAjustes(), context.getClienteService(), context.getActualizacionService());
        ajustesController.setOnMutacion(operativo);

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

    /** Las dos pantallas que consultan el histórico de equipos. */
    private RefrescadorPantallas<HistorialEquipos> crearRefrescadorHistorialEquipos(
        CDEViewController cde,
        VerEquiposController verEquipos
    ) {
        LectorHistorialEquipos lector = new LectorHistorialEquipos(
            context.getEquipoService(),
            context.getEquipoOtrosService());

        Consumer<HistorialEquipos> repartir = datos -> {
            cde.pintar(datos);
            verEquipos.pintar(datos);
        };

        return new RefrescadorPantallas<>(
            "refresco-historial-equipos", lector, repartir, this::mostrarErrorDeRefresco);
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
