package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.common.constants.Constantes;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.common.model.FilaAEntregar;
import com.example.common.model.RemitoAEntregar;
import com.example.features.equipos.common.controller.helpers.AplicadorPorPartes;
import com.example.features.equipos.ortopedias.view.helpers.InstitucionEntregaItem;
import com.example.features.equipos.ortopedias.view.helpers.MaterialEntregaItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Decide qué se entrega con la selección de Para Entregar, y arma el texto de la confirmación.
 *
 * <p>Lógica pura, sin Swing: el controller orquesta y no calcula. Lo que se confirma es
 * exactamente lo que viaja a la escritura — los ítems ya traen sus filas.
 */
public final class PlanificadorEntrega {

    /** Lo que se entrega a un destino. Una transacción por destino. */
    public record SolicitudEntrega(EntregaDestinoKey destino, String nombreDestino,
                                   List<MaterialEntregaItem> items) {

        public SolicitudEntrega {
            items = List.copyOf(items);
        }

        public List<FilaAEntregar> filas() {
            return items.stream().flatMap(i -> i.filas().stream()).toList();
        }

        public List<RemitoAEntregar> remitos() {
            return items.stream().flatMap(i -> i.remitos().stream()).toList();
        }
    }

    public sealed interface ResultadoPlan permits Rechazo, Plan { }

    /** No hay nada que entregar: {@code mensaje} va al operador. */
    public record Rechazo(String mensaje) implements ResultadoPlan { }

    /**
     * @param solicitudes una por destino con algo para entregar, en el orden de la tabla: va
     *                    directo a {@code AplicadorPorPartes}
     */
    public record Plan(Map<EntregaDestinoKey, SolicitudEntrega> solicitudes, String textoConfirmacion)
            implements ResultadoPlan {

        public Plan {
            solicitudes = Collections.unmodifiableMap(new LinkedHashMap<>(solicitudes));
        }
    }

    /**
     * Los textos del resultado de una entrega por partes. {@code null} en un campo significa que
     * esa categoría no tiene nada que decir: {@code info} vacío si no hubo ninguna exitosa,
     * {@code error} vacío si no hubo error ni conflicto.
     */
    public record ResultadoTexto(String info, String error) { }

    private PlanificadorEntrega() {
    }

    /**
     * El alcance:
     * <ul>
     *   <li><b>varios</b> destinos → todo lo de cada uno. {@code materialesSeleccionados} se ignora:
     *       con dos o más destinos la tabla de materiales está vacía;</li>
     *   <li><b>uno</b> con materiales seleccionados → sólo ésos;</li>
     *   <li><b>uno</b> sin selección → todo.</li>
     * </ul>
     * Los destinos sin nada se excluyen; si no queda ninguno, se rechaza.
     */
    public static ResultadoPlan planificar(List<InstitucionEntregaItem> destinosSeleccionados,
                                           Map<EntregaDestinoKey, List<MaterialEntregaItem>> materialesPorDestino,
                                           List<MaterialEntregaItem> materialesSeleccionados) {
        if (destinosSeleccionados.isEmpty()) {
            return new Rechazo(Constantes.Mensajes.ENTREGA_SELECCIONE_DESTINO);
        }
        boolean soloLoSeleccionado = destinosSeleccionados.size() == 1 && !materialesSeleccionados.isEmpty();

        Map<EntregaDestinoKey, SolicitudEntrega> solicitudes = new LinkedHashMap<>();
        for (InstitucionEntregaItem destino : destinosSeleccionados) {
            List<MaterialEntregaItem> items = materialesPorDestino.getOrDefault(destino.getKey(), List.of());
            if (soloLoSeleccionado) {
                // Se filtra la lista del destino, no se toma la selección tal cual: conserva el
                // orden de la tabla y deja afuera cualquier cosa que no sea de este destino.
                items = items.stream().filter(materialesSeleccionados::contains).toList();
            }
            if (!items.isEmpty()) {
                solicitudes.put(destino.getKey(),
                    new SolicitudEntrega(destino.getKey(), destino.getNombre(), items));
            }
        }

        if (solicitudes.isEmpty()) {
            return new Rechazo(Constantes.Mensajes.ENTREGA_SIN_PENDIENTES);
        }
        return new Plan(solicitudes, textoConfirmacion(solicitudes.values()));
    }

    /** Una línea por ítem, con su ingreso: dos "Tornillo" de ingresos distintos son dos líneas. */
    private static String textoConfirmacion(Iterable<SolicitudEntrega> solicitudes) {
        StringBuilder sb = new StringBuilder(Constantes.Mensajes.ENTREGA_CONFIRMAR_PREGUNTA).append("\n\n");
        for (SolicitudEntrega solicitud : solicitudes) {
            String tipo = solicitud.destino().getTipo() == TipoDestino.CLIENTE
                ? Constantes.Textos.ENTIDAD_CLIENTE
                : Constantes.Textos.ENTIDAD_INSTITUCION;
            sb.append(String.format(Constantes.Mensajes.ENTREGA_CONFIRMACION_DESTINO,
                tipo, solicitud.nombreDestino())).append('\n');
            for (MaterialEntregaItem item : solicitud.items()) {
                sb.append(String.format(Constantes.Mensajes.ENTREGA_CONFIRMACION_ITEM,
                    item.ingreso(), item.material(), item.cantidad())).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Los textos del resultado de {@code AplicadorPorPartes.aplicarTodos}, nombrando los destinos
     * de cada categoría por su nombre (no por la clave). Exitosas y (error + conflicto) son
     * independientes: pueden convivir en el mismo resultado si algunos destinos se entregaron y
     * otros no.
     */
    public static ResultadoTexto describirResultado(AplicadorPorPartes.Resultado<EntregaDestinoKey> resultado,
                                                     Map<EntregaDestinoKey, SolicitudEntrega> solicitudes) {
        String info = resultado.exitosas().isEmpty() ? null
            : String.format(Constantes.Mensajes.ENTREGA_RESULTADO_OK, nombres(resultado.exitosas(), solicitudes));

        List<String> partesError = new ArrayList<>();
        if (!resultado.conConflicto().isEmpty()) {
            partesError.add(Constantes.Mensajes.CONFLICTO_ENTREGA + "\n"
                + nombres(resultado.conConflicto(), solicitudes));
        }
        if (!resultado.conError().isEmpty()) {
            partesError.add(String.format(Constantes.Mensajes.ENTREGA_RESULTADO_ERROR,
                nombres(resultado.conError(), solicitudes)));
        }
        String error = partesError.isEmpty() ? null : String.join("\n\n", partesError);

        return new ResultadoTexto(info, error);
    }

    private static String nombres(List<EntregaDestinoKey> destinos,
                                  Map<EntregaDestinoKey, SolicitudEntrega> solicitudes) {
        return destinos.stream()
            .map(destino -> solicitudes.get(destino).nombreDestino())
            .collect(Collectors.joining(", "));
    }
}
