package com.example.features.equipos.controller;

import com.example.features.clientes.service.ClienteService;
import com.example.features.equipos.controller.helpers.ConsultaEquipos;
import com.example.features.equipos.controller.helpers.PaginasEquipos;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.service.EquipoReporteService;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.service.EquipoOtrosReporteService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.instituciones.service.InstitucionService;
import com.example.features.equipos.view.PantallaVerEquipos;
import com.example.features.equipos.view.helpers.DetalleOrtopediaDialog;
import com.example.features.equipos.view.helpers.DetalleOtrosDialog;
import com.example.features.equipos.view.helpers.ImprimirEquiposDialog;
import com.example.ui.common.TareaUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;


/**
 * Cablea la pantalla <b>Ver Equipos</b>: dos grillas —ortopedias y "otros"— que paginan por
 * separado sobre el mismo panel de filtros, con los filtros y el orden resueltos en SQL.
 *
 * <h2>Ya no hay snapshot completo, y por eso tampoco hay flag "cargado"</h2>
 * Esta pantalla recibía el histórico entero de las dos tablas y filtraba en memoria. El viejo
 * {@code cargado} existía porque {@code aplicarFiltros()} podía correr —por un cambio de filtro—
 * antes de que llegara el primer snapshot, y sin él habría filtrado dos listas vacías. Con
 * paginación cada cambio de filtro <b>es</b> una lectura: no queda ningún estado "todavía no
 * cargué" que proteger.
 *
 * <h2>Dos paginadores, un filtro</h2>
 * Las dos grillas son tablas distintas, con modelos distintos y volúmenes distintos, así que cada
 * una tiene su página. El filtro es uno solo, porque el panel de filtros es uno solo: los campos
 * que no aplican a "otros" —profesional, paciente, institución— los ignora su DAO, que es
 * exactamente lo que hacía el filtrado en memoria. Ver {@link FiltroEquipos}.
 *
 * <h2>El filtro y las páginas se publican, no se leen desde el hilo de fondo</h2>
 * {@code RefrescadorPantallas} lee con un {@code Supplier} sin parámetros que corre fuera del EDT.
 * Todo cambio publica una {@link ConsultaEquipos} nueva —inmutable, en el EDT— en {@link #consulta},
 * y el lector lee esa referencia. Nada de este controller se toca desde el hilo de fondo salvo ese
 * campo {@code volatile}.
 *
 * <p><b>Invariante del que depende ese esquema:</b> toda publicación va seguida de
 * {@code solicitarRefresco.run()}, que cancela la lectura en vuelo. Por eso los totales que llegan
 * a {@code pintar} corresponden siempre al filtro publicado.</p>
 */
public class VerEquiposController {

    private static final Logger log = LoggerFactory.getLogger(VerEquiposController.class);

    private final PantallaVerEquipos       panel;
    private final EquipoOtrosService       equipoOtrosService;
    private final ClienteService           clienteService;
    private final InstitucionService       institucionService;
    private final EquipoReporteService     equipoReporteService;
    private final EquipoOtrosReporteService equipoOtrosReporteService;
    private final Runnable                 solicitarRefresco;

    /**
     * Lo que el lector de fondo tiene que leer. <b>Se escribe sólo en el EDT</b> y se lee desde el
     * hilo de fondo; por eso es {@code volatile} y por eso lo que guarda es inmutable.
     */
    private volatile ConsultaEquipos consulta =
        ConsultaEquipos.primeraPagina(FiltroEquipos.sinFiltros());

    /**
     * Alcance: lectura paginada de equipos para las dos grillas y el detalle, autocompletado de
     * cliente/institución en los diálogos de impresión, y los dos reportes.
     *
     * <p>La lectura de las páginas no pasa por acá: la arma {@code UiCoordinator} sobre
     * {@link #consultaActual()}, que es lo que hace que el lector corra en el hilo de fondo sin
     * tocar estado del EDT.
     */
    public VerEquiposController(PantallaVerEquipos panel,
                                EquipoOtrosService equipoOtrosService,
                                ClienteService clienteService,
                                InstitucionService institucionService,
                                EquipoReporteService equipoReporteService,
                                EquipoOtrosReporteService equipoOtrosReporteService,
                                Runnable solicitarRefresco) {
        this.panel                   = Objects.requireNonNull(panel, "panel");
        this.equipoOtrosService      = Objects.requireNonNull(equipoOtrosService, "equipoOtrosService");
        this.clienteService          = Objects.requireNonNull(clienteService, "clienteService");
        this.institucionService      = Objects.requireNonNull(institucionService, "institucionService");
        this.equipoReporteService    = Objects.requireNonNull(equipoReporteService, "equipoReporteService");
        this.equipoOtrosReporteService =
            Objects.requireNonNull(equipoOtrosReporteService, "equipoOtrosReporteService");
        this.solicitarRefresco       = Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        panel.setOnImprimirOrtopedias(this::abrirDialogoOrtopedias);
        panel.setOnImprimirOtros(this::abrirDialogoOtros);
        panel.configurarFiltros(this::alCambiarFiltros);
        panel.setAlCambiarPaginaOrtopedias(this::alCambiarPaginaOrtopedias);
        panel.setAlCambiarPaginaOtros(this::alCambiarPaginaOtros);

        // El botón "Actualizar" (y F5) releen lo mismo que se está mirando —mismo filtro, mismas
        // páginas— con los totales al día. Esta pantalla no acumula estado, así que no lleva guarda.
        panel.setAccionRefrescar(this::refrescar);

        panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                // Sin notificar: la relectura la pide alCambiarFiltros(), y pintar() es el único
                // que repinta, para no mostrar un flash con la página de la visita anterior.
                panel.aplicarFiltroInicial();
                alCambiarFiltros();
            }
        });

        panel.getTablaOrtopedias().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) abrirDetalleOrtopedia();
            }
        });

        panel.getTablaOtros().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) abrirDetalleOtros();
            }
        });
    }

    /**
     * Qué tiene que leer el hilo de fondo. Es el único miembro de esta clase que se puede tocar
     * fuera del EDT.
     */
    public ConsultaEquipos consultaActual() {
        return consulta;
    }

    // ── Carga de datos ────────────────────────────────────────────────────────

    /** Vuelca las dos páginas a sus grillas y a sus barras de paginación. Sin I/O. */
    public void pintar(PaginasEquipos paginas) {
        // Los totales recién leídos se arrastran: el próximo cambio de página no vuelve a contar.
        consulta = consulta.conTotales(
            paginas.ortopedias().totalFilas(), paginas.otros().totalFilas());
        panel.setDatosOrtopedia(paginas.ortopedias());
        panel.setDatosOtros(paginas.otros());
        panel.marcarActualizado();
        log.info("Ver equipos: página {} de ortopedias ({} en total), página {} de otros ({} en total)",
            paginas.ortopedias().numeroPagina(), paginas.ortopedias().totalFilas(),
            paginas.otros().numeroPagina(), paginas.otros().totalFilas());
    }

    // ── Disparadores ──────────────────────────────────────────────────────────

    /** Filtro nuevo ⇒ las dos grillas vuelven a la página 1 y los dos totales se recuentan. */
    private void alCambiarFiltros() {
        publicarYPedir(ConsultaEquipos.primeraPagina(filtroDeLaPantalla()));
    }

    /** Otra página de ortopedias: no mueve la de "otros" ni recuenta nada. */
    private void alCambiarPaginaOrtopedias(int numeroPagina) {
        publicarYPedir(consulta.ortopediasEnPagina(numeroPagina));
    }

    /** Otra página de "otros": no mueve la de ortopedias ni recuenta nada. */
    private void alCambiarPaginaOtros(int numeroPagina) {
        publicarYPedir(consulta.otrosEnPagina(numeroPagina));
    }

    /** Refresco pedido por el operador: las mismas páginas y el mismo filtro, contando de nuevo. */
    private void refrescar() {
        publicarYPedir(consulta.recontando());
    }

    /**
     * Publica qué hay que leer y pide la lectura, en ese orden y siempre juntos: el
     * {@code solicitar()} cancela lo que haya en vuelo, y así lo que llegue a {@code pintar}
     * corresponde a lo último publicado.
     */
    private void publicarYPedir(ConsultaEquipos nueva) {
        consulta = nueva;
        solicitarRefresco.run();
    }

    /**
     * Los siete filtros de la pantalla, tal como los va a resolver la base.
     *
     * <p>Los textos viajan sin normalizar: {@code FiltroEquiposSql} los trata como substring
     * insensible a mayúsculas y considera vacío lo que esté en blanco, que es lo mismo que hacía el
     * {@code contains} en memoria.
     */
    private FiltroEquipos filtroDeLaPantalla() {
        return new FiltroEquipos(
            panel.getCmbEstados().getSelectedItems(),
            panel.getTxtCliente().getText().trim(),
            panel.getTxtProfesional().getText().trim(),
            panel.getTxtPaciente().getText().trim(),
            panel.getTxtInstitucion().getText().trim(),
            panel.getCmbTipoIngreso().getSelectedItems(),
            toLocalDate(panel.getDateDesde().getDate()),
            toLocalDate(panel.getDateHasta().getDate()));
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    private void abrirDetalleOrtopedia() {
        int viewRow = panel.getTablaOrtopedias().getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = panel.getTablaOrtopedias().convertRowIndexToModel(viewRow);
        Equipo equipo = panel.getEquipoOrtopediaAt(modelRow);
        if (equipo == null) return;
        Window ventana = SwingUtilities.getWindowAncestor(panel);
        new DetalleOrtopediaDialog(ventana, equipo).setVisible(true);
    }

    /**
     * Doble clic → detalle con materiales. La lectura va en fondo: {@code obtenerPorId} hace
     * JDBC y, desde el Paso 6, propaga {@link com.example.common.exception.DatabaseException} en
     * vez de devolver {@code null} ante un fallo — llamarlo directo desde el EDT dejaría ese
     * error sin capturar.
     */
    private void abrirDetalleOtros() {
        int viewRow = panel.getTablaOtros().getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = panel.getTablaOtros().convertRowIndexToModel(viewRow);
        EquipoOtros equipo = panel.getEquipoOtrosAt(modelRow);
        if (equipo == null) return;

        TareaUI.<EquipoOtros>nueva()
            .nombre("detalle-equipo-otros")
            .leer(() -> equipoOtrosService.obtenerPorId(equipo.getId()))
            .pintar(equipoConMateriales -> {
                if (equipoConMateriales == null) return;   // borrado entre el doble clic y la lectura
                new DetalleOtrosDialog(
                    SwingUtilities.getWindowAncestor(panel), equipoConMateriales).setVisible(true);
            })
            .siFalla(this::mostrarErrorDetalle)
            .lanzar();
    }

    /** Un detalle que no se pudo leer se avisa; abrir un diálogo vacío mentiría. */
    private void mostrarErrorDetalle(Throwable causa) {
        JOptionPane.showMessageDialog(panel,
            "No se pudo leer el detalle del equipo.\n\n" + causa.getMessage(),
            "Error al leer el equipo", JOptionPane.ERROR_MESSAGE);
    }

    // ── Impresión ─────────────────────────────────────────────────────────────

    private void abrirDialogoOrtopedias() {
        Frame ventana = (Frame) SwingUtilities.getWindowAncestor(panel);
        new ImprimirEquiposDialog(ventana, "Imprimir Reporte de Ortopedias",
            clienteService::buscarClientes,
            institucionService::buscarInstituciones,
            (desde, hasta, clienteId, institucionId) ->
                TareaUI.<Void>nueva()
                    .nombre("reporte-ortopedias")
                    .leer(() -> {
                        equipoReporteService.generarYMostrarReporte(desde, hasta, clienteId, institucionId);
                        return null;
                    })
                    .siFalla(this::mostrarErrorReporte)
                    .lanzar()
        ).setVisible(true);
    }

    private void abrirDialogoOtros() {
        Frame ventana = (Frame) SwingUtilities.getWindowAncestor(panel);
        new ImprimirEquiposDialog(ventana, "Imprimir Reporte de Otros",
            clienteService::buscarClientes,
            (desde, hasta, clienteId, institucionId) ->
                TareaUI.<Void>nueva()
                    .nombre("reporte-otros")
                    .leer(() -> {
                        equipoOtrosReporteService.generarYMostrarReporte(desde, hasta, clienteId);
                        return null;
                    })
                    .siFalla(this::mostrarErrorReporte)
                    .lanzar()
        ).setVisible(true);
    }

    private void mostrarErrorReporte(Throwable e) {
        JOptionPane.showMessageDialog(panel,
            "Error al generar el reporte:\n" + e.getMessage(),
            "Error", JOptionPane.ERROR_MESSAGE);
    }
}
