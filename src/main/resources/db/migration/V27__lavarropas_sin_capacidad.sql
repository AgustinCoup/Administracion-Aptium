-- `lavarropas.capacidad_litros` se borra: nadie la mostraba.
--
-- El único lector era LavarropasTableModel, que no tiene un solo llamador en src/main ni en
-- src/test — o sea que la columna "Capacidad (L)" que definía no se dibuja en ninguna pantalla.
-- El resto de la cadena (LavarropasDAO, el modelo Lavarropas, LavarropasItem y
-- ConstructorVistaCiclos.mapearLavarropas) la arrastraba de la base a la vista sin que nadie la
-- leyera del otro lado.
--
-- POR QUÉ ESTE NÚMERO Y NO LA V26: la V26 sólo AGREGA, así que un JAR anterior sigue corriendo
-- contra una base migrada. Ésta saca una columna, y por eso va junto con el código que deja de
-- leerla, en el mismo commit. Dropearla un paso antes deja la suite en rojo sin que haya nada que
-- arreglar en ese paso — y peor, el fallo es silencioso: `LavarropasDAO.obtenerTodos()` se comía
-- el SQLException y devolvía lista vacía (eso también se arregla en este mismo paso).
--
-- RECUPERACIÓN si queda en estado `failed`: flyway repair y reintentar. La columna no se puede
-- restaurar con sus valores —eran todos 13, el seed de la V10—, pero ningún código la lee.

ALTER TABLE lavarropas DROP COLUMN capacidad_litros;
