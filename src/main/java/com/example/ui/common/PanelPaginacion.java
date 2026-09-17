package com.example.ui.common;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

import com.example.common.constants.Constantes;
import com.example.common.paginacion.Pagina;

/**
 * Barra de paginación reutilizable: "Mostrando X-Y de Z" más las pestañas numeradas.
 *
 * <h2>No lee datos</h2>
 * Este componente <b>no</b> sabe de DAOs, de services ni de {@code TareaUI}: cuando el operador
 * elige una página emite el número por {@link #setAlCambiarPagina(IntConsumer)} y termina ahí. Es
 * la misma separación que {@code TareaUI} hace entre {@code leer} y {@code pintar}: quien lee es el
 * controller, en el hilo de fondo, y vuelve acá con {@link #mostrar(Pagina)}. Un componente de UI
 * que dispara lecturas por su cuenta es exactamente lo que este repo evita en todos lados.
 *
 * <p>El ciclo completo es: click → {@code alCambiarPagina.accept(n)} → el controller lanza su
 * {@code TareaUI} → en su {@code pintar} llama a {@code mostrar(pagina)}. Si la lectura falla, la
 * barra queda como estaba, que es lo correcto: sigue describiendo lo que la tabla muestra.
 *
 * <h2>Dos decisiones visibles</h2>
 * <ul>
 *   <li><b>Con una sola página se oculta entera.</b> Una barra de paginación sobre 12 filas es
 *       ruido, y es el caso habitual en las pantallas de volumen chico.</li>
 *   <li><b>Al cambiar de página no se toca el scroll de la tabla.</b> Este panel no conoce la
 *       tabla, así que no puede tocarlo ni por accidente; la tabla se repinta desde arriba. Es lo
 *       esperado y evita el "cambié de página y quedé a mitad".</li>
 * </ul>
 */
public class PanelPaginacion extends JPanel {

    /**
     * Cuántos números de página se dibujan alrededor del actual. Con más páginas que esto aparecen
     * la primera, la última y elipsis: 40 botones no entran en pantalla y no se leen.
     */
    private static final int NUMEROS_VISIBLES = 7;

    private final JLabel lblRango = new JLabel();
    private final JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));

    private IntConsumer alCambiarPagina = numero -> { };

    public PanelPaginacion() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 4));
        setOpaque(false);
        panelBotones.setOpaque(false);
        lblRango.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        lblRango.setForeground(Estilos.Colores.TEXTO_AYUDA);
        add(lblRango);
        add(panelBotones);
        setVisible(false);
    }

    /**
     * Qué hacer cuando el operador pide otra página. Recibe el número pedido, base 1.
     *
     * <p>El componente no valida contra el total más allá de no ofrecer páginas que no existen:
     * quien lee decide qué hacer si el mundo cambió entre el click y la lectura.
     */
    public void setAlCambiarPagina(IntConsumer accion) {
        this.alCambiarPagina = accion != null ? accion : numero -> { };
    }

    /**
     * Actualiza lo que se ve a partir de la página que la pantalla acaba de pintar.
     *
     * <p>Se llama desde el {@code pintar()} del controller, en el EDT, con la misma página cuyas
     * filas se cargaron en la tabla.
     */
    public void mostrar(Pagina<?> pagina) {
        int total = pagina.totalPaginas();
        if (total <= 1) {
            setVisible(false);
            return;
        }
        setVisible(true);

        lblRango.setText(String.format(Constantes.Textos.PAGINACION_RANGO,
                pagina.primeraFila(), pagina.ultimaFila(), pagina.totalFilas()));

        int actual = pagina.numeroPagina();
        panelBotones.removeAll();
        agregarNavegacion(Constantes.Botones.PAGINA_ANTERIOR, actual - 1, !pagina.esPrimera());

        int[] ventana = ventana(actual, total, NUMEROS_VISIBLES);
        if (ventana[0] > 1) {
            agregarNumero(1, actual);
            agregarElipsis();
        }
        for (int numero = ventana[0]; numero <= ventana[1]; numero++) {
            agregarNumero(numero, actual);
        }
        if (ventana[1] < total) {
            agregarElipsis();
            agregarNumero(total, actual);
        }

        agregarNavegacion(Constantes.Botones.PAGINA_SIGUIENTE, actual + 1, !pagina.esUltima());
        panelBotones.revalidate();
        panelBotones.repaint();
    }

    /**
     * Primer y último número de la ventana deslizante, ambos inclusive.
     *
     * <p>Se centra en {@code actual} y se corre contra los extremos para conservar el ancho: cerca
     * del principio o del final la ventana no se achica, se desplaza. Sin eso la barra cambia de
     * tamaño al navegar y los botones se mueven bajo el cursor.
     *
     * <p>{@code static} y visible al paquete a propósito: es la única aritmética de esta clase, así
     * que si alguna vez necesita un test, lo puede tener sin construir un {@code JPanel}.
     */
    static int[] ventana(int actual, int total, int visibles) {
        if (total <= visibles) {
            return new int[]{1, total};
        }
        int desde = actual - visibles / 2;
        int hasta = desde + visibles - 1;
        if (desde < 1) {
            desde = 1;
            hasta = visibles;
        } else if (hasta > total) {
            hasta = total;
            desde = total - visibles + 1;
        }
        return new int[]{desde, hasta};
    }

    private void agregarNumero(int numero, int actual) {
        JButton boton = crearBoton(String.valueOf(numero), numero, numero != actual);
        if (numero == actual) {
            boton.setFont(boton.getFont().deriveFont(Font.BOLD));
        }
        panelBotones.add(boton);
    }

    private void agregarNavegacion(String texto, int destino, boolean habilitado) {
        panelBotones.add(crearBoton(texto, destino, habilitado));
    }

    private JButton crearBoton(String texto, int destino, boolean habilitado) {
        JButton boton = new JButton(texto);
        boton.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        boton.setMargin(new Insets(2, 6, 2, 6));
        boton.setFocusable(false);
        boton.setEnabled(habilitado);
        if (habilitado) {
            boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            boton.addActionListener(e -> alCambiarPagina.accept(destino));
        }
        return boton;
    }

    private void agregarElipsis() {
        JLabel elipsis = new JLabel(Constantes.Textos.PAGINACION_ELIPSIS);
        elipsis.setFont(Estilos.Fuentes.TABLA_CONTENIDO);
        elipsis.setForeground(Estilos.Colores.TEXTO_AYUDA);
        panelBotones.add(elipsis);
    }
}
