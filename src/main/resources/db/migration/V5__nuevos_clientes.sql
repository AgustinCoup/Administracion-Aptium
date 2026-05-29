-- Clientes a eliminar:
DELETE IGNORE FROM clientes WHERE nombre IN (
    'MINISTERIO DE SALUD'
);

-- Clientes a insertar:
INSERT IGNORE INTO clientes (nombre) VALUES
('Ministerio de Salud - Río Cuarto'),
('Ministerio de Salud - Villa María'),
('Ministerio de Salud - Bell Ville'),
('Ministerio de Salud - Marcos Juárez'),
('Ministerio de Salud - Corral de Bustos'),
('Ministerio de Salud - Huinca Renancó'),
('Ministerio de Salud - Laboulaye'),
('Abdala, Victor Esteban'),
('Vettorazzi, María Luz - RYCEA'),
('Santa Juliana, Luis'),
('Palacios, Santiado - Clinica del niño'),
('Maffrand'),
('Serra, María'),
('Etcheverry, Sofía'),
('Ballesté, Luciana'),
('IMEB'),
('Kremer, Mauricio'),
('Aznar, Ivan - Civus'),
('Oyola, Ricardo'),
('Conti, Adriana'),
('Imperio Dental'),
('Roasio, Maximiliano');
