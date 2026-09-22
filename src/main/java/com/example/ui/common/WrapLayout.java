package com.example.ui.common;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * {@link FlowLayout} que <b>declara la altura de las filas que realmente va a dibujar</b>.
 *
 * <p>El {@code FlowLayout} de la JDK ya envuelve los componentes que no entran a lo ancho, pero
 * su {@code preferredLayoutSize} informa <b>siempre una sola fila</b>. Si el panel está en un
 * {@code BorderLayout.SOUTH} —que le da exactamente su altura preferida— las filas envueltas
 * caen fuera del área visible: el botón existe, responde a {@code isShowing()} y a los tests,
 * pero el operador no lo ve ni lo puede apretar. Es la peor forma de fallar de un botón, porque
 * no se parece a un error: se parece a una función que no existe.
 *
 * <p>Medido en Salidas de Lavadero a 1280×720 —el tamaño mínimo <i>y</i> por defecto de
 * {@code PantallaPrincipal}—: la fila de tres botones pide 624 px y recibe 526, así que
 * "Ingresar al CDE" se dibujaba en {@code y=48} dentro de una fila de 48 px de alto.
 *
 * <p><b>No usarlo dentro de un {@code JScrollPane}</b> con scroll horizontal: ahí el ancho del
 * viewport no acota al contenido y el cálculo se realimenta. Es contrato de javadoc y nada más —
 * el panel no conoce a su padre cuando se construye, así que no hay dónde verificarlo.
 */
public class WrapLayout extends FlowLayout {

    private WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /**
     * Único constructor público, y es una fábrica de panel y no del layout a propósito: el layout
     * solo no alcanza.
     *
     * <p>{@code Container.getPreferredSize()} <b>cachea</b> mientras el componente esté válido, y
     * en la primera pasada de layout este panel todavía mide 0 de ancho — o sea que la altura que
     * queda cacheada es la de una sola fila, justo la que hay que corregir. Recién cuando el panel
     * recibe su ancho real se puede saber cuántas filas son, y para entonces nadie vuelve a
     * preguntar. Por eso el {@code componentResized} tira el valor cacheado y pide otra pasada:
     * converge en dos, porque la segunda ya no cambia los bounds y no vuelve a disparar.
     *
     * <p>Construir {@code new JPanel(new WrapLayout(...))} a mano compila y se ve bien hasta que
     * la ventana es angosta. No hay constructor público del layout para que esa variante no exista.
     */
    public static JPanel panel(int alineacion, int hgap, int vgap) {
        JPanel panel = new JPanel(new WrapLayout(alineacion, hgap, vgap));
        panel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                panel.invalidate();
                panel.revalidate();
            }
        });
        return panel;
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return tamanioDeFilas(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        return tamanioDeFilas(target, false);
    }

    /**
     * Recorre los componentes armando filas contra el ancho que el contenedor <i>ya tiene</i>, y
     * suma las alturas. Antes del primer layout ese ancho es 0; ahí se responde "una sola fila"
     * (el comportamiento de {@code FlowLayout}) y la pasada siguiente, ya con ancho real,
     * corrige — por eso la vista tiene que revalidarse al dimensionarse, que es lo que Swing hace
     * solo en cada {@code componentResized}.
     */
    private Dimension tamanioDeFilas(Container target, boolean preferido) {
        synchronized (target.getTreeLock()) {
            int anchoDisponible = target.getSize().width;
            if (anchoDisponible == 0) {
                anchoDisponible = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int margenHorizontal = insets.left + insets.right + getHgap() * 2;
            int anchoMaximo = anchoDisponible - margenHorizontal;

            Dimension total = new Dimension(0, 0);
            int anchoFila = 0;
            int altoFila = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = preferido ? c.getPreferredSize() : c.getMinimumSize();

                if (anchoFila != 0 && anchoFila + getHgap() + d.width > anchoMaximo) {
                    acumularFila(total, anchoFila, altoFila);
                    anchoFila = 0;
                    altoFila = 0;
                }
                if (anchoFila != 0) {
                    anchoFila += getHgap();
                }
                anchoFila += d.width;
                altoFila = Math.max(altoFila, d.height);
            }
            acumularFila(total, anchoFila, altoFila);

            total.width  += margenHorizontal;
            total.height += insets.top + insets.bottom + getVgap() * 2;
            return total;
        }
    }

    private void acumularFila(Dimension total, int anchoFila, int altoFila) {
        total.width = Math.max(total.width, anchoFila);
        if (total.height > 0) {
            total.height += getVgap();
        }
        total.height += altoFila;
    }
}
