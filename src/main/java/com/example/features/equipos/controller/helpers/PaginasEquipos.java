package com.example.features.equipos.controller.helpers;

import com.example.common.paginacion.Pagina;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.otros.model.EquipoOtros;

/**
 * Lo que una lectura de <b>Ver Equipos</b> devuelve: una página por cada una de sus dos grillas.
 *
 * <p>Es el tipo del grupo de refresco {@code refresco-ver-equipos}.
 * {@code RefrescadorPantallas} reparte <b>un</b> valor por lectura, y esta pantalla tiene dos
 * tablas con paginación independiente: el par viaja junto para que las dos se pinten en el mismo
 * bloque del hilo de UI, igual que hacía el snapshot que reemplaza.
 *
 * <p>Ocupa el lugar del viejo {@code HistorialEquipos}, y la diferencia es toda la del paso: aquél
 * traía las dos listas <b>completas</b>; éste trae 50 filas de cada una, ya filtradas y ordenadas
 * por la base.
 *
 * @param ortopedias página de la grilla de ortopedias
 * @param otros      página de la grilla de "otros"
 */
public record PaginasEquipos(Pagina<Equipo> ortopedias, Pagina<EquipoOtros> otros) {
}
