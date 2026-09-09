-- Identidad persistida de un Equipo* repartido entre lavarropas. Antes vivía sólo en un
-- AtomicInteger en memoria de CiclosController y se perdía al lanzar el ciclo: N fracciones
-- de un mismo equipo se contaban como N equipos distintos en cualquier suma sobre
-- elementos_ciclo_lavadero (ver plans/fracciones-de-equipo-persistidas.md, decisión A).
--
-- total_partes se fija al lanzar (decisión B): todas las fracciones de una instancia lanzan
-- juntas o no lanza ninguna sin retomar el staging, así que el número final de partes se
-- conoce de entrada y nunca cambia después de creada la fila.
CREATE TABLE instancias_equipo_ciclo (
    id                        INT AUTO_INCREMENT PRIMARY KEY,
    elemento_clasificacion_id INT NOT NULL,
    total_partes              INT NOT NULL,
    FOREIGN KEY (elemento_clasificacion_id) REFERENCES elementos_clasificacion_lavadero(id) ON DELETE RESTRICT
);

-- NULL para elementos regulares y para las filas ya existentes (no hay forma de saber cuáles
-- eran fracciones de un mismo equipo repartido; ver decisión D — sin backfill).
ALTER TABLE elementos_ciclo_lavadero ADD COLUMN instancia_equipo_id INT NULL;

ALTER TABLE elementos_ciclo_lavadero
    ADD CONSTRAINT fk_eci_instancia FOREIGN KEY (instancia_equipo_id)
    REFERENCES instancias_equipo_ciclo(id) ON DELETE RESTRICT;

CREATE INDEX idx_eci_instancia ON elementos_ciclo_lavadero (instancia_equipo_id);
