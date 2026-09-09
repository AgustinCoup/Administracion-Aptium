-- Un equipo repartido en N lavarropas es UNA salida cuando las N partes terminan, no N
-- salidas (ver plans/fracciones-de-equipo-persistidas.md, decisiones B/C). elemento_ciclo_id
-- pasa a ser opcional porque una salida de instancia no corresponde a una sola fila de
-- elementos_ciclo_lavadero: corresponde a la instancia entera.
--
-- Exactamente una de las dos columnas está poblada por fila; se valida en SalidaLavaderoDAO,
-- no acá — no hay CHECK en ninguna migración de este repo, el patrón es validar en código.
--
-- ALTER separados, sin AFTER. Precedentes: V2 (MODIFY COLUMN ... NULL), V12/V19 (ADD
-- CONSTRAINT como sentencia separada).
ALTER TABLE salidas_lavadero MODIFY COLUMN elemento_ciclo_id INT NULL;
ALTER TABLE salidas_lavadero ADD COLUMN instancia_equipo_id INT NULL;
ALTER TABLE salidas_lavadero
    ADD CONSTRAINT fk_salidas_instancia FOREIGN KEY (instancia_equipo_id)
    REFERENCES instancias_equipo_ciclo(id) ON DELETE RESTRICT;
CREATE INDEX idx_salidas_instancia ON salidas_lavadero (instancia_equipo_id);
