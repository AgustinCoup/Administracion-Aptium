package com.example.features.lavadero.dao.helpers;

import java.time.LocalDateTime;

/**
 * Una fila cruda de {@code elementos_ciclo_lavadero}, una por fracción de una instancia de
 * equipo repartido, antes de agrupar ({@link AgrupadorInstanciasSalida}). No es una unidad de
 * negocio por sí sola — sólo existe para transportar el resultado de la consulta hasta el
 * agrupador.
 */
public record FilaInstanciaEquipo(
        int instanciaEquipoId,
        int elementoClasificacionId,
        int totalPartes,
        int elementoCicloId,
        int lavarropasNumero,
        LocalDateTime fechaFinCiclo,
        int ingresoId,
        int clienteId,
        String clienteNombre,
        String elementoNombre,
        int cantidadYaMarcada) { }
