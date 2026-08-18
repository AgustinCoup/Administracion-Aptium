package com.example.features.lavadero.controller.helpers;

import com.example.features.equipos.otros.model.EquipoOtros;

import java.util.List;
import java.util.Map;

/**
 * Los ingresos armados y de qué salidas salió cada uno.
 *
 * <p>Van juntos a propósito: se calculan en una sola pasada y no pueden desincronizarse.
 * El mapa se indexa por identidad de instancia — {@link EquipoOtros} no redefine
 * {@code equals}, y dos ingresos recién armados pueden ser indistinguibles por valor.</p>
 *
 * @param ingresos  un {@link EquipoOtros} por cliente asignado
 * @param salidaIds ingreso → ids de las salidas de lavadero que lo componen
 */
public record IngresosCDE(List<EquipoOtros> ingresos, Map<EquipoOtros, List<Integer>> salidaIds) { }
