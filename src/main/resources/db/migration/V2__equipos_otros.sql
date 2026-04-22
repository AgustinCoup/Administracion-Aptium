-- ============================================================
-- V2: Soporte para equipos "Otros" (REMITO y DETALLES)
-- ============================================================

-- ── Nuevas tablas ────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS catalogo_otros (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    descripcion VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS equipo_otros (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    nro_cliente      INT NOT NULL,
    estado           VARCHAR(50) DEFAULT 'Nuevo',
    requiere_lavado  TINYINT(1)  NOT NULL DEFAULT 1,
    requiere_empaque TINYINT(1)  NOT NULL DEFAULT 1,
    tipo_ingreso     VARCHAR(10) NOT NULL DEFAULT 'DETALLES',
    remito_id        VARCHAR(30) NULL,
    remito_cantidad  INT         NULL,
    remito_observaciones TEXT    NULL,
    volumen_equipo   INT         NOT NULL DEFAULT 0,
    fecha_ingreso    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otros_remito_id (remito_id),
    FOREIGN KEY (nro_cliente) REFERENCES clientes(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS equipo_otros_materiales (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    equipo_otros_id  INT          NOT NULL,
    catalogo_otros_id INT         NOT NULL,
    descripcion      VARCHAR(255) NOT NULL,
    cantidad         INT          NOT NULL DEFAULT 1,
    estado           VARCHAR(50)  DEFAULT 'Nuevo',
    lote_id          INT,
    volumen_lote     INT          NULL,
    INDEX idx_otros_mat_equipo  (equipo_otros_id),
    INDEX idx_otros_mat_estado  (equipo_otros_id, estado),
    INDEX idx_otros_mat_lote    (lote_id),
    FOREIGN KEY (equipo_otros_id)   REFERENCES equipo_otros(id)     ON DELETE CASCADE,
    FOREIGN KEY (catalogo_otros_id) REFERENCES catalogo_otros(id)   ON DELETE RESTRICT,
    FOREIGN KEY (lote_id)           REFERENCES lotes(id)            ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS otros_material_movimientos (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    material_id     INT NOT NULL,
    equipo_otros_id INT NOT NULL,
    cantidad        INT NOT NULL,
    estado_origen   VARCHAR(50),
    estado_destino  VARCHAR(50) NOT NULL,
    fecha           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otros_mov_material (material_id),
    INDEX idx_otros_mov_equipo   (equipo_otros_id),
    FOREIGN KEY (material_id)     REFERENCES equipo_otros_materiales(id) ON DELETE CASCADE,
    FOREIGN KEY (equipo_otros_id) REFERENCES equipo_otros(id)            ON DELETE CASCADE
);

-- ── Columnas nuevas en tablas existentes ─────────────────────

ALTER TABLE equipos_auditoria
    ADD COLUMN tipo_equipo VARCHAR(10) DEFAULT 'ORTOPEDIA';

ALTER TABLE equipos_eliminados
    ADD COLUMN tipo_equipo VARCHAR(10) DEFAULT 'ORTOPEDIA';

ALTER TABLE materiales_eliminados
    ADD COLUMN tipo_equipo VARCHAR(10) DEFAULT 'ORTOPEDIA';

-- ── Cambios en columnas existentes ───────────────────────────

ALTER TABLE materiales_eliminados
    MODIFY COLUMN codigo_catalogo INT NULL;

-- ── Vista de auditoría unificada ─────────────────────────────

CREATE OR REPLACE VIEW vista_auditoria AS

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
    COALESCE(c.nombre, co.nombre, ee.cliente_nombre, '-')  AS cliente_nombre,
    CASE
        WHEN ea.tipo_equipo = 'OTROS' AND eom.id IS NOT NULL
            THEN COALESCE(eom.descripcion, '-')
        WHEN cd.codigo IS NOT NULL
            THEN CONCAT(cd.codigo, ' - ', cd.descripcion)
        ELSE '-'
    END                                                    AS material_info,
    COALESCE(ea.tipo_equipo, 'ORTOPEDIA')                  AS tipo_equipo
FROM equipos_auditoria ea
LEFT JOIN equipos               e   ON ea.equipo_id        = e.id
                                    AND (ea.tipo_equipo IS NULL OR ea.tipo_equipo = 'ORTOPEDIA')
LEFT JOIN clientes              c   ON e.nro_cliente       = c.id
LEFT JOIN equipo_otros          eo  ON ea.equipo_id        = eo.id
                                    AND ea.tipo_equipo     = 'OTROS'
LEFT JOIN clientes              co  ON eo.nro_cliente      = co.id
LEFT JOIN equipos_eliminados    ee  ON ea.equipo_id        = ee.equipo_id_original
LEFT JOIN equipo_materiales     em  ON ea.material_id      = em.id
                                    AND (ea.tipo_equipo IS NULL OR ea.tipo_equipo = 'ORTOPEDIA')
LEFT JOIN catalogo_descripciones cd ON em.codigo_catalogo  = cd.codigo
LEFT JOIN equipo_otros_materiales eom ON ea.material_id    = eom.id
                                       AND ea.tipo_equipo  = 'OTROS'
WHERE ea.tipo_cambio NOT IN ('ELIMINACION_EQUIPO', 'ELIMINACION_MATERIAL')

UNION ALL

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
                CASE
                    WHEN m.tipo_equipo = 'OTROS'
                        THEN CONCAT(COALESCE(m.descripcion, '-'), ' - ', COALESCE(m.cantidad, 0))
                    ELSE CONCAT(COALESCE(m.codigo_catalogo, ''), ' - ', COALESCE(m.descripcion, '-'), ' - ', COALESCE(m.cantidad, 0))
                END
                ORDER BY m.descripcion
                SEPARATOR ', '
            )
            FROM materiales_eliminados m
            WHERE m.equipo_id_original = ee.equipo_id_original
              AND ABS(TIMESTAMPDIFF(SECOND, ee.fecha_eliminacion, m.fecha_eliminacion)) <= 3
        ),
        '-'
    )                                                      AS material_info,
    COALESCE(ee.tipo_equipo, 'ORTOPEDIA')                  AS tipo_equipo
FROM equipos_eliminados ee

UNION ALL

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
    COALESCE(c.nombre, co.nombre, ee.cliente_nombre, '-')  AS cliente_nombre,
    CASE
        WHEN m.tipo_equipo = 'OTROS'
            THEN CONCAT(COALESCE(m.descripcion, '-'), ' - ', COALESCE(m.cantidad, 0))
        ELSE CONCAT(COALESCE(m.codigo_catalogo, ''), ' - ', COALESCE(m.descripcion, '-'), ' - ', COALESCE(m.cantidad, 0))
    END                                                    AS material_info,
    COALESCE(m.tipo_equipo, 'ORTOPEDIA')                   AS tipo_equipo
FROM materiales_eliminados m
LEFT JOIN equipos           e   ON m.equipo_id_original = e.id
                                AND (m.tipo_equipo IS NULL OR m.tipo_equipo = 'ORTOPEDIA')
LEFT JOIN clientes          c   ON e.nro_cliente        = c.id
LEFT JOIN equipo_otros      eo  ON m.equipo_id_original = eo.id
                                AND m.tipo_equipo       = 'OTROS'
LEFT JOIN clientes          co  ON eo.nro_cliente       = co.id
LEFT JOIN equipos_eliminados ee ON m.equipo_id_original = ee.equipo_id_original
WHERE NOT EXISTS (
    SELECT 1
    FROM equipos_eliminados ee2
    WHERE ee2.equipo_id_original = m.equipo_id_original
      AND ABS(TIMESTAMPDIFF(SECOND, ee2.fecha_eliminacion, m.fecha_eliminacion)) <= 3
);
