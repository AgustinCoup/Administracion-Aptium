package com.example.features.lotes.controller.helpers;

import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.lotes.view.helpers.MaterialLoteItem;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa los materiales "otros" pendientes de un autoclave por ingreso
 * (equipo_otros), produciendo las filas que muestra DialogoVolumenesIngreso.
 * Lógica pura, sin Swing: testeable en aislamiento.
 */
public final class AgrupadorIngresosLote {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private AgrupadorIngresosLote() { }

    /**
     * @param pendientes        materiales cargados en el autoclave (ortopedia se ignora)
     * @param equiposOtrosPorId equipos otros indexados por id, para etiquetar cada ingreso
     * @return una fila por ingreso, en orden de aparición en {@code pendientes}
     */
    public static List<IngresoPendienteInfo> agrupar(List<MaterialLoteItem> pendientes,
                                                     Map<Integer, EquipoOtros> equiposOtrosPorId) {
        Map<Integer, IngresoPendienteInfo> porIngreso = new LinkedHashMap<>();
        for (MaterialLoteItem item : pendientes) {
            if (!item.isEsOtros()) continue;
            int ingresoId = item.getEquipoId();
            IngresoPendienteInfo previo = porIngreso.get(ingresoId);
            if (previo == null) {
                porIngreso.put(ingresoId, new IngresoPendienteInfo(
                    ingresoId,
                    item.getClienteNombre(),
                    etiquetar(ingresoId, equiposOtrosPorId.get(ingresoId)),
                    item.getCantidad()));
            } else {
                porIngreso.put(ingresoId, new IngresoPendienteInfo(
                    previo.getEquipoOtrosId(),
                    previo.getClienteNombre(),
                    previo.getEtiquetaIngreso(),
                    previo.getCantidadTotal() + item.getCantidad()));
            }
        }
        return new ArrayList<>(porIngreso.values());
    }

    /** REMITO → remito_id; DETALLES → fecha de ingreso + id; sin datos → id solo. */
    private static String etiquetar(int ingresoId, EquipoOtros equipo) {
        if (equipo != null && equipo.getRemitoId() != null) {
            return equipo.getRemitoId();
        }
        if (equipo != null && equipo.getFechaIngreso() != null) {
            return "Ingreso " + equipo.getFechaIngreso().format(FORMATO_FECHA) + " (#" + ingresoId + ")";
        }
        return "Ingreso #" + ingresoId;
    }
}
