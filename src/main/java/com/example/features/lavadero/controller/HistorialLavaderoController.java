package com.example.features.lavadero.controller;

import com.example.common.util.AbstractFilterController;
import com.example.common.util.FilterStrategy;
import com.example.features.lavadero.controller.helpers.HistorialFilterCriteria;
import com.example.features.lavadero.controller.helpers.HistorialFilterStrategy;
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
 * Cablea la pantalla de Historial de Lavadero: filtrado en memoria sobre el snapshot y lectura
 * del detalle de un ingreso bajo demanda.
 *
 * <p><b>Sólo lectura.</b> Historial no muta nada: mutar es trabajo de Clasificación, Ciclos y
 * Salidas.</p>
 *
 * <p><b>El detalle va por {@link TareaUI}</b>, y el diálogo se construye dentro de {@code pintar}.
 * Traerlo para todos los ingresos en cada refresco costaría leer el histórico entero de cuatro
 * tablas para mostrar una fila, así que se lee el de un ingreso cuando se lo pide con doble clic
 * — pero fuera del hilo de la interfaz, como todo acceso a la base (ver {@code EdtGuard}).</p>
 */
public class HistorialLavaderoController extends AbstractFilterController<IngresoHistorial> {

    private final PantallaHistorialLavadero pantalla;
    private final HistorialLavaderoService  service;

    private final FilterStrategy<IngresoHistorial, HistorialFilterCriteria> filterStrategy =
        new HistorialFilterStrategy();

    /** Alcance: pintar la grilla desde el refresco y leer el detalle de un ingreso bajo demanda. */
    public HistorialLavaderoController(PantallaHistorialLavadero pantalla,
                                       HistorialLavaderoService service,
                                       Runnable solicitarRefresco) {
        this.pantalla = Objects.requireNonNull(pantalla, "pantalla no puede ser null");
        this.service  = Objects.requireNonNull(service,  "service no puede ser null");
        Objects.requireNonNull(solicitarRefresco, "solicitarRefresco no puede ser null");

        pantalla.setOnFiltrosChanged(this::aplicarFiltros);

        pantalla.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                // Entrar a Historial siempre da la misma vista. El restablecimiento no notifica:
                // pintar() es el único que filtra y repinta, para no mostrar un flash con datos
                // viejos mientras llega el snapshot nuevo.
                pantalla.restablecerFiltrosPorDefecto();
                solicitarRefresco.run();
            }
        });

        pantalla.getTablaIngresos().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) abrirDetalle();
            }
        });
    }

    /** Vuelca el snapshot a la grilla. Sin I/O. */
    public void pintar(List<IngresoHistorial> historial) {
        recargarCache(historial);
    }

    @Override
    protected void aplicarFiltros() {
        HistorialFilterCriteria criteria = new HistorialFilterCriteria(
            pantalla.getFiltroCliente(),
            pantalla.getFiltroEstados(),
            pantalla.getFiltroDesde(),
            pantalla.getFiltroHasta(),
            pantalla.getFiltroElemento(),
            pantalla.getFiltroLavarropas()
        );
        pantalla.actualizarIngresos(filterStrategy.filter(getCache(), criteria));
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
