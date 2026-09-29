package com.example.common.eliminacion;

import com.example.common.constants.Constantes.Mensajes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * El texto para el operador de cada {@link Bloqueo}: una línea por bloqueo, que dice <b>qué
 * hacer</b>.
 *
 * <p>Es el único lugar que arma estos textos. Lo usan el resumen previo (el diálogo, antes de
 * pedir la password) y {@link EliminacionBloqueadaException} (cuando el bloqueo apareció por una
 * carrera, dentro de la transacción). Clase plana, sin estado.</p>
 */
public final class TextoBloqueos {

    private TextoBloqueos() {}

    /** Una línea por bloqueo, en el orden recibido, separadas por {@code \n}. */
    public static String describir(List<Bloqueo> bloqueos) {
        return bloqueos.stream()
            .map(TextoBloqueos::describir)
            .collect(Collectors.joining("\n"));
    }

    public static String describir(Bloqueo bloqueo) {
        if (bloqueo instanceof Bloqueo.LoteEnCurso l) {
            return String.format(Mensajes.ELIMINAR_BLOQUEO_LOTE_EN_CURSO, l.loteIdNegocio());
        }
        if (bloqueo instanceof Bloqueo.CicloEnCurso c) {
            return String.format(Mensajes.ELIMINAR_BLOQUEO_CICLO_EN_CURSO, c.lavarropasNumero());
        }
        if (bloqueo instanceof Bloqueo.DerivadoCompartido d) {
            return String.format(Mensajes.ELIMINAR_BLOQUEO_DERIVADO_COMPARTIDO,
                d.equipoOtrosId(), numeros(d.otrosIngresosLavadero()));
        }
        if (bloqueo instanceof Bloqueo.DerivadoEnLoteEnCurso d) {
            return String.format(Mensajes.ELIMINAR_BLOQUEO_DERIVADO_EN_LOTE,
                d.equipoOtrosId(), d.loteIdNegocio());
        }
        throw new IllegalArgumentException("Bloqueo sin texto: " + bloqueo);
    }

    private static String numeros(List<Integer> ids) {
        return ids.stream().map(id -> "#" + id).collect(Collectors.joining(", "));
    }
}
