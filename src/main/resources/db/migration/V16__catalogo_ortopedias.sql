-- Catálogo de ortopedias: listado oficial de códigos 400-430.
--
-- Los códigos que ya existían conservan su volumen actual (solo se corrige la
-- descripción); los nuevos entran con volumen 10, salvo el 421 (servicio, sin
-- volumen físico) que entra en 0.
--
-- Los códigos compuestos (406, 409, 418, 420) se desdoblan en códigos nuevos:
--   406 'Makita - Perforador - Taladro'        -> 431 MAKITA, 423 PERFORADOR, 424 TALADRO
--   409 'Guias Metálicas - Instrumental peq.'  -> 432 INSTRUMENTAL PEQUEÑO, 428 GUIAS METÁLICAS
--   418 'clavos - placas'                      -> 433 CLAVOS, 429 PLACAS
--   420 'Tornillo - elemento pequeño'          -> 434 TORNILLO, 430 ELEMENTO PEQUEÑO
--
-- Los cuatro códigos viejos NO se reescriben al significado angosto: un material
-- histórico cargado con 409 pudo ser una guía metálica o instrumental pequeño, y
-- como la descripción se resuelve por JOIN (no hay snapshot en equipo_materiales),
-- reescribir 409 a 'INSTRUMENTAL PEQUEÑO' re-etiquetaría todo el historial con una
-- afirmación que puede ser falsa. Se retiran con (LEGACY) + vigente = FALSE,
-- conservando su texto original: el historial sigue leyéndose tal como se cargó y
-- los códigos nuevos son los únicos asignables de acá en adelante.
--
-- Por eso MAKITA / INSTRUMENTAL PEQUEÑO / CLAVOS / TORNILLO quedan en 431-434 y no
-- en los 406/409/418/420 del listado oficial: es el precio de no corromper historia.
--
-- El 414 ('atornilladores/ dremmel') no forma parte del listado oficial y recibe el
-- mismo tratamiento. Ninguno se puede borrar: equipo_materiales los referencia con
-- ON DELETE RESTRICT.

ALTER TABLE catalogo_descripciones
    ADD COLUMN vigente BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO catalogo_descripciones (codigo, descripcion, volumen) VALUES
(400, 'TORNILLERA', 15),
(401, 'CAJA DE CIRUGÍA', 0),
(402, 'CAJA DE CIRUGÍA TAMAÑO "M"', 25),
(403, 'CAJA DE CIRUGÍA TAMAÑO "L"', 30),
(404, 'CAJA DE CIRUGÍA TAMAÑO "XL"', 40),
(405, 'CAJA DE CIRUGÍA TAMAÑO "XXL"', 50),
(407, 'SIERRA BTR O SIMIL, CON HOJAS + ACCESORIOS', 15),
(408, 'MICROMOTORES', 10),
(410, 'INSTRUMENTAL GRANDE', 20),
(411, 'PRÓTESIS', 10),
(412, 'BATERÍAS EXTRAS', 10),
(413, 'IMPLANTES', 10),
(415, 'CLAVIJAS', 10),
(416, 'ALAMBRE', 10),
(417, 'SUPER XXL', 60),
(419, 'MAKITA APTIUM', 10),
(421, 'SERVICIO DE RETIRO Y ENTREGA MATERIAL', 0),
(422, 'MESITA', 10),
(423, 'PERFORADOR', 10),
(424, 'TALADRO', 10),
(425, 'DREMMEL', 10),
(426, 'EQUIPO DE CORTE', 10),
(427, 'ATORNILLADORES', 10),
(428, 'GUIAS METÁLICAS', 10),
(429, 'PLACAS', 10),
(430, 'ELEMENTO PEQUEÑO', 10),
(431, 'MAKITA', 10),
(432, 'INSTRUMENTAL PEQUEÑO', 10),
(433, 'CLAVOS', 10),
(434, 'TORNILLO', 5)
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- Códigos retirados: conservan su texto original para no re-etiquetar el historial.
-- vigente = FALSE es lo que impide asignarlos en equipos nuevos.
UPDATE catalogo_descripciones
SET descripcion = CONCAT('(LEGACY) ', descripcion),
    vigente     = FALSE
WHERE codigo IN (406, 409, 418, 420);

UPDATE catalogo_descripciones
SET descripcion = '(LEGACY) ATORNILLADORES / DREMMEL',
    vigente     = FALSE
WHERE codigo = 414;
