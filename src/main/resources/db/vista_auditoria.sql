-- ============================================================
-- VIEW: vista_auditoria
-- Centraliza los tres orígenes de registros de auditoría en
-- una única proyección uniforme.
--
-- Columnas proyectadas:
--   id, equipo_id, material_id, tipo_cambio, campo_modificado,
--   valor_anterior, valor_nuevo, motivo, fecha_cambio,
--   cliente_nombre, material_info
--
-- Ejecutar una sola vez en la base de datos (o incluir en el
-- proceso de inicialización de schema antes de los seeds).
-- ============================================================

CREATE OR REPLACE VIEW vista_auditoria AS

-- ── Bloque 1: modificaciones de cantidad y código ─────────────────────────
-- valor_anterior / valor_nuevo contienen el campo modificado.
-- cliente_nombre: COALESCE entre equipo activo → snapshot de eliminado.
-- material_info:  código - descripción del material modificado.
SELECT
    ea.id,
    ea.equipo_id,
    ea.material_id,
    ea.tipo_cambio,
    ea.campo_modificado,
    ea.valor_anterior,
    ea.valor_nuevo,
    ea.motivo,
    ea.fecha_cambio,
    COALESCE(c.nombre, ee.cliente_nombre, '-')            AS cliente_nombre,
    CASE
        WHEN cd.codigo IS NOT NULL
            THEN CONCAT(cd.codigo, ' - ', cd.descripcion)
        ELSE '-'
    END                                                    AS material_info
FROM equipos_auditoria ea
LEFT JOIN equipos               e  ON ea.equipo_id         = e.id
LEFT JOIN clientes              c  ON e.nro_cliente        = c.id
LEFT JOIN equipos_eliminados    ee ON ea.equipo_id         = ee.equipo_id_original
LEFT JOIN equipo_materiales     em ON ea.material_id       = em.id
LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo  = cd.codigo
WHERE ea.tipo_cambio NOT IN ('ELIMINACION_EQUIPO', 'ELIMINACION_MATERIAL')

UNION ALL

-- ── Bloque 2: equipos eliminados ──────────────────────────────────────────
-- cliente_nombre: del snapshot en equipos_eliminados.
-- material_info:  lista de materiales del equipo al momento de su eliminación,
--                 tomada del snapshot en materiales_eliminados.
--                 Formato por ítem: "código - descripción - cantidad uds."
--                 Ventana de corte: 3 segundos (mismo evento de eliminación).
-- valor_anterior / valor_nuevo: NULL — la info relevante está en las columnas
--                 cliente_nombre y material_info.
SELECT
    ee.id,
    ee.equipo_id_original                                  AS equipo_id,
    NULL                                                   AS material_id,
    'ELIMINACION_EQUIPO'                                   AS tipo_cambio,
    'equipo'                                               AS campo_modificado,
    NULL                                                   AS valor_anterior,
    NULL                                                   AS valor_nuevo,
    ee.motivo,
    ee.fecha_eliminacion                                   AS fecha_cambio,
    COALESCE(ee.cliente_nombre, '-')                       AS cliente_nombre,
    COALESCE(
        (
            SELECT GROUP_CONCAT(
                CONCAT(
                    m.codigo_catalogo, ' - ',
                    COALESCE(m.descripcion, '-'), ' - ',
                    COALESCE(m.cantidad, 0)
                )
                ORDER BY m.codigo_catalogo
                SEPARATOR ', '
            )
            FROM materiales_eliminados m
            WHERE m.equipo_id_original = ee.equipo_id_original
              AND ABS(TIMESTAMPDIFF(SECOND, ee.fecha_eliminacion, m.fecha_eliminacion)) <= 3
        ),
        '-'
    )                                                      AS material_info
FROM equipos_eliminados ee

UNION ALL

-- ── Bloque 3: materiales eliminados de forma individual ───────────────────
-- Excluye materiales eliminados junto con su equipo (ventana de 3 segundos)
-- para evitar duplicados: esos ya aparecen consolidados en el bloque 2.
-- cliente_nombre: COALESCE entre equipo activo → snapshot de equipos_eliminados.
--                 Esto cubre el caso en que el material fue eliminado primero
--                 de forma individual y luego el equipo fue eliminado después.
-- material_info:  código - descripción - cantidad del material (del snapshot).
-- valor_anterior / valor_nuevo: NULL.
SELECT
    m.id,
    m.equipo_id_original                                   AS equipo_id,
    m.material_id_original                                 AS material_id,
    'ELIMINACION_MATERIAL'                                 AS tipo_cambio,
    'material'                                             AS campo_modificado,
    NULL                                                   AS valor_anterior,
    NULL                                                   AS valor_nuevo,
    m.motivo,
    m.fecha_eliminacion                                    AS fecha_cambio,
    COALESCE(c.nombre, ee.cliente_nombre, '-')             AS cliente_nombre,
    CONCAT(
        m.codigo_catalogo, ' - ',
        COALESCE(m.descripcion, '-'), ' - ',
        COALESCE(m.cantidad, 0)
    )                                                      AS material_info
FROM materiales_eliminados m
LEFT JOIN equipos           e  ON m.equipo_id_original = e.id
LEFT JOIN clientes          c  ON e.nro_cliente        = c.id
LEFT JOIN equipos_eliminados ee ON m.equipo_id_original = ee.equipo_id_original
WHERE NOT EXISTS (
    SELECT 1
    FROM equipos_eliminados ee2
    WHERE ee2.equipo_id_original = m.equipo_id_original
      AND ABS(TIMESTAMPDIFF(SECOND, ee2.fecha_eliminacion, m.fecha_eliminacion)) <= 3
);