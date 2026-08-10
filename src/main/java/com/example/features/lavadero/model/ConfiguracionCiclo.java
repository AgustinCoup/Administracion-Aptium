package com.example.features.lavadero.model;

import java.math.BigDecimal;

/**
 * Configuración con la que se lanza un ciclo de lavado.
 *
 * <p>Agrupa los parámetros que antes viajaban sueltos y posicionales por service, DAO y
 * controller. No valida nada: la validación vive en {@code CicloLavaderoService}.</p>
 *
 * @param jabon         jabón del catálogo; obligatorio (lo exige el service)
 * @param litrosJabon   mililitros de jabón; obligatorio y mayor a cero (lo exige el service)
 * @param suavizante    si el ciclo lleva suavizante
 * @param potenciador   si el ciclo lleva potenciador
 * @param litrosTotales litros totales del ciclo; <b>nullable</b>, la columna admite NULL
 */
public record ConfiguracionCiclo(JabonCatalogo jabon, BigDecimal litrosJabon,
                                 boolean suavizante, boolean potenciador,
                                 BigDecimal litrosTotales) {
}
