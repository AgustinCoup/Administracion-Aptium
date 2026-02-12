-- ============================================================
-- SCHEMA: Estructura de tablas de la base de datos
-- ============================================================

-- Tabla de clientes (debe crearse primero - referenciada por otras tablas)
CREATE TABLE IF NOT EXISTS clientes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL
);

-- Tabla de profesionales
CREATE TABLE IF NOT EXISTS profesionales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL UNIQUE
);

-- Tabla de instituciones
CREATE TABLE IF NOT EXISTS instituciones (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL UNIQUE
);

-- Tabla de autoclaves
CREATE TABLE IF NOT EXISTS autoclaves (
    nombre VARCHAR(150) PRIMARY KEY,
    capacidad INT NOT NULL
);

-- Tabla del catálogo de materiales
CREATE TABLE IF NOT EXISTS catalogo_descripciones (
    codigo INT PRIMARY KEY,
    descripcion VARCHAR(255) NOT NULL,
    volumen INT NOT NULL
);

-- Tabla de equipos (referencia a clientes, profesionales e instituciones)
CREATE TABLE IF NOT EXISTS equipos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nro_cliente INT NOT NULL,
    cliente_nombre VARCHAR(100),
    nro_profesional INT,
    paciente VARCHAR(150),
    nro_institucion INT NOT NULL,
    nro_operador INT,
    operador_nombre VARCHAR(150),
    estado VARCHAR(50) DEFAULT 'Nuevo',
    requiere_lavado TINYINT(1) NOT NULL DEFAULT 1,
    requiere_empaque TINYINT(1) NOT NULL DEFAULT 1,
    fecha_ingreso TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (nro_cliente) REFERENCES clientes(id) ON DELETE RESTRICT,
    FOREIGN KEY (nro_profesional) REFERENCES profesionales(id) ON DELETE RESTRICT,
    FOREIGN KEY (nro_institucion) REFERENCES instituciones(id) ON DELETE RESTRICT
);

-- Tabla de materiales de equipos (lotes por estado)
CREATE TABLE IF NOT EXISTS equipo_materiales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    equipo_id INT NOT NULL,
    codigo_catalogo INT,
    descripcion_copia VARCHAR(255),
    cantidad INT,
    estado VARCHAR(50) DEFAULT 'Nuevo',
    INDEX idx_equipo_material_equipo (equipo_id),
    INDEX idx_equipo_material_estado (equipo_id, codigo_catalogo, estado),
    FOREIGN KEY (equipo_id) REFERENCES equipos(id) ON DELETE CASCADE
);

-- Tabla de movimientos de materiales (trazabilidad)
CREATE TABLE IF NOT EXISTS material_movimientos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    material_id INT NOT NULL,
    equipo_id INT NOT NULL,
    cantidad INT NOT NULL,
    estado_origen VARCHAR(50),
    estado_destino VARCHAR(50) NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mov_material (material_id),
    INDEX idx_mov_equipo (equipo_id),
    FOREIGN KEY (material_id) REFERENCES equipo_materiales(id) ON DELETE CASCADE,
    FOREIGN KEY (equipo_id) REFERENCES equipos(id) ON DELETE CASCADE
);
