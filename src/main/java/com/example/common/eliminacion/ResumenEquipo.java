package com.example.common.eliminacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * El resumen previo de un ingreso del CDE ({@code equipos} o {@code equipo_otros}).
 *
 * @param ingreso                 módulo {@code ORTOPEDIA} u {@code OTROS}
 * @param clienteNombre           nullable
 * @param institucionNombre       sólo ortopedias; {@code null} en Otros
 * @param pacienteNombre          sólo ortopedias; {@code null} en Otros o si no se cargó
 * @param fechaIngreso            nullable
 * @param estado                  el nombre del {@code EstadoEquipo} de la cabecera
 * @param version                 <b>el token de guarda</b>: el borrado se aborta como conflicto si
 *                                la cabecera ya no tiene esta {@code version}
 * @param materiales              lo que desaparece con el ingreso
 * @param bloqueos                {@link Bloqueo.LoteEnCurso}, uno por lote en curso
 * @param ingresosLavaderoOrigen  los ingresos de Lavadero cuyas salidas se derivaron a este
 *                                {@code equipo_otros}; vacío si se cargó a mano o si es de
 *                                ortopedias. <b>Esos ingresos no se eliminan</b>: sus salidas
 *                                quedan con {@code equipo_otros_id = NULL}. Es una lista y no un
 *                                solo número porque un derivado puede ser compartido, que es
 *                                justamente el caso que manda a eliminarlo desde acá.
 */
public record ResumenEquipo(
    IngresoAEliminar ingreso,
    String clienteNombre,
    String institucionNombre,
    String pacienteNombre,
    LocalDateTime fechaIngreso,
    String estado,
    int version,
    List<LineaMaterial> materiales,
    List<Bloqueo> bloqueos,
    List<Integer> ingresosLavaderoOrigen
) implements ResumenEliminacion {

    public ResumenEquipo {
        Objects.requireNonNull(ingreso, "ingreso");
        if (ingreso.modulo() == ModuloIngreso.LAVADERO) {
            throw new IllegalArgumentException("Un ingreso de Lavadero se resume con ResumenIngresoLavadero");
        }
        materiales = List.copyOf(materiales);
        bloqueos = List.copyOf(bloqueos);
        ingresosLavaderoOrigen = List.copyOf(ingresosLavaderoOrigen);
    }

    /**
     * Una fila de material tal como se va a archivar.
     *
     * @param loteIdNegocio el {@code id_negocio} de su lote, en curso o finalizado; {@code null}
     *                      si nunca entró a uno
     */
    public record LineaMaterial(String descripcion, int cantidad, String estado, String loteIdNegocio) {}
}
