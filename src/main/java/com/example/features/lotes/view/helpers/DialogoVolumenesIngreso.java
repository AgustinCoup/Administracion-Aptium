package com.example.features.lotes.view.helpers;

import com.example.features.lotes.controller.helpers.IngresoPendienteInfo;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Diálogo de lanzamiento de lote cuando hay materiales "otros": pide los litros
 * por ingreso (cliente + ingreso con elementos en el lote), recalcula el
 * volumen en vivo y confirma el volumen final. Reemplaza, para este caso, a la
 * confirmación de texto y al campo "Volumen final" del panel.
 *
 * <p>Solo presentación: la agrupación viene resuelta (AgrupadorIngresosLote) y
 * la validación de negocio vive en LoteService.
 */
public final class DialogoVolumenesIngreso {

    private static final int LITROS_MIN = 1;
    private static final int LITROS_MAX = 10000;

    /** Resultado confirmado: litros por ingreso + volumen final del lote. */
    public static final class ResultadoLanzamiento {
        private final Map<Integer, Integer> litrosPorIngreso;
        private final int volumenFinal;

        private ResultadoLanzamiento(Map<Integer, Integer> litrosPorIngreso, int volumenFinal) {
            this.litrosPorIngreso = litrosPorIngreso;
            this.volumenFinal     = volumenFinal;
        }

        public Map<Integer, Integer> getLitrosPorIngreso() { return litrosPorIngreso; }
        public int getVolumenFinal()                       { return volumenFinal; }
    }

    private DialogoVolumenesIngreso() { }

    /**
     * @param parent            componente padre para el modal
     * @param ingresos          filas (cliente, ingreso, cantidad) a completar
     * @param resumenMateriales líneas "descripcion (xN)" de todos los materiales del lote
     * @param volumenOrtopedias volumen calculado de los materiales de ortopedia
     * @param capacidadTotal    capacidad del autoclave (tope duro del volumen final)
     * @return litros por ingreso + volumen final, o vacío si el usuario cancela
     */
    public static Optional<ResultadoLanzamiento> mostrar(Component parent,
                                                         List<IngresoPendienteInfo> ingresos,
                                                         List<String> resumenMateriales,
                                                         int volumenOrtopedias,
                                                         int capacidadTotal) {
        Map<Integer, JSpinner> spinnersPorIngreso = new LinkedHashMap<>();
        JSpinner spVolumenFinal = new JSpinner(
                new SpinnerNumberModel(1, 1, Math.max(LITROS_MIN, capacidadTotal), 1));
        spVolumenFinal.setEditor(new JSpinner.NumberEditor(spVolumenFinal, "0"));
        JLabel lblCalculado  = new JLabel();
        JLabel lblAdvertencia = new JLabel(" ");
        lblAdvertencia.setForeground(new Color(178, 88, 0));

        SincronizadorVolumenFinal sincronizador =
                new SincronizadorVolumenFinal(volumenOrtopedias, capacidadTotal);
        // true mientras el código (no el usuario) está seteando spVolumenFinal, para
        // no confundir esa escritura programática con una edición manual.
        final boolean[] sincronizando = {false};

        Runnable recalcular = () -> {
            int totalIngresos = 0;
            for (JSpinner sp : spinnersPorIngreso.values()) totalIngresos += (Integer) sp.getValue();
            int propuesto = sincronizador.onLitrosIngresoChange(totalIngresos);
            lblCalculado.setText(sincronizador.textoCalculado());
            // No-op si el usuario ya editó a mano: SpinnerNumberModel no dispara el
            // listener cuando el valor no cambia, así que esto no reabre el latch.
            sincronizando[0] = true;
            spVolumenFinal.setValue(propuesto);
            sincronizando[0] = false;
            lblAdvertencia.setText(sincronizador.textoAdvertencia());
        };

        JPanel contenido = construirPanel(ingresos, resumenMateriales, spinnersPorIngreso,
                spVolumenFinal, lblCalculado, lblAdvertencia);

        for (JSpinner sp : spinnersPorIngreso.values()) sp.addChangeListener(e -> recalcular.run());
        spVolumenFinal.addChangeListener(e -> {
            if (!sincronizando[0]) {
                sincronizador.onVolumenFinalEditadoPorUsuario((Integer) spVolumenFinal.getValue());
                lblAdvertencia.setText(sincronizador.textoAdvertencia());
            }
        });
        recalcular.run();

        // showOptionDialog con botones propios devuelve el ÍNDICE del elegido dentro
        // del array, no OK_OPTION/CANCEL_OPTION: hay que comparar contra la posición
        // de "Lanzar". El botón por defecto sale del mismo índice para que reordenar
        // el array no pueda dejar la comparación apuntando al botón equivocado.
        Object[] botones = {"Lanzar", "Cancelar"};
        final int indiceLanzar = 0;
        int opcion = JOptionPane.showOptionDialog(parent, contenido,
                "Confirmar Lanzamiento de Lote", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, botones, botones[indiceLanzar]);
        if (opcion != indiceLanzar) return Optional.empty();

        Map<Integer, Integer> litros = new LinkedHashMap<>();
        for (Map.Entry<Integer, JSpinner> entry : spinnersPorIngreso.entrySet()) {
            litros.put(entry.getKey(), (Integer) entry.getValue().getValue());
        }
        return Optional.of(new ResultadoLanzamiento(litros, (Integer) spVolumenFinal.getValue()));
    }

    // ── Construcción del panel ───────────────────────────────────────────────

    private static JPanel construirPanel(List<IngresoPendienteInfo> ingresos,
                                         List<String> resumenMateriales,
                                         Map<Integer, JSpinner> spinnersPorIngreso,
                                         JSpinner spVolumenFinal,
                                         JLabel lblCalculado,
                                         JLabel lblAdvertencia) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));

        StringBuilder resumen = new StringBuilder("Se lanzará el lote con los siguientes materiales:\n\n");
        for (String linea : resumenMateriales) resumen.append("• ").append(linea).append("\n");
        JTextArea areaResumen = new JTextArea(resumen.toString());
        areaResumen.setEditable(false);
        areaResumen.setOpaque(false);
        panel.add(areaResumen, BorderLayout.NORTH);

        JPanel tabla = new JPanel(new GridBagLayout());
        tabla.setBorder(BorderFactory.createTitledBorder("Litros por ingreso"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridy = 0;
        gbc.gridx = 0; tabla.add(new JLabel("<html><b>Cliente</b></html>"), gbc);
        gbc.gridx = 1; tabla.add(new JLabel("<html><b>Ingreso</b></html>"), gbc);
        gbc.gridx = 2; tabla.add(new JLabel("<html><b>Cantidad</b></html>"), gbc);
        gbc.gridx = 3; tabla.add(new JLabel("<html><b>Litros</b></html>"), gbc);

        for (IngresoPendienteInfo ingreso : ingresos) {
            gbc.gridy++;
            gbc.gridx = 0; tabla.add(new JLabel(ingreso.getClienteNombre()), gbc);
            gbc.gridx = 1; tabla.add(new JLabel(ingreso.getEtiquetaIngreso()), gbc);
            gbc.gridx = 2; tabla.add(new JLabel(String.valueOf(ingreso.getCantidadTotal())), gbc);
            JSpinner sp = new JSpinner(new SpinnerNumberModel(LITROS_MIN, LITROS_MIN, LITROS_MAX, 1));
            sp.setEditor(new JSpinner.NumberEditor(sp, "0"));
            gbc.gridx = 3; tabla.add(sp, gbc);
            spinnersPorIngreso.put(ingreso.getEquipoOtrosId(), sp);
        }
        panel.add(new JScrollPane(tabla), BorderLayout.CENTER);

        JPanel pie = new JPanel(new GridBagLayout());
        GridBagConstraints g2 = new GridBagConstraints();
        g2.insets = new Insets(2, 6, 2, 6);
        g2.anchor = GridBagConstraints.WEST;
        g2.gridx = 0; g2.gridy = 0; g2.gridwidth = 2;
        pie.add(lblCalculado, g2);
        g2.gridy = 1; g2.gridwidth = 1;
        pie.add(new JLabel("Volumen final:"), g2);
        g2.gridx = 1;
        pie.add(spVolumenFinal, g2);
        g2.gridx = 0; g2.gridy = 2; g2.gridwidth = 2;
        pie.add(lblAdvertencia, g2);
        panel.add(pie, BorderLayout.SOUTH);

        return panel;
    }
}
