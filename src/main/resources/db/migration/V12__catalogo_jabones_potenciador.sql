-- Catálogo de jabones (reemplaza el enum TipoJabon hardcodeado)
CREATE TABLE catalogo_jabones (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO catalogo_jabones (nombre) VALUES ('Skip'), ('Lider');

-- Limpiar ciclos existentes (tipo_jabon era enum sin correspondencia a los jabones reales)
DELETE FROM elementos_ciclo_lavadero;
DELETE FROM ciclos_lavadero;

-- Reemplazar tipo_jabon (VARCHAR enum) por FK al catálogo; agregar potenciador.
-- ALTER separados y sin AFTER para compatibilidad con H2 (tests) y MySQL (producción).
ALTER TABLE ciclos_lavadero DROP COLUMN tipo_jabon;
ALTER TABLE ciclos_lavadero ADD COLUMN jabon_id INT NOT NULL;
ALTER TABLE ciclos_lavadero ADD COLUMN potenciador BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ciclos_lavadero
    ADD CONSTRAINT fk_ciclos_jabon FOREIGN KEY (jabon_id) REFERENCES catalogo_jabones(id) ON DELETE RESTRICT;
