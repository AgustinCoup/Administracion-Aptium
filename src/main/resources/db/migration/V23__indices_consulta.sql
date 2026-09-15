-- Índices de fecha y estado para las consultas de listado (Paso 5 de
-- plans/conexiones-y-paginacion.md). Ninguno existía: verificado con `grep INDEX` sobre las 22
-- migraciones previas — los únicos índices que mencionan `estado` son compuestos de material
-- (V1, V2), no de la cabecera del equipo/ingreso.
--
-- Qué consulta paga cada uno:
--   idx_equipos_fecha_ingreso / idx_otros_fecha_ingreso
--       el ORDER BY de todos los listados de EquipoDAO/EquipoOtrosDAO y obtenerEntreFechas(...).
--       En InnoDB todo índice secundario incluye la PK, así que cubre entero un
--       `ORDER BY fecha_ingreso DESC, id DESC` — el orden que usan los listados hoy y el que
--       va a usar la paginación SQL del Paso 10.
--   idx_equipos_estado / idx_otros_estado
--       obtenerEquiposNuevos() (WHERE estado = 'Nuevo') y el filtro por estado que el Paso 10
--       mueve a SQL.
--   idx_ingresos_lav_estado
--       CicloLavaderoDAO (WHERE il.estado = 'CLASIFICADO') y el filtro de estados del Paso 8.
--   idx_ingresos_lav_fecha_ingreso
--       el ORDER BY de HistorialLavaderoDAO.SQL_RESUMEN y la paginación SQL del Paso 8.
CREATE INDEX idx_equipos_fecha_ingreso      ON equipos            (fecha_ingreso);
CREATE INDEX idx_equipos_estado             ON equipos            (estado);
CREATE INDEX idx_otros_fecha_ingreso        ON equipo_otros       (fecha_ingreso);
CREATE INDEX idx_otros_estado               ON equipo_otros       (estado);
CREATE INDEX idx_ingresos_lav_fecha_ingreso ON ingresos_lavadero  (fecha_ingreso);
CREATE INDEX idx_ingresos_lav_estado        ON ingresos_lavadero  (estado);
