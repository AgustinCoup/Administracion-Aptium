package com.example.features.eliminaciones.controller.helpers;

import com.example.common.constants.Constantes;
import com.example.common.constants.Constantes.Mensajes;
import com.example.common.eliminacion.ModuloIngreso;
import com.example.common.eliminacion.ResumenEliminacion;
import com.example.common.eliminacion.ResumenEquipo;
import com.example.common.eliminacion.ResumenIngresoLavadero;
import com.example.common.eliminacion.TextoBloqueos;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * El texto del diálogo de confirmación de eliminación. Clase plana, sin Swing y sin estado: el
 * diálogo sólo lo muestra.
 *
 * <p>Las plantillas viven en {@code Constantes.Mensajes}. Los bloqueos se arman con
 * {@link TextoBloqueos}, el mismo lugar que usa la excepción de la transacción: el operador lee
 * lo mismo se haya visto el bloqueo antes o haya aparecido por una carrera.</p>
 */
public final class TextoEliminacion {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern(Constantes.Formatos.FORMATO_FECHA);

    private TextoEliminacion() {}

    /** Qué se elimina, con el detalle que desaparece, y cierra con "No se puede deshacer". */
    public static String confirmacion(ResumenEliminacion resumen) {
        List<String> lineas = new ArrayList<>();
        if (resumen instanceof ResumenEquipo equipo) {
            agregarEquipo(lineas, equipo);
        } else if (resumen instanceof ResumenIngresoLavadero lavadero) {
            agregarLavadero(lineas, lavadero);
        }
        lineas.add("");
        lineas.add(Mensajes.ELIMINAR_SE_ARCHIVA);
        lineas.add(Mensajes.ELIMINAR_NO_SE_PUEDE_DESHACER);
        return String.join("\n", lineas);
    }

    /** El encabezado y una línea por bloqueo, con qué hacer. Igual al mensaje de la excepción. */
    public static String bloqueos(ResumenEliminacion resumen) {
        return Mensajes.ELIMINACION_BLOQUEADA + "\n" + TextoBloqueos.describir(resumen.bloqueos());
    }

    private static void agregarEquipo(List<String> lineas, ResumenEquipo r) {
        String titulo = r.ingreso().modulo() == ModuloIngreso.ORTOPEDIA
            ? Mensajes.ELIMINAR_TITULO_ORTOPEDIA : Mensajes.ELIMINAR_TITULO_OTROS;
        lineas.add(String.format(titulo, r.ingreso().id()));
        agregarSiHay(lineas, Mensajes.ELIMINAR_LINEA_CLIENTE, r.clienteNombre());
        agregarSiHay(lineas, Mensajes.ELIMINAR_LINEA_INSTITUCION, r.institucionNombre());
        agregarSiHay(lineas, Mensajes.ELIMINAR_LINEA_PACIENTE, r.pacienteNombre());
        agregarSiHay(lineas, Mensajes.ELIMINAR_LINEA_FECHA, fecha(r.fechaIngreso()));
        lineas.add(String.format(Mensajes.ELIMINAR_LINEA_ESTADO, r.estado()));
        if (r.materiales().isEmpty()) {
            lineas.add(Mensajes.ELIMINAR_SIN_MATERIALES);
        } else {
            lineas.add(Mensajes.ELIMINAR_ENCABEZADO_MATERIALES);
            r.materiales().forEach(m -> lineas.add(lineaMaterial(m)));
        }
        if (!r.ingresosLavaderoOrigen().isEmpty()) {
            lineas.add(String.format(Mensajes.ELIMINAR_VINO_DE_LAVADERO, numerales(r.ingresosLavaderoOrigen())));
        }
    }

    private static void agregarLavadero(List<String> lineas, ResumenIngresoLavadero r) {
        lineas.add(String.format(Mensajes.ELIMINAR_TITULO_LAVADERO, r.ingreso().id()));
        agregarSiHay(lineas, Mensajes.ELIMINAR_LINEA_CLIENTE, r.clienteNombre());
        agregarSiHay(lineas, Mensajes.ELIMINAR_LINEA_FECHA, fecha(r.fechaIngreso()));
        lineas.add(String.format(Mensajes.ELIMINAR_LINEA_ESTADO, r.estado().name()));
        lineas.add(String.format(Mensajes.ELIMINAR_LINEA_PESO, r.pesoTotalKg().toPlainString()));
        if (r.elementos().isEmpty()) {
            lineas.add(Mensajes.ELIMINAR_SIN_CLASIFICAR);
        } else {
            lineas.add(Mensajes.ELIMINAR_ENCABEZADO_ELEMENTOS);
            r.elementos().forEach(e ->
                lineas.add(String.format(Mensajes.ELIMINAR_LINEA_ELEMENTO, e.cantidad(), e.nombre())));
        }
        r.derivados().forEach(d -> lineas.add(String.format(
            Mensajes.ELIMINAR_DERIVADO_DEL_CDE, d.equipoOtrosId(), d.estado(), d.unidades())));
    }

    private static String lineaMaterial(ResumenEquipo.LineaMaterial m) {
        return m.loteIdNegocio() == null
            ? String.format(Mensajes.ELIMINAR_LINEA_MATERIAL, m.cantidad(), m.descripcion(), m.estado())
            : String.format(Mensajes.ELIMINAR_LINEA_MATERIAL_EN_LOTE,
                m.cantidad(), m.descripcion(), m.estado(), m.loteIdNegocio());
    }

    private static void agregarSiHay(List<String> lineas, String plantilla, String valor) {
        if (valor != null && !valor.isBlank()) {
            lineas.add(String.format(plantilla, valor));
        }
    }

    private static String fecha(LocalDateTime fecha) {
        return fecha == null ? null : FECHA.format(fecha);
    }

    private static String numerales(List<Integer> ids) {
        return ids.stream().map(id -> "#" + id).collect(Collectors.joining(", "));
    }
}
