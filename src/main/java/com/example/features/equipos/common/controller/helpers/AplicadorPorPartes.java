package com.example.features.equipos.common.controller.helpers;

import com.example.common.exception.ConflictoConcurrenciaException;
import com.example.common.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Corre una escritura por parte —un equipo en Registrar Estado, un destino en Entregas— y
 * clasifica cada parte en exitosa, con error o con conflicto.
 *
 * <p>Es la parte del "confirmar" que no habla con Swing: el loop y la clasificación. Vive aparte
 * para testearla en aislamiento, sin EDT ni base. El despacho al service y el armado de mensajes
 * quedan en cada controller.
 *
 * <p><b>El loop no corta.</b> Cada parte es su propia transacción: las que ya pasaron ya
 * commitearon. Cortar ante un fallo dejaría al operador sin saber cuáles se escribieron, y al
 * reintentar chocaría contra su propio trabajo.
 */
public final class AplicadorPorPartes {

    private static final Logger log = LoggerFactory.getLogger(AplicadorPorPartes.class);

    /**
     * Escritura de una parte; {@code false} = el service no aplicó el cambio.
     *
     * <p>Puede propagar {@link ConflictoConcurrenciaException} o {@link DatabaseException}: las
     * atrapa {@link #aplicarTodos} y las clasifica. Cualquier otra excepción sale del loop.
     */
    @FunctionalInterface
    public interface Operacion<K, V> {
        boolean aplicar(K parte, V datos);
    }

    /**
     * Partes clasificadas por desenlace, cada lista en el orden en que se intentaron:
     * {@code conError} son fallos técnicos, {@code conConflicto} son choques de concurrencia. Se
     * distinguen porque sobre una parte que chocó el operador tiene que rehacer su trabajo con los
     * datos frescos, no reintentar a ciegas.
     */
    public record Resultado<K>(List<K> exitosas, List<K> conError, List<K> conConflicto) {
        public Resultado {
            exitosas = List.copyOf(exitosas);
            conError = List.copyOf(conError);
            conConflicto = List.copyOf(conConflicto);
        }

        public boolean todosExitosos() {
            return conError.isEmpty() && conConflicto.isEmpty();
        }

        public boolean algunaExitosa() {
            return !exitosas.isEmpty();
        }
    }

    private AplicadorPorPartes() {
    }

    /**
     * Corre {@code operacion} una vez por parte, en el orden del mapa (el llamador pasa un
     * {@code LinkedHashMap}). Cómo clasifica:
     * <ul>
     *   <li>{@code true} → exitosa;</li>
     *   <li>{@code false} → error: el service ya decidió que no se aplicó;</li>
     *   <li>{@link ConflictoConcurrenciaException} → conflicto: otro se adelantó, no es un error;</li>
     *   <li>{@link DatabaseException} → error de <em>esa</em> parte, logueado, y se sigue: un
     *       fallo técnico en una transacción no dice nada de las demás, y cortar escondería las que
     *       ya commitearon;</li>
     *   <li>cualquier otra {@code RuntimeException} <b>propaga</b>: un NPE o una
     *       {@code ValidationException} son bugs, y contarlos como "una parte que falló" los
     *       escondería detrás de un cartel.</li>
     * </ul>
     */
    public static <K, V> Resultado<K> aplicarTodos(Map<K, V> partes, Operacion<K, V> operacion) {
        List<K> exitosas = new ArrayList<>();
        List<K> conError = new ArrayList<>();
        List<K> conConflicto = new ArrayList<>();
        for (Map.Entry<K, V> entrada : partes.entrySet()) {
            K parte = entrada.getKey();
            try {
                if (operacion.aplicar(parte, entrada.getValue())) {
                    exitosas.add(parte);
                } else {
                    conError.add(parte);
                }
            } catch (ConflictoConcurrenciaException e) {
                conConflicto.add(parte);
            } catch (DatabaseException e) {
                log.error("Falló la escritura de la parte {}; se sigue con las demás", parte, e);
                conError.add(parte);
            }
        }
        return new Resultado<>(exitosas, conError, conConflicto);
    }
}
