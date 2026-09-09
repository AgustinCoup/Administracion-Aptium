-- Salidas del lavadero: cantidades ya secadas y dobladas ("Listo") y su destino final.
--
-- Una fila = una cantidad de una tanda lavada (elementos_ciclo_lavadero) marcada como Listo.
-- destino/equipo_otros_id/fecha_salida quedan en NULL hasta que se decide el destino:
--   NULL            -> lista, todavía sin destino (se puede revertir a Lavado)
--   'FUERA_DE_FLUJO'-> se devuelve al cliente, no entra al CDE
--   'CDE_OTROS'     -> se creó un ingreso equipo_otros; equipo_otros_id apunta a él
--
-- Las dos FK tienen políticas distintas a propósito:
--
-- elemento_ciclo_id -> RESTRICT. Nada del lavadero se borra nunca (no hay un solo
--   DELETE sobre ingresos/clasificación/ciclos en todo el código), así que RESTRICT
--   no bloquea ningún flujo real y protege la trazabilidad si alguien toca la BD a mano.
--
-- equipo_otros_id   -> SET NULL. Los equipos "otros" SÍ se borran: Correcciones permite
--   eliminar un ingreso mientras está en estado Nuevo (EquipoOtrosDAO.eliminarEquipo),
--   que es justamente el estado en el que nace un ingreso derivado del lavadero. Con
--   RESTRICT, eliminar desde Correcciones un ingreso venido del lavadero fallaría con
--   "Error al eliminar el equipo". Con SET NULL la salida conserva destino='CDE_OTROS'
--   y pierde sólo el puntero: la historia queda contada correctamente ("se derivó al CDE,
--   y ese ingreso después se eliminó"), y la eliminación en sí ya queda auditada en
--   equipos_eliminados.

CREATE TABLE salidas_lavadero (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    elemento_ciclo_id INT NOT NULL,
    cantidad          INT NOT NULL,
    fecha_listo       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    destino           VARCHAR(20) NULL,
    equipo_otros_id   INT         NULL,
    fecha_salida      TIMESTAMP   NULL,
    FOREIGN KEY (elemento_ciclo_id) REFERENCES elementos_ciclo_lavadero(id) ON DELETE RESTRICT,
    FOREIGN KEY (equipo_otros_id)   REFERENCES equipo_otros(id)             ON DELETE SET NULL
);

CREATE INDEX idx_salidas_elemento_ciclo ON salidas_lavadero (elemento_ciclo_id);
CREATE INDEX idx_salidas_destino        ON salidas_lavadero (destino);
