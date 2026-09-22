package com.example.features.catalogo.model;

/**
 * Fila del catálogo de descripciones de ortopedias ({@code catalogo_descripciones}), tal como la
 * necesita Ajustes: código, descripción y si está vigente. No reemplaza a
 * {@code obtenerDescripcionVigente}/{@code obtenerVolumen}, que son consultas de un solo campo
 * usadas por los flujos de carga e históricos respectivamente.
 *
 * @param codigo      código del material (PK)
 * @param descripcion descripción, tal como está en la base (incluye el prefijo
 *                    {@code "(LEGACY) "} de los códigos retirados en la V16)
 * @param vigente     baja lógica; {@code false} para un código dado de baja
 */
public record ItemCatalogo(int codigo, String descripcion, boolean vigente) {
}
