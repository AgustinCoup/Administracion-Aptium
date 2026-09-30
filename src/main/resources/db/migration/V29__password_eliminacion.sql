-- Password para eliminar ingresos completos, guardada como hash PBKDF2.
--
-- Nunca la password en claro: salt, hash e iteraciones, generados con HasherPbkdf2
-- (PBKDF2WithHmacSHA256, salt aleatorio de 16 bytes, clave de 256 bits, Base64). Las iteraciones
-- van EN LA FILA y no en el código: se pueden subir más adelante sin invalidar el hash vigente,
-- porque quien verifica usa las del hash guardado. 600 000 es la recomendación de OWASP para
-- PBKDF2-HMAC-SHA256.
--
-- LA PASSWORD INICIAL está documentada en CLAUDE.md ("Eliminar ingresos → password"), junto con el
-- UPDATE que la restablece si se olvida. No se escribe acá. Aun así es pública: este archivo viaja
-- en el JAR que se publica en GitHub Releases y el hash se puede atacar por diccionario. Se aceptó
-- con la condición de avisar mientras siga vigente (es_inicial = TRUE); cambiarla desde Ajustes
-- la apaga. PasswordDAOTest.semillaDeLaMigracion_verificaConLaPasswordInicialDocumentada garantiza
-- que esta semilla verifica contra la password que documenta CLAUDE.md.
--
-- Qué protege: es una barrera contra borrados accidentales o no autorizados de operadores, NO un
-- límite contra quien tiene las credenciales de la base (con ellas se puede todo, incluido
-- restablecer esta fila).
--
-- UNA FILA POR PROPÓSITO, y no una tabla clave-valor genérica de configuración: una password no
-- es un valor de configuración. Tiene forma fija (algoritmo, iteraciones, salt, hash, es_inicial),
-- y en una clave-valor esas cinco cosas serían cinco filas sueltas que se pueden desincronizar
-- entre sí, sin que la base pueda exigir que existan juntas. Si mañana hace falta otra password
-- (otra acción protegida), es otra fila con otro `proposito`, sin migración.
--
-- Sin CHECK ni AFTER: patrón del repo, para que corra igual en H2 (tests) y MySQL (producción).
--
-- RECUPERACIÓN si queda en estado `failed`: flyway repair, DROP TABLE IF EXISTS passwords; y
-- reintentar (MySQL hace commit implícito en cada DDL).

CREATE TABLE passwords (
    proposito      VARCHAR(40)  PRIMARY KEY,
    algoritmo      VARCHAR(40)  NOT NULL,
    iteraciones    INT          NOT NULL,
    salt           VARCHAR(64)  NOT NULL,
    hash           VARCHAR(128) NOT NULL,
    es_inicial     BOOLEAN      NOT NULL,
    actualizado_en TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO passwords (proposito, algoritmo, iteraciones, salt, hash, es_inicial)
VALUES ('ELIMINAR_INGRESO', 'PBKDF2WithHmacSHA256', 600000,
        'Q4vyghGQ19SmsJ1XTFDaiw==', 'YrXrz108NVb5CY/uDPo4EX/ZVIJk8Yx4pxE7hqF1M0Q=', TRUE);
