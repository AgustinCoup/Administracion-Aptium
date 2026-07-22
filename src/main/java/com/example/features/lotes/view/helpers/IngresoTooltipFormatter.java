package com.example.features.lotes.view.helpers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Arma el texto HTML del tooltip que se muestra al pasar el cursor sobre una fila
 * de material en Gestionar Lotes.
 *
 * <p>Lógica pura: sin dependencias de Swing, testeable en aislamiento. El glue de
 * Swing vive en {@code com.example.ui.common.RowTooltipTable}.</p>
 */
public final class IngresoTooltipFormatter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String GUION = "—";

    private IngresoTooltipFormatter() { }

    /**
     * @param item fila señalada; aporta el nombre del material del encabezado
     * @param info datos del ingreso de origen, o null si no se pudo resolver
     * @return HTML listo para {@code JComponent.setToolTipText}
     */
    public static String format(MaterialLoteItem item, IngresoInfo info) {
        String descripcion = escapar(item != null ? item.getDescripcion() : null);

        StringBuilder sb = new StringBuilder("<html><b>").append(descripcion).append("</b>");
        if (info == null) {
            return sb.append("<br>Sin datos de ingreso</html>").toString();
        }

        linea(sb, "Cliente", info.getClienteNombre());
        if (info.isEsOtros()) {
            if (info.isEsRemito()) linea(sb, "Remito", info.getRemitoId());
        } else {
            linea(sb, "Profesional", info.getProfesionalNombre());
            linea(sb, "Paciente",    info.getPacienteNombre());
            linea(sb, "Institución", info.getInstitucionNombre());
        }
        linea(sb, "Ingreso", formatearFecha(info.getFechaIngreso()));

        return sb.append("</html>").toString();
    }

    private static void linea(StringBuilder sb, String etiqueta, String valor) {
        sb.append("<br>").append(etiqueta).append(": ").append(escapar(orGuion(valor)));
    }

    private static String formatearFecha(LocalDateTime fecha) {
        return fecha != null ? fecha.format(FMT) : null;
    }

    private static String orGuion(String valor) {
        return (valor == null || valor.trim().isEmpty()) ? GUION : valor;
    }

    /** Evita que un texto del usuario con &lt; o &amp; rompa el HTML del tooltip. */
    private static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}
