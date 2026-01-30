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

-- Tabla del catálogo de materiales
CREATE TABLE IF NOT EXISTS catalogo_descripciones (
    codigo INT PRIMARY KEY,
    descripcion VARCHAR(255) NOT NULL
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
    fecha_ingreso TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (nro_cliente) REFERENCES clientes(id) ON DELETE RESTRICT,
    FOREIGN KEY (nro_profesional) REFERENCES profesionales(id) ON DELETE RESTRICT,
    FOREIGN KEY (nro_institucion) REFERENCES instituciones(id) ON DELETE RESTRICT
);

-- Tabla de materiales de equipos (referencia a equipos)
CREATE TABLE IF NOT EXISTS equipo_materiales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    equipo_id INT NOT NULL,
    codigo_catalogo INT,
    descripcion_copia VARCHAR(255),
    cantidad INT,
    estado VARCHAR(50) DEFAULT 'Nuevo',
    FOREIGN KEY (equipo_id) REFERENCES equipos(id) ON DELETE CASCADE
);
