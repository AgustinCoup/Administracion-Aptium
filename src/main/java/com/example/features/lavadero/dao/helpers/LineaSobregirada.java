package com.example.features.lavadero.dao.helpers;

/**
 * Una línea de {@code elementos_clasificacion_lavadero} cuyo total ya procesado supera su
 * cantidad clasificada, bajo la fórmula de la decisión C del blueprint de fracciones de equipo
 * (regulares sumados + instancias contadas una sola vez).
 *
 * <p>Sólo puede aparecer con datos escritos antes de {@code V19}, cuando las N fracciones de un
 * equipo repartido se contaban como N unidades (ver
 * {@code plans/fracciones-de-equipo-persistidas.md}, decisión D). Es un síntoma de base de
 * desarrollo sucia: {@link com.example.features.lavadero.dao.CicloLavaderoDAO#detectarLineasSobregiradas()}
 * lo delata, sin repararlo — la limpieza es un paso operativo manual.</p>
 */
public record LineaSobregirada(
        int elementoClasificacionId,
        int ingresoId,
        String elemento,
        int cantidad,
        int yaProcesada) { }
