-- Baja lógica de catálogos, ABM de lavarropas y jabón por defecto de cada tipo de lavado.
--
-- NUMERACIÓN: el plan la llama "V25", pero ese número lo tomó el plan A
-- (V25__drop_booleanos_y_litros_totales_ciclo.sql), que se partió en dos migraciones sobre el
-- final. Una migración ya escrita no se toca, así que ésta es la V26 y el DROP de
-- lavarropas.capacidad_litros del Paso 2 pasa a ser la V27.
--
-- `catalogo_descripciones` NO entra acá: ya tiene `vigente` desde V16, respetada por el Ingreso de
-- ortopedias y por las diez rutas de Correcciones. Agregarle un `activo` paralelo daría dos
-- fuentes de verdad sobre lo mismo. La diferencia de nombre (`vigente` allá, `activo` acá) queda
-- a propósito: renombrar obligaría a una migración sobre una tabla histórica, a tocar diez
-- consultas y a reescribir el comentario de la V16 —que explica en detalle por qué existe
-- `vigente`— para cero cambio de comportamiento.
--
-- Esta migración sólo AGREGA: ninguna columna ni tabla desaparece, así que el código anterior
-- sigue compilando y corriendo contra una base ya migrada. El `DROP COLUMN capacidad_litros` va
-- en la V27, escrita junto con el DAO que deja de leerla: dropearla acá dejaría
-- `LavarropasDAO.obtenerTodos()` con un `SELECT` roto, y ese método se come el `SQLException` y
-- devuelve lista vacía — o sea que fallaría en silencio.
--
-- RECUPERACIÓN si esta migración queda en estado `failed`:
--   1. flyway repair            (borra la fila fallida de flyway_schema_history)
--   2. DROP TABLE IF EXISTS jabon_por_tipo_lavado;
--      ALTER TABLE ... DROP COLUMN activo;   -- sólo las que hayan llegado a aplicarse
--   3. reintentar la migración
-- Hace falta hacerlo a mano porque MySQL hace commit implícito en cada DDL: esta migración NO se
-- revierte sola. No se pierde ningún dato: todo lo que hace es agregar.
--
-- Sentencias separadas y sin AFTER, para que corra igual en H2 (tests) y MySQL (producción).

-- 1. Baja lógica de los catálogos que hoy no la tienen.
--    DEFAULT TRUE deja todas las filas preexistentes activas, que es el estado en el que están.
ALTER TABLE catalogo_elementos_lavadero ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE catalogo_jabones            ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE catalogo_otros              ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Alta/baja de lavarropas. Un lavarropas dado de baja se reactiva y recupera su historia: es
--    la misma máquina. Por eso la baja es lógica y el número no se puede reusar para otra.
ALTER TABLE lavarropas ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

-- 3. Jabón por defecto de cada tipo de lavado.
--    La PK es el name() del enum TipoLavado (LIMPIO / SUCIO), que es exactamente lo que ya se
--    persiste en ciclos_lavadero.tipo_lavado. Una fila por tipo: el default es único por
--    definición, y la PK lo sostiene en la base en vez de en el service.
--    FK en RESTRICT: un jabón configurado como default no se puede borrar del catálogo.
CREATE TABLE jabon_por_tipo_lavado (
    tipo_lavado VARCHAR(20) PRIMARY KEY,
    jabon_id    INT NOT NULL,
    FOREIGN KEY (jabon_id) REFERENCES catalogo_jabones(id) ON DELETE RESTRICT
);

-- 4. Los defaults iniciales: Sucio → Skip, Limpio → Lider (los dos seeds de la V12).
--    Se resuelven POR NOMBRE porque catalogo_jabones.id es AUTO_INCREMENT y no se puede
--    hardcodear (mismo razonamiento que la V24 con los insumos y Constantes.Lavadero.CLIENTE_APTIUM).
--
--    Si en alguna base esos nombres ya no existen —los renombraron, los dieron de baja del
--    catálogo antes de que existiera este default—, el SELECT no devuelve filas, el INSERT
--    inserta cero y la migración NO falla. Es deliberado: "no hay default configurado" es un
--    estado legítimo, y la regla del jabón automático ya lo contempla ("sin default configurado,
--    no se toca nada"). Abortar el arranque de todas las máquinas porque falta una preferencia
--    de conveniencia sería desproporcionado. Lo fija MigracionV26Test.
INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id)
SELECT 'SUCIO',  id FROM catalogo_jabones WHERE nombre = 'Skip';

INSERT INTO jabon_por_tipo_lavado (tipo_lavado, jabon_id)
SELECT 'LIMPIO', id FROM catalogo_jabones WHERE nombre = 'Lider';
