-- Archivo de ingresos eliminados: la copia completa de un ingreso que se borra físicamente.
--
-- Eliminar un ingreso (Ortopedias, Otros o Lavadero, en cualquier estado) es un borrado FÍSICO:
-- las consultas existentes no se tocan, y no hay ~30 filtros que un olvido convierta en un
-- ingreso borrado que reaparece. Antes del DELETE, y en la MISMA transacción, se escribe acá una
-- fila con el árbol completo del ingreso en JSON (`snapshot`). Esta fila es lo único que queda
-- del ingreso: un borrado sin archivo no puede existir. Es la diferencia con Correcciones, que
-- audita en equipos_eliminados/materiales_eliminados DESPUÉS del DELETE y acepta la
-- no-atomicidad. Esta tabla no aparece en la pantalla de Auditoría.
--
-- Una sola tabla para los tres módulos (`modulo` = ModuloIngreso.name()): columnas para buscar
-- y el resto en el JSON, que lleva su propio número de formato para evolucionar sin migrar filas
-- viejas. Un borrado de Lavadero escribe una fila LAVADERO y una OTROS por cada ingreso del CDE
-- que arrastró, con `archivo_padre_id` apuntando a la de Lavadero: así, buscar un ingreso del
-- CDE por su id lo encuentra aunque haya caído junto con uno de Lavadero.
--
-- SIN FOREIGN KEYS, a propósito:
--   - ni a `clientes`: el archivo guarda el nombre como texto (igual que equipos_eliminados). Una
--     FK obligaría a contemplar esta tabla en la fusión de clientes
--     (FusionClientesDAOTest.todaTablaConFkAClientes_estaContempladaEnLaFusion lo exige), y un
--     archivo no puede impedir borrar ni fusionar un cliente;
--   - ni `archivo_padre_id` -> `id`: es un archivo, de acá no se borra nada.
--
-- Sin CHECK ni AFTER, y los índices en sentencias aparte: patrón del repo, para que corra igual
-- en H2 (modo MySQL, tests) y en MySQL (producción).
--
-- A partir de esta migración Lavadero SÍ se borra, lo que desmiente el comentario de la V17
-- ("Nada del lavadero se borra nunca"). La V17 no se toca: una migración aplicada no se
-- modifica. La corrección queda documentada en CLAUDE.md.
--
-- RECUPERACIÓN si queda en estado `failed`: flyway repair, DROP TABLE IF EXISTS
-- ingresos_eliminados; y reintentar (MySQL hace commit implícito en cada DDL).

CREATE TABLE ingresos_eliminados (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    modulo              VARCHAR(20)  NOT NULL,
    ingreso_id_original INT          NOT NULL,
    cliente_nombre      VARCHAR(150) NULL,
    fecha_ingreso       TIMESTAMP    NULL,
    estado              VARCHAR(50)  NULL,
    motivo              VARCHAR(500) NOT NULL,
    puesto              VARCHAR(255) NULL,
    archivo_padre_id    INT          NULL,
    fecha_eliminacion   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    snapshot            LONGTEXT     NOT NULL
);

CREATE INDEX idx_ing_elim_modulo_id ON ingresos_eliminados (modulo, ingreso_id_original);
CREATE INDEX idx_ing_elim_fecha     ON ingresos_eliminados (fecha_eliminacion);
