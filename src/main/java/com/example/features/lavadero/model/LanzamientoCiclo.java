package com.example.features.lavadero.model;

import java.util.List;

/**
 * Lo que un lavarropas lleva a su ciclo dentro de una tanda de lanzamiento: su configuración
 * y las líneas de staging que se le asignaron.
 *
 * <p>Una tanda es una {@code List<LanzamientoCiclo>} y se escribe entera o no se escribe
 * (ver {@code CicloLavaderoDAO.lanzarTanda}): las fracciones de un equipo repartido viven en
 * varios lavarropas, así que ninguno de ellos es una unidad de trabajo válida por sí solo.
 */
public record LanzamientoCiclo(int lavarropasNumero, ConfiguracionCiclo config,
                               List<LineaLanzamiento> lineas) { }
