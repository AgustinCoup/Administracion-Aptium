-- Deduplica clientes con nombre repetido (conserva MIN(id)) y agrega UNIQUE(nombre).
--
-- Por qué: la tabla clientes carecía de UNIQUE en nombre, a diferencia de profesionales
-- e instituciones. GestorNuevasEntidades podía insertar el mismo nombre más de una vez
-- sin recibir error de la DB.
--
-- Orden:
--   1. Redirigir FKs en equipos y equipo_otros al id canónico (el menor por nombre).
--   2. Eliminar las filas duplicadas ya sin referencias.
--   3. Agregar la restricción UNIQUE para prevenir futuros duplicados.

-- 1. Reasignar FKs en equipos
UPDATE equipos e
JOIN clientes c ON e.nro_cliente = c.id
JOIN (SELECT nombre, MIN(id) AS min_id FROM clientes GROUP BY nombre) d
    ON c.nombre = d.nombre
SET e.nro_cliente = d.min_id
WHERE e.nro_cliente != d.min_id;

-- 2. Reasignar FKs en equipo_otros
UPDATE equipo_otros eo
JOIN clientes c ON eo.nro_cliente = c.id
JOIN (SELECT nombre, MIN(id) AS min_id FROM clientes GROUP BY nombre) d
    ON c.nombre = d.nombre
SET eo.nro_cliente = d.min_id
WHERE eo.nro_cliente != d.min_id;

-- 3. Eliminar filas duplicadas (las que no son el MIN id para su nombre)
DELETE c FROM clientes c
JOIN (SELECT nombre, MIN(id) AS min_id FROM clientes GROUP BY nombre) d
    ON c.nombre = d.nombre
WHERE c.id != d.min_id;

-- 4. Prevenir futuros duplicados
ALTER TABLE clientes ADD UNIQUE (nombre);
