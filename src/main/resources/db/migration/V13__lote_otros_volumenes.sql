-- Volúmenes de agua por ingreso (equipo_otros) por lote.
-- Reemplaza a equipo_otros_materiales.volumen_lote (litros por fila de material):
-- el dato de negocio siempre fue "litros por ingreso en cada lote".
-- Numerada V13 porque la rama Lavadero ocupa V7-V12 (ver plans/refactor-volumenes-por-ingreso.md).

CREATE TABLE lote_otros_volumenes (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    lote_id         INT NOT NULL,
    equipo_otros_id INT NOT NULL,
    volumen         INT NOT NULL,
    CONSTRAINT uq_lote_ingreso UNIQUE (lote_id, equipo_otros_id),
    FOREIGN KEY (lote_id)         REFERENCES lotes(id)        ON DELETE CASCADE,
    FOREIGN KEY (equipo_otros_id) REFERENCES equipo_otros(id) ON DELETE CASCADE
);

-- Backfill: agrega los litros históricos por (lote, ingreso).
INSERT INTO lote_otros_volumenes (lote_id, equipo_otros_id, volumen)
SELECT lote_id, equipo_otros_id, SUM(volumen_lote)
FROM equipo_otros_materiales
WHERE lote_id IS NOT NULL AND volumen_lote IS NOT NULL
GROUP BY lote_id, equipo_otros_id;

ALTER TABLE equipo_otros_materiales DROP COLUMN volumen_lote;
