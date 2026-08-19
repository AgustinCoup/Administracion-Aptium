-- El cliente bajo el que se derivan al CDE los materiales que no conservan su cliente original.
-- En producción ya existe; esto es para las BD de desarrollo y para los tests.
-- clientes.nombre es UNIQUE desde V4, así que INSERT IGNORE es idempotente.
INSERT IGNORE INTO clientes (nombre) VALUES ('APTIUM');
