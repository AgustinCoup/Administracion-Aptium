package com.example.ui.common;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lógica de decisión del botón "Actualizar" de {@link PanelHeader}, extraída a
 * una clase plana sin dependencias de Swing para poder testearla headless
 * (misma convención que {@code AgrupadorIngresosLote} o {@code DuplicadoHighlighter};
 * {@code JOptionPane.showConfirmDialog} tira {@code HeadlessException} en los tests).
 *
 * <p>El botón no lee datos: sólo decide <em>si</em> corresponde disparar la
 * relectura que la pantalla ya sabe hacer, y qué pasa con el trabajo en curso.
 *
 * <ul>
 *   <li>Sin guard ({@code hayPendientes == null}) → siempre refresca.</li>
 *   <li>Con guard pero sin pendientes → refresca sin consultar al confirmador.</li>
 *   <li>Con pendientes → consulta al {@code confirmador}; si dice que no, no
 *       refresca ni descarta nada; si dice que sí, corre
 *       {@code onDescartarConfirmado} (si lo hay) y refresca.</li>
 * </ul>
 */
public final class GuardaRefresco {

    private final Supplier<Boolean> hayPendientes;
    private final String mensaje;
    private final Runnable onDescartarConfirmado;
    private final Predicate<String> confirmador;

    /**
     * @param hayPendientes         {@code null} = pantalla sin guard; si no,
     *                              retorna {@code true} cuando hay trabajo sin guardar
     * @param mensaje               texto que recibe el confirmador (el cartel de la pantalla)
     * @param onDescartarConfirmado acción a correr cuando el operador confirma el
     *                              descarte; {@code null} en las pantallas donde lo
     *                              pendiente se conserva
     * @param confirmador           pregunta al operador y devuelve {@code true} si acepta
     */
    public GuardaRefresco(Supplier<Boolean> hayPendientes, String mensaje,
                          Runnable onDescartarConfirmado, Predicate<String> confirmador) {
        this.hayPendientes = hayPendientes;
        this.mensaje = mensaje;
        this.onDescartarConfirmado = onDescartarConfirmado;
        this.confirmador = confirmador;
    }

    /** @return true si corresponde disparar la relectura de la pantalla. */
    public boolean debeRefrescar() {
        if (hayPendientes == null || !Boolean.TRUE.equals(hayPendientes.get())) {
            return true;
        }
        if (!confirmador.test(mensaje)) {
            return false;
        }
        if (onDescartarConfirmado != null) {
            onDescartarConfirmado.run();
        }
        return true;
    }
}
