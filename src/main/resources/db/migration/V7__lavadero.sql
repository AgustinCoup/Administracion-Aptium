CREATE TABLE ingresos_lavadero (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    cliente_id    INT NOT NULL,
    fecha_ingreso TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE RESTRICT
);

CREATE TABLE bolsas_lavadero (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    ingreso_id INT NOT NULL,
    peso_kg    DECIMAL(6,2) NOT NULL,
    FOREIGN KEY (ingreso_id) REFERENCES ingresos_lavadero(id) ON DELETE CASCADE
);