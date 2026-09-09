package com.example.features.lavadero.dao.helpers;

import com.example.features.lavadero.model.DestinoSalida;

import java.time.LocalDateTime;

/**
 * Una fila cruda del detalle del historial: una fracción de un {@code Equipo*} repartido, antes
 * de agrupar ({@link AgrupadorLineasHistorial}). Espejo de {@link FilaInstanciaEquipo} para el
 * lado de sólo lectura — no es una unidad de negocio, sólo transporta el resultado de la
 * consulta hasta el agrupador.
 */
public record FilaHistorialCruda(
        int instanciaEquipoId,
        int totalPartes,
        String elementoNombre,
        int lavarropasNumero,
        LocalDateTime fechaFinCiclo,
        LocalDateTime fechaListo,
        DestinoSalida destino) { }
