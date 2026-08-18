package com.example.features.lavadero.model;

import java.time.LocalDateTime;

/**
 * Una salida ya marcada como Listo y todavía sin destino asignado
 * ({@code salidas_lavadero.destino IS NULL}).
 *
 * <p>Conserva el contexto de dónde vino (cliente, lavarropas, fecha de fin de ciclo) aunque
 * ya esté lista: al decidir el destino el operador necesita distinguir dos tandas del mismo
 * elemento tanto como al doblarlas.</p>
 */
public record SalidaLista(
        int salidaId,
        int cicloId,
        int lavarropasNumero,
        int ingresoId,
        int clienteId,
        String clienteNombre,
        String elementoNombre,
        int cantidad,
        LocalDateTime fechaFinCiclo,
        LocalDateTime fechaListo) { }
