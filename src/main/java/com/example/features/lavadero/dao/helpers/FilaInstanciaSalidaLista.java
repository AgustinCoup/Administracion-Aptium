package com.example.features.lavadero.dao.helpers;

import java.time.LocalDateTime;

/**
 * Una fila cruda de una salida de instancia sin destino, una por fracción (lavarropas) de la
 * instancia, antes de agrupar ({@link AgrupadorInstanciasSalida}). Simétrica de
 * {@link FilaInstanciaEquipo} para el lado "ya lista" en vez de "pendiente de listo".
 */
public record FilaInstanciaSalidaLista(
        int salidaId,
        int instanciaEquipoId,
        int cantidad,
        LocalDateTime fechaListo,
        int lavarropasNumero,
        LocalDateTime fechaFinCiclo,
        int ingresoId,
        int clienteId,
        String clienteNombre,
        String elementoNombre) { }
