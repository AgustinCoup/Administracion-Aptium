package com.example.features.lavadero.controller;

import com.example.common.paginacion.Pagina;
import com.example.features.lavadero.controller.helpers.ConsultaHistorial;
import com.example.features.lavadero.model.FiltroHistorial;
import com.example.features.lavadero.model.IngresoHistorial;
import com.example.features.lavadero.model.LineaHistorial;
import com.example.features.lavadero.service.HistorialLavaderoService;
import com.example.features.lavadero.view.PantallaHistorialLavadero;
import com.example.features.lavadero.view.helpers.DetalleHistorialDialog;
import com.example.ui.common.TareaUI;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

/**
 * Cablea la pantalla de Historial de Lavadero: paginación con los filtros resueltos en SQL y
 * lectura del detalle de un ingreso bajo demanda.
 *
 * <p><b>Sólo lectura.</b> Historial no muta nada: mutar es trabajo de Clasificación, Ciclos y
 * Salidas.</p>
 *
 * <h2>Ya no hay snapshot completo</h2>
 * Este controller <b>no</b> extiende {@code AbstractFilterController} y no tiene {@code cache}: la
 * lista completa no se lee nunca más. Los filtros viajan a la base y vuelve una página de 50. Dejar
 * la herencia con un cache vacío sería peor que sacarla — invita a que alguien lo vuelva a llenar
 * y a filtrar en memoria una página, que da resultados incorrectos (filtra 50 de 5000 en vez de
 * las 50 primeras de las que matchean).
 *
 * <h2>Qué pasa con la página y el filtro en cada disparador</h2>
 * <ul>
 *   <li><b>Cambiar un filtro vuelve a la página 1.</b> Quedarse en la página 7 después de filtrar
 *       muestra una página vacía sobre un resultado que sí tiene filas, y el operador concluye que
 *       no hay nada. El reset cuelga del callback de cambio de filtro, <b>no</b> del pintado: un
 *       reset en {@code pintar} mandaría a la página 1 en cada F5.</li>
 *   <li><b>Cambiar de página conserva el filtro</b> y arrastra el total ya leído: no se recuenta.</li>
 *   <li><b>El refresco (F5 / botón) conserva la página y el filtro</b>, pero vuelve a contar: el
 *       botón existe para ver datos frescos de lo que estoy mirando, no para mandarme al
 *       principio. Esta pantalla no acumula estado, así que no necesita guarda de refresco.</li>
 *   <li><b>Entrar a la pantalla</b> restablece los filtros al default sin notificar y va a la
 *       página 1, que es la misma vista de siempre.</li>
 * </ul>
 *
 * <h2>El filtro y la página se publican, no se leen desde el hilo de fondo</h2>
 * {@code RefrescadorPantallas} lee con un {@code Supplier} sin parámetros que corre fuera del EDT.
 * Todo cambio de filtro o de página publica una {@link ConsultaHistorial} nueva —inmutable, en el
 * EDT— en {@link #consulta}, y el lector lee esa referencia. Nada de este controller se toca desde
 * el hilo de fondo salvo ese campo {@code volatile}.
 *
 * <p><b>Invariante del que depende ese esquema:</b> toda publicación va seguida de
 * {@code solicitarRefresco.run()}, que cancela la lectura en vuelo. Por eso el total que llega en
 * {@code pintar} corresponde siempre al filtro publicado.</p>
 *
 * <p><b>El detalle va por {@link TareaUI}</b>, y el diálogo se construye dentro de {@code pintar}.
 * Traerlo para todos los ingresos en cada refresco costaría leer el histórico entero de cuatro
 * tablas para mostrar una fila, así que se lee el de un ingreso cuando se lo pide con doble clic
 * — pero fuera del hilo de la interfaz, como todo acceso a la base (ver {@code EdtGuard}).</p>
 */
public class HistorialLavaderoController {

    private final PantallaHistorialLavadero pantalla;
    private final HistorialLavaderoService  service;
    private final Runnable                  solicitarRefresco;

    /**
     * Lo que el lector de fondo tiene que leer. <b>Se escribe sólo en el EDT</b> y se lee desde el
     * hilo de fondo; por eso es {@code volatile} y por eso lo que guarda es inmutable.
     */
    private volatile ConsultaHistorial consulta =
        ConsultaHistorial.primeraPagina(FiltroHistorial.sinFiltros());

    /** Alcance: pintar la grilla desde el refresco y leer el detalle de un ingreso bajo demanda. */
    public HistorialLavaderoController(PantallaHistorialLavadero pantalla,
                                       HistorialLavaderoService service,
                                       Runnable solicitarRefresco) {
        this.pantalla          = Objects.requireNonNull(pantalla, "pantalla no puede ser null");
        this.service           = Objects.requireNonNull(service,  "service no puede ser null");
        this.solicitarRefresco = Objects.requireNonNull(solicitarRefresco,
            "solicitarRefresco no puede ser null");

        pantalla.setOnFiltrosChanged(this::alCambiarFiltros);
        pantalla.setAlCambiarPagina(this::alCambiarPagina);

        // El botón "Actualizar" (y F5) releen lo mismo que se está mirando, con el total al día.
        pantalla.setAccionRefrescar(this::refrescar);

        pantalla.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                // Entrar a Historial siempre da la misma vista. El restablecimiento no notifica:
                // pintar() es el único que repinta, para no mostrar un flash con datos viejos
                // mientras llega la página nueva.
                pantalla.restablecerFiltrosPorDefecto();
                alCambiarFiltros();
            }
        });

        pantalla.getTablaIngresos().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) abrirDetalle();
            }
        });
    }

    /**
     * Qué tiene que leer el hilo de fondo. Es el único miembro de esta clase que se puede tocar
     * fuera del EDT.
     */
    public ConsultaHistorial consultaActual() {
        return consulta;
    }

    /** Vuelca la página a la grilla y a la barra de paginación. Sin I/O. */
    public void pintar(Pagina<IngresoHistorial> pagina) {
        // El total recién leído se arrastra: el próximo cambio de página no vuelve a contar.
        consulta = consulta.conTotal(pagina.totalFilas());
        pantalla.actualizarIngresos(pagina.contenido());
        pantalla.mostrarPaginacion(pagina);
        pantalla.marcarActualizado();
    }

    // ── disparadores ──────────────────────────────────────────────────────────

    /** Filtro nuevo ⇒ página 1 y total nuevo. */
    private void alCambiarFiltros() {
        publicarYPedir(ConsultaHistorial.primeraPagina(filtroDeLaPantalla()));
    }

    /** Otra página del mismo filtro: conserva filtro y total. */
    private void alCambiarPagina(int numeroPagina) {
        publicarYPedir(consulta.enPagina(numeroPagina));
    }

    /** Refresco pedido por el operador: la misma página y el mismo filtro, contando de nuevo. */
    private void refrescar() {
        publicarYPedir(consulta.recontando());
    }

    /**
     * Publica qué hay que leer y pide la lectura, en ese orden y siempre juntos: el
     * {@code solicitar()} cancela lo que haya en vuelo, y así lo que llegue a {@code pintar}
     * corresponde a lo último publicado.
     */
    private void publicarYPedir(ConsultaHistorial nueva) {
        consulta = nueva;
        solicitarRefresco.run();
    }

    private FiltroHistorial filtroDeLaPantalla() {
        return new FiltroHistorial(
            pantalla.getFiltroCliente(),
            pantalla.getFiltroEstados(),
            pantalla.getFiltroDesde(),
            pantalla.getFiltroHasta(),
            pantalla.getFiltroElemento(),
            pantalla.getFiltroLavarropas());
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    /**
     * Doble clic → trazabilidad del ingreso. La lectura va en fondo y el diálogo se arma recién
     * con las líneas ya leídas: acá no se llama al service desde el hilo de la interfaz.
     */
    private void abrirDetalle() {
        int viewRow = pantalla.getTablaIngresos().getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = pantalla.getTablaIngresos().convertRowIndexToModel(viewRow);
        IngresoHistorial ingreso = pantalla.getIngresoAt(modelRow);
        if (ingreso == null) return;

        TareaUI.<List<LineaHistorial>>nueva()
            .nombre("detalle-historial-lavadero")
            .leer(() -> service.obtenerDetalle(ingreso.id()))
            .pintar(lineas -> new DetalleHistorialDialog(
                SwingUtilities.getWindowAncestor(pantalla), ingreso, lineas).setVisible(true))
            .siFalla(this::mostrarErrorDetalle)
            .lanzar();
    }

    /** Un detalle que no se pudo leer se avisa; abrir un diálogo vacío mentiría. */
    private void mostrarErrorDetalle(Throwable causa) {
        JOptionPane.showMessageDialog(pantalla,
            "No se pudo leer el detalle del ingreso.\n\n" + causa.getMessage(),
            "Error al leer el historial", JOptionPane.ERROR_MESSAGE);
    }
}
