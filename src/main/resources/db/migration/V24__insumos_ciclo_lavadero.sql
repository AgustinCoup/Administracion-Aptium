-- Insumos extra de un ciclo de lavado: de dos booleanos fijos a un catálogo.
--
-- Suavizante y potenciador eran una columna BOOLEAN cada uno (V10, V12). La lista de insumos
-- crece con el uso, y con un booleano por insumo cada insumo nuevo cuesta una migración y un
-- checkbox más en la card. Pasan a ser filas: un catálogo (catalogo_insumos) y una tabla puente
-- que dice qué insumos llevó cada ciclo (insumos_ciclo_lavadero).
--
-- Esta migración sólo AGREGA y copia. Los DROP de suavizante, potenciador y litros_totales van
-- en la V25, que se escribe junto con el DAO que deja de usarlos: si estuvieran acá, el código
-- que todavía lee esas columnas se rompería entre una migración y la otra. En producción las dos
-- corren en la misma pasada de Flyway, sin escrituras de la app en el medio, así que la copia de
-- abajo ve todos los booleanos que la V25 después borra. Flyway aplica en orden: la V25 no puede
-- correr sin que esta haya corrido antes.
--
-- RECUPERACIÓN si esta migración queda en estado `failed`:
--   1. flyway repair            (borra la fila fallida de flyway_schema_history)
--   2. DROP TABLE IF EXISTS insumos_ciclo_lavadero;
--      DROP TABLE IF EXISTS catalogo_insumos;
--   3. reintentar la migración
-- Hace falta hacerlo a mano porque MySQL hace commit implícito en cada DDL: esta migración NO
-- se revierte sola, y un reintento sin el paso 2 muere en el CREATE TABLE. Los booleanos de
-- ciclos_lavadero siguen intactos (esta migración no los toca), así que no se pierde nada.
--
-- Sentencias separadas y sin AFTER, para que corra igual en H2 (tests) y MySQL (producción).

-- 1. Catálogo de insumos extra de un ciclo de lavado.
--    Nace con `activo` aunque todavía nadie lo lea: el plan de Ajustes da de baja insumos,
--    y agregar la columna después costaría otra migración sobre una tabla recién creada.
CREATE TABLE catalogo_insumos (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    activo BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO catalogo_insumos (nombre) VALUES ('Suavizante'), ('Potenciador');

-- 2. Qué insumos lleva cada ciclo.
--    PK compuesta: un insumo no se repite dentro de un ciclo, y eso lo sostiene la base,
--    no la card. Sin columna de orden: el orden en que se eligieron no importa.
--    FK del ciclo en CASCADE (igual que elementos_ciclo_lavadero) y la del insumo en
--    RESTRICT: un insumo usado por algún ciclo no se puede borrar del catálogo.
CREATE TABLE insumos_ciclo_lavadero (
    ciclo_id  INT NOT NULL,
    insumo_id INT NOT NULL,
    PRIMARY KEY (ciclo_id, insumo_id),
    FOREIGN KEY (ciclo_id)  REFERENCES ciclos_lavadero(id)  ON DELETE CASCADE,
    FOREIGN KEY (insumo_id) REFERENCES catalogo_insumos(id) ON DELETE RESTRICT
);

-- 3. Migración de datos. Tiene que correr ANTES de los DROP de la V25: al revés, el DROP se
--    lleva los datos y estos INSERT insertan cero filas sin fallar — un error silencioso que
--    no deja rastro en ningún log.
--    El id del insumo se resuelve por nombre porque es AUTO_INCREMENT y no se puede hardcodear
--    (mismo razonamiento que Constantes.Lavadero.CLIENTE_APTIUM).
INSERT INTO insumos_ciclo_lavadero (ciclo_id, insumo_id)
SELECT cl.id, ci.id
FROM ciclos_lavadero cl
JOIN catalogo_insumos ci ON ci.nombre = 'Suavizante'
WHERE cl.suavizante = TRUE;

INSERT INTO insumos_ciclo_lavadero (ciclo_id, insumo_id)
SELECT cl.id, ci.id
FROM ciclos_lavadero cl
JOIN catalogo_insumos ci ON ci.nombre = 'Potenciador'
WHERE cl.potenciador = TRUE;
