package com.example.features.equipos.ortopedias.dao;

/**
 * Proyección de una fila de {@code equipo_materiales}, con la descripción
 * resuelta desde {@code catalogo_descripciones}.
 *
 * <p>Reemplaza al {@code Object[]} posicional que devolvían las consultas de
 * {@link MaterialDAO}: el acceso por índice con cast no lo verifica el
 * compilador, así que reordenar el {@code SELECT} fallaba recién en runtime
 * (o peor, no fallaba y guardaba el dato equivocado en la auditoría).
 *
 * <p>No es el modelo de dominio {@link
 * com.example.features.equipos.ortopedias.model.Material}: ése es mutable,
 * expone el estado como enum {@code EstadoEquipo} y no conoce su
 * {@code equipoId}. Acá el estado viaja como el {@code String} crudo de la BD,
 * que es lo que la auditoría persiste.
 *
 * <p>Todos los campos vienen siempre poblados. {@code descripcion} es el único
 * que puede ser nulo, porque el JOIN al catálogo es un LEFT JOIN.
 */
public record FilaMaterial(
    int    id,
    int    equipoId,
    int    codigo,
    String descripcion,
    int    cantidad,
    String estado
) {}
