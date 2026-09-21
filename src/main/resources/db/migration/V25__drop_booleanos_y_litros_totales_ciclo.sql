-- Fuera de ciclos_lavadero: suavizante, potenciador y litros_totales.
--
-- suavizante y potenciador ya viven como filas de insumos_ciclo_lavadero: la V24 los copió, y
-- Flyway aplica en orden, así que esta migración no puede correr sin que la copia haya corrido
-- antes. Se borran recién acá, y no en la V24, porque el DAO que dejó de leerlas se escribió
-- junto con esta migración: con los DROP en la V24 el código de entonces se rompía.
--
-- litros_totales se borra sin reemplazo: era un dato que nadie consultaba y que sin embargo
-- había que cargar para poder lanzar un ciclo.
--
-- RECUPERACIÓN si esta migración queda en estado `failed` DESPUÉS de alguno de los DROP:
-- restaurar del backup. MySQL hace commit implícito en cada DDL, así que un DROP ya aplicado no
-- se revierte, y los booleanos borrados no se pueden recalcular desde ningún otro lado (la copia
-- de la V24 sí sobrevive, pero litros_totales no tiene copia). Si falló en el primer DROP, alcanza
-- con `flyway repair` y reintentar.
--
-- Sentencias separadas y sin AFTER, para que corra igual en H2 (tests) y MySQL (producción).

ALTER TABLE ciclos_lavadero DROP COLUMN suavizante;
ALTER TABLE ciclos_lavadero DROP COLUMN potenciador;
ALTER TABLE ciclos_lavadero DROP COLUMN litros_totales;
