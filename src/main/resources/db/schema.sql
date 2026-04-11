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
    volumen INT NOT NULL DEFAULT 100
);

-- Tabla de equipos (referencia a clientes, profesionales e instituciones)
CREATE TABLE IF NOT EXISTS equipos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nro_cliente INT NOT NULL,
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

-- Tabla de lotes de esterilizacion
CREATE TABLE IF NOT EXISTS lotes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_negocio VARCHAR(20) NOT NULL UNIQUE,
    anio INT NOT NULL,
    secuencia INT NOT NULL,
    autoclave_nombre VARCHAR(150) NOT NULL,
    capacidad_total INT NOT NULL,
    capacidad_usada INT NOT NULL,
    fecha_inicio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_fin TIMESTAMP NULL,
    estado VARCHAR(20) DEFAULT 'ACTIVO' COMMENT 'ACTIVO, EXITOSO, FALLIDO',
    INDEX idx_lotes_autoclave (autoclave_nombre),
    INDEX idx_lotes_anio_secuencia (anio, secuencia),
    FOREIGN KEY (autoclave_nombre) REFERENCES autoclaves(nombre) ON DELETE RESTRICT
);

-- Tabla de materiales de equipos (lotes por estado)
CREATE TABLE IF NOT EXISTS equipo_materiales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    equipo_id INT NOT NULL,
    codigo_catalogo INT NOT NULL,
    cantidad INT,
    estado VARCHAR(50) DEFAULT 'Nuevo',
    lote_id INT,
    INDEX idx_equipo_material_equipo (equipo_id),
    INDEX idx_equipo_material_estado (equipo_id, codigo_catalogo, estado),
    INDEX idx_equipo_material_lote (lote_id),
    FOREIGN KEY (equipo_id) REFERENCES equipos(id) ON DELETE CASCADE,
    FOREIGN KEY (codigo_catalogo) REFERENCES catalogo_descripciones(codigo) ON DELETE RESTRICT,
    FOREIGN KEY (lote_id) REFERENCES lotes(id) ON DELETE SET NULL
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

-- ============================================================
-- TABLAS DE AUDITORÍA - Para correcciones y cambios
-- ============================================================

-- Tabla de auditoría para materiales (modificaciones y correcciones)
CREATE TABLE IF NOT EXISTS equipos_auditoria (
    id INT AUTO_INCREMENT PRIMARY KEY,
    equipo_id INT NOT NULL,
    material_id INT,
    tipo_cambio VARCHAR(50) NOT NULL COMMENT 'MODIFICACION_CANTIDAD, MODIFICACION_CODIGO, ELIMINACION_EQUIPO',
    campo_modificado VARCHAR(100),
    valor_anterior VARCHAR(255),
    valor_nuevo VARCHAR(255),
    motivo VARCHAR(500),
    fecha_cambio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_auditoria_equipo (equipo_id),
    INDEX idx_auditoria_tipo (tipo_cambio),
    INDEX idx_auditoria_fecha (fecha_cambio)
);

-- Historial persistente de equipos eliminados (no depende de FK para conservar trazabilidad)
CREATE TABLE IF NOT EXISTS equipos_eliminados (
    id INT AUTO_INCREMENT PRIMARY KEY,
    equipo_id_original INT NOT NULL,
    nro_cliente INT,
    cliente_nombre VARCHAR(150),
    nro_profesional INT,
    paciente VARCHAR(150),
    nro_institucion INT,
    institucion_nombre VARCHAR(150),
    estado VARCHAR(50),
    motivo VARCHAR(500),
    fecha_eliminacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eq_elim_equipo_id (equipo_id_original),
    INDEX idx_eq_elim_fecha (fecha_eliminacion)
);

-- Historial persistente de materiales eliminados (por código y equipo)
CREATE TABLE IF NOT EXISTS materiales_eliminados (
    id INT AUTO_INCREMENT PRIMARY KEY,
    equipo_id_original INT NOT NULL,
    material_id_original INT,
    codigo_catalogo INT NOT NULL,
    descripcion VARCHAR(255),
    cantidad INT,
    estado VARCHAR(50),
    motivo VARCHAR(500),
    fecha_eliminacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mat_elim_equipo_id (equipo_id_original),
    INDEX idx_mat_elim_codigo (codigo_catalogo),
    INDEX idx_mat_elim_fecha (fecha_eliminacion)
);

-- ============================================================
-- ADICIONES AL SCHEMA: Tablas para ingreso de tipo "Otros"
-- Se agregan al final de schema.sql
-- ============================================================

-- Catálogo de materiales para "Otros"
-- Sin seed: crece on-the-fly con el uso
CREATE TABLE IF NOT EXISTS catalogo_otros (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    descripcion VARCHAR(255) NOT NULL UNIQUE
);

-- Tabla de equipos "Otros"
CREATE TABLE IF NOT EXISTS equipo_otros (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    nro_cliente      INT NOT NULL,
    estado           VARCHAR(50) DEFAULT 'Nuevo',
    requiere_lavado  TINYINT(1)  NOT NULL DEFAULT 1,
    requiere_empaque TINYINT(1)  NOT NULL DEFAULT 1,
    tipo_ingreso     VARCHAR(10) NOT NULL DEFAULT 'DETALLES' COMMENT 'REMITO | DETALLES',
    remito_id        VARCHAR(30) NULL COMMENT 'Identificador: ddmmaaaa-{id}',
    remito_cantidad  INT         NULL COMMENT 'Cantidad de elementos del remito',
    remito_observaciones TEXT    NULL COMMENT 'Observaciones opcionales del remito',
    volumen_equipo   INT         NOT NULL DEFAULT 0 COMMENT 'Suma acumulada de litros en lotes exitosos',
    fecha_ingreso    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otros_remito_id (remito_id),
    FOREIGN KEY (nro_cliente) REFERENCES clientes(id) ON DELETE RESTRICT
);

-- Materiales de equipos "Otros"
CREATE TABLE IF NOT EXISTS equipo_otros_materiales (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    equipo_otros_id  INT          NOT NULL,
    catalogo_otros_id INT         NOT NULL,
    descripcion      VARCHAR(255) NOT NULL,
    cantidad         INT          NOT NULL DEFAULT 1,
    estado           VARCHAR(50)  DEFAULT 'Nuevo',
    lote_id          INT,
    volumen_lote     INT          NULL COMMENT 'Litros que ocupa este grupo al esterilizar',
    INDEX idx_otros_mat_equipo  (equipo_otros_id),
    INDEX idx_otros_mat_estado  (equipo_otros_id, estado),
    INDEX idx_otros_mat_lote    (lote_id),
    FOREIGN KEY (equipo_otros_id)   REFERENCES equipo_otros(id)     ON DELETE CASCADE,
    FOREIGN KEY (catalogo_otros_id) REFERENCES catalogo_otros(id)   ON DELETE RESTRICT,
    FOREIGN KEY (lote_id)           REFERENCES lotes(id)            ON DELETE SET NULL
);

-- Trazabilidad de movimientos de materiales "Otros"
CREATE TABLE IF NOT EXISTS otros_material_movimientos (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    material_id     INT NOT NULL,
    equipo_otros_id INT NOT NULL,
    cantidad        INT NOT NULL,
    estado_origen   VARCHAR(50),
    estado_destino  VARCHAR(50) NOT NULL,
    fecha           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otros_mov_material (material_id),
    INDEX idx_otros_mov_equipo   (equipo_otros_id),
    FOREIGN KEY (material_id)     REFERENCES equipo_otros_materiales(id) ON DELETE CASCADE,
    FOREIGN KEY (equipo_otros_id) REFERENCES equipo_otros(id)            ON DELETE CASCADE
);