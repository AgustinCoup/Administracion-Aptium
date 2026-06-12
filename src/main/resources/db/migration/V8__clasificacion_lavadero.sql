CREATE TABLE catalogo_elementos_lavadero (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO catalogo_elementos_lavadero (nombre) VALUES
    ('Equipo de trauma'),
    ('Equipo de hernia'),
    ('Equipo de HD'),
    ('Equipo pediatrico'),
    ('Equipo de parto'),
    ('Kit de anestesia'),
    ('Kit de sala 5'),
    ('Kit cirugia menor'),
    ('Pierneras'),
    ('Arco C corto'),
    ('Arco C largo'),
    ('Bolsillo'),
    ('Batas'),
    ('Toallon'),
    ('Toalla'),
    ('Funda'),
    ('Cubre camilla tela'),
    ('Cubre camilla Plastico'),
    ('Sabana grande'),
    ('Sabana bebe'),
    ('Poncho'),
    ('Pantalon'),
    ('Ambo'),
    ('Seca instrumental'),
    ('Paño quirofano');

CREATE TABLE elementos_clasificacion_lavadero (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    ingreso_id  INT NOT NULL,
    elemento_id INT NOT NULL,
    cantidad    INT NOT NULL,
    FOREIGN KEY (ingreso_id)  REFERENCES ingresos_lavadero(id)          ON DELETE CASCADE,
    FOREIGN KEY (elemento_id) REFERENCES catalogo_elementos_lavadero(id) ON DELETE RESTRICT
);
