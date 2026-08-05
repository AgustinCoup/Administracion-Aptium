package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.features.equipos.ortopedias.model.EquipoAuditoria;
import java.time.LocalDate;
import java.util.List;

/**
 * Filtra el historial de auditoría por fecha, tipo de cambio y tipo de equipo.
 *
 * <p>Lógica pura sobre una lista ya cargada: sin Swing y sin base de datos.
 *
 * <p>Un filtro sin selección significa "sin filtrar", no "no mostrar nada": es
 * el estado inicial de los combos de la pantalla.
 */
public final class FiltroAuditorias {

    private static final String TIPO_EQUIPO_OTROS     = "OTROS";
    private static final String ETIQUETA_OTROS        = "Otros";
    private static final String ETIQUETA_ORTOPEDIA    = "Ortopedia";
    private static final String TIPO_CAMBIO_DESCONOCIDO = "Desconocido";

    private FiltroAuditorias() {
        throw new UnsupportedOperationException("Clase utilitaria no instanciable");
    }

    /**
     * Criterio de búsqueda tal como lo tiene puesto el usuario.
     *
     * @param desde        fecha mínima inclusive; null = sin límite
     * @param hasta        fecha máxima inclusive; null = sin límite
     * @param tiposCambio  etiquetas legibles seleccionadas; vacío = todos
     * @param tiposEquipo  "Ortopedia" y/o "Otros"; vacío = todos
     */
    public record Criterio(
        LocalDate    desde,
        LocalDate    hasta,
        List<String> tiposCambio,
        List<String> tiposEquipo
    ) {
        public Criterio {
            tiposCambio = List.copyOf(tiposCambio);
            tiposEquipo = List.copyOf(tiposEquipo);
        }

        public static Criterio sinFiltros() {
            return new Criterio(null, null, List.of(), List.of());
        }
    }

    public static List<EquipoAuditoria> filtrar(List<EquipoAuditoria> auditorias, Criterio criterio) {
        return auditorias.stream()
            .filter(a -> cumpleFechas(a, criterio))
            .filter(a -> cumpleTipoCambio(a, criterio))
            .filter(a -> cumpleTipoEquipo(a, criterio))
            .toList();
    }

    /**
     * Convierte el tipo de cambio interno a la etiqueta legible que se muestra
     * en la tabla y en el combo del filtro. Que ambos usen esta misma función es
     * lo que hace que el filtro y lo mostrado no se desincronicen.
     */
    public static String traducirTipoCambio(String tipoCambio) {
        if (tipoCambio == null) return TIPO_CAMBIO_DESCONOCIDO;
        switch (tipoCambio) {
            case "MODIFICACION_CANTIDAD": return "Modificación de Cantidad";
            case "MODIFICACION_CODIGO":   return "Modificación de Código";
            case "ADICION_MATERIAL":      return "Adición de Material";
            case "ELIMINACION_EQUIPO":    return "Eliminación de Equipo";
            case "ELIMINACION_MATERIAL":  return "Eliminación de Material";
            default:                      return tipoCambio;
        }
    }

    /** Un registro sin fecha nunca queda fuera: no hay con qué descartarlo. */
    private static boolean cumpleFechas(EquipoAuditoria auditoria, Criterio criterio) {
        if (auditoria.getFechaCambio() == null) return true;
        LocalDate fecha = auditoria.getFechaCambio().toLocalDate();

        if (criterio.desde() != null && fecha.isBefore(criterio.desde())) return false;
        return criterio.hasta() == null || !fecha.isAfter(criterio.hasta());
    }

    private static boolean cumpleTipoCambio(EquipoAuditoria auditoria, Criterio criterio) {
        if (criterio.tiposCambio().isEmpty()) return true;
        return criterio.tiposCambio().contains(traducirTipoCambio(auditoria.getTipoCambio()));
    }

    private static boolean cumpleTipoEquipo(EquipoAuditoria auditoria, Criterio criterio) {
        if (criterio.tiposEquipo().isEmpty()) return true;
        String etiqueta = TIPO_EQUIPO_OTROS.equals(auditoria.getTipoEquipo())
            ? ETIQUETA_OTROS : ETIQUETA_ORTOPEDIA;
        return criterio.tiposEquipo().contains(etiqueta);
    }
}
