-- 1. Estado de procesamiento en ingresos_lavadero
ALTER TABLE ingresos_lavadero
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';

-- 2. Backfill: ingresos que ya tienen clasificación pasan a CLASIFICADO
UPDATE ingresos_lavadero il
SET estado = 'CLASIFICADO'
WHERE EXISTS (
    SELECT 1 FROM elementos_clasificacion_lavadero e WHERE e.ingreso_id = il.id
);

-- 3. Tabla de lavarropas (análoga a autoclaves)
CREATE TABLE lavarropas (
    numero           INT PRIMARY KEY,
    capacidad_litros INT NOT NULL
);

INSERT INTO lavarropas (numero, capacidad_litros) VALUES
    (1, 13), (2, 13), (3, 13), (4, 13), (5, 13), (6, 13), (7, 13),
    (8, 13), (9, 13), (10, 13), (11, 13), (12, 13), (13, 13);

-- 4. Tabla de ciclos de lavado
CREATE TABLE ciclos_lavadero (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    lavarropas_numero INT NOT NULL,
    tipo_jabon        VARCHAR(100) NOT NULL,
    litros_jabon      DECIMAL(6,2) NOT NULL,
    suavizante        BOOLEAN NOT NULL DEFAULT FALSE,
    litros_totales    DECIMAL(8,2) NULL,
    fecha_inicio      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_fin         TIMESTAMP NULL,
    estado            VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    FOREIGN KEY (lavarropas_numero) REFERENCES lavarropas(numero) ON DELETE RESTRICT
);

CREATE INDEX idx_ciclos_lavarropas ON ciclos_lavadero (lavarropas_numero);

-- 5. Vínculo elemento clasificado <-> ciclo (permite subcantidades parciales)
CREATE TABLE elementos_ciclo_lavadero (
    id                        INT AUTO_INCREMENT PRIMARY KEY,
    ciclo_id                  INT NOT NULL,
    elemento_clasificacion_id INT NOT NULL,
    cantidad                  INT NOT NULL,
    FOREIGN KEY (ciclo_id)                  REFERENCES ciclos_lavadero(id)                  ON DELETE CASCADE,
    FOREIGN KEY (elemento_clasificacion_id) REFERENCES elementos_clasificacion_lavadero(id) ON DELETE RESTRICT
);
