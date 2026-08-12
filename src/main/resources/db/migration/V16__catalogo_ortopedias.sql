-- Catálogo de ortopedias: listado oficial de códigos 400-430.
--
-- Los códigos que ya existían conservan su volumen actual (solo se corrige la
-- descripción); los nuevos entran con volumen 10, salvo el 421 (servicio, sin
-- volumen físico) que entra en 0.
--
-- Los códigos compuestos se desdoblan: 406 -> 406/423/424, 409 -> 409/428,
-- 418 -> 418/429, 420 -> 420/430. El 414 ('atornilladores/ dremmel') no forma
-- parte del listado oficial pero no se puede borrar: equipo_materiales lo
-- referencia con ON DELETE RESTRICT. Se marca como no vigente, que es lo que
-- impide cargarlo en equipos nuevos; los materiales históricos siguen
-- resolviendo su descripción por JOIN y quedan intactos.

ALTER TABLE catalogo_descripciones
    ADD COLUMN vigente BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO catalogo_descripciones (codigo, descripcion, volumen) VALUES
(400, 'TORNILLERA', 15),
(401, 'CAJA DE CIRUGÍA', 0),
(402, 'CAJA DE CIRUGÍA TAMAÑO "M"', 25),
(403, 'CAJA DE CIRUGÍA TAMAÑO "L"', 30),
(404, 'CAJA DE CIRUGÍA TAMAÑO "XL"', 40),
(405, 'CAJA DE CIRUGÍA TAMAÑO "XXL"', 50),
(406, 'MAKITA', 10),
(407, 'SIERRA BTR O SIMIL, CON HOJAS + ACCESORIOS', 15),
(408, 'MICROMOTORES', 10),
(409, 'INSTRUMENTAL PEQUEÑO', 10),
(410, 'INSTRUMENTAL GRANDE', 20),
(411, 'PRÓTESIS', 10),
(412, 'BATERÍAS EXTRAS', 10),
(413, 'IMPLANTES', 10),
(415, 'CLAVIJAS', 10),
(416, 'ALAMBRE', 10),
(417, 'SUPER XXL', 60),
(418, 'CLAVOS', 10),
(419, 'MAKITA APTIUM', 10),
(420, 'TORNILLO', 5),
(421, 'SERVICIO DE RETIRO Y ENTREGA MATERIAL', 0),
(422, 'MESITA', 10),
(423, 'PERFORADOR', 10),
(424, 'TALADRO', 10),
(425, 'DREMMEL', 10),
(426, 'EQUIPO DE CORTE', 10),
(427, 'ATORNILLADORES', 10),
(428, 'GUIAS METÁLICAS', 10),
(429, 'PLACAS', 10),
(430, 'ELEMENTO PEQUEÑO', 10)
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

UPDATE catalogo_descripciones
SET descripcion = '(LEGACY) ATORNILLADORES / DREMMEL',
    vigente     = FALSE
WHERE codigo = 414;
