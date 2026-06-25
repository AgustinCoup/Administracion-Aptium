ALTER TABLE catalogo_elementos_lavadero
    ADD COLUMN categoria VARCHAR(50) NOT NULL DEFAULT 'REGULAR';

UPDATE catalogo_elementos_lavadero
SET categoria = 'EQUIPO'
WHERE nombre LIKE 'Equipo%';
