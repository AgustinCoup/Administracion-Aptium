package com.example.common.eliminacion;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Una fila de {@code ingresos_eliminados}: la copia de un ingreso que se va a borrar.
 *
 * <p>Las columnas sueltas son para buscar; el ingreso completo va en {@code snapshot}, un JSON ya
 * armado por el eliminador de cada módulo con {@link SnapshotJson}. No lleva nada sensible, así
 * que el {@code toString} del record es seguro.</p>
 *
 * @param modulo            a qué tabla pertenecía el ingreso
 * @param ingresoIdOriginal el id que tenía en esa tabla
 * @param clienteNombre     el nombre como texto, no el id: el archivo no tiene FK a {@code clientes}
 * @param fechaIngreso      nullable
 * @param estado            el estado en el que estaba al borrarse; nullable
 * @param motivo            obligatorio, sin espacios en los bordes, hasta
 *                          {@link Constantes.Eliminacion#MOTIVO_MAX_LARGO} caracteres
 * @param puesto            desde dónde se borró ({@link PuestoDeTrabajo#actual()}); nullable
 * @param archivoPadreId    la fila {@code LAVADERO} que arrastró a este {@code OTROS}; nullable
 * @param snapshot          el árbol completo del ingreso en JSON
 */
public record IngresoArchivado(
    ModuloIngreso modulo,
    int ingresoIdOriginal,
    String clienteNombre,
    LocalDateTime fechaIngreso,
    String estado,
    String motivo,
    String puesto,
    Integer archivoPadreId,
    String snapshot
) {

    public IngresoArchivado {
        Objects.requireNonNull(modulo, "modulo");
        if (ingresoIdOriginal <= 0) {
            throw new IllegalArgumentException("ingresoIdOriginal debe ser positivo: " + ingresoIdOriginal);
        }
        if (snapshot == null || snapshot.isBlank()) {
            throw new IllegalArgumentException("Un ingreso no se archiva sin su snapshot");
        }
        motivo = motivo == null ? "" : motivo.strip();
        ValidationException.builder()
            .addErrorIf(motivo.isEmpty(), Constantes.Mensajes.MOTIVO_ELIMINACION_OBLIGATORIO)
            .addErrorIf(motivo.length() > Constantes.Eliminacion.MOTIVO_MAX_LARGO,
                String.format(Constantes.Mensajes.MOTIVO_ELIMINACION_LARGO,
                    Constantes.Eliminacion.MOTIVO_MAX_LARGO))
            .throwIfHasErrors();
    }
}
