package com.example.common.eliminacion;

/**
 * A qué módulo pertenece un ingreso archivado en {@code ingresos_eliminados}.
 *
 * <p><b>Se persiste con {@link #name()}</b> en la columna {@code modulo}. Renombrar un valor rompe
 * el archivo: las filas viejas quedan con un nombre que ya no existe y {@code valueOf} deja de
 * poder leerlas. Agregar valores es seguro; renombrar o borrar, no.</p>
 */
public enum ModuloIngreso {
    /** Una fila de {@code equipos}. */
    ORTOPEDIA,
    /** Una fila de {@code equipo_otros}, cargada a mano o derivada desde Lavadero. */
    OTROS,
    /** Una fila de {@code ingresos_lavadero}. */
    LAVADERO
}
