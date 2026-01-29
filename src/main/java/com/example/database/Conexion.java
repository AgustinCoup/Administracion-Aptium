package com.example.database;

import java.sql.*;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Conexion {
    private static final Properties PROPS = new Properties();

    static {
        try {
            Path p = Paths.get("Administracion-Aptium/config.properties");
            if (!Files.exists(p)) {
                p = Paths.get("config.properties");
            }
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    PROPS.load(is);
                }
            } else {
                System.out.println("No se encontró config.properties; usando valores por defecto.");
            }
        } catch (IOException e) {
            System.out.println("No se pudo cargar config.properties: " + e.getMessage());
        }
    }

    private static String getDbIp() { return PROPS.getProperty("db.ip", "localhost"); }
    private static String getDbUser() { return PROPS.getProperty("db.user", "root"); }
    private static String getDbPassword() { return PROPS.getProperty("db.pass", "tu_password_aqui"); }

    public static Connection conectar() {
        try {
            // URL a MySQL sin especificar BD
            String url = "jdbc:mysql://" + getDbIp() + ":3306/?serverTimezone=UTC";
            Connection conn = DriverManager.getConnection(url, getDbUser(), getDbPassword());
            
            // Seleccionar la base de datos después de conectar
            if (conn != null) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("USE sistema_empresa");
                }
            }
            
            return conn;
        } catch (SQLException e) {
            System.out.println("Error al conectar a MySQL: " + e.getMessage());
            return null;
        }
    }

    // --- NUEVOS MÉTODOS DE INICIALIZACIÓN ---

    public static void inicializarBaseDeDatos() {
        try (Connection conn = conectar(); Statement stmt = conn.createStatement()) {
            if (conn == null) return;

            // Crear la base de datos si no existe
            stmt.execute("CREATE DATABASE IF NOT EXISTS sistema_empresa");
            stmt.execute("USE sistema_empresa");

            // PRIMERO: Crear tabla de clientes (otras tablas la referencian)
            ClientesInitializer.crearTabla(conn);

            // Crear tabla de profesionales
            String tablaProfesionales = "CREATE TABLE IF NOT EXISTS profesionales ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "nombre VARCHAR(150) NOT NULL UNIQUE);";
            stmt.execute(tablaProfesionales);

            // Crear tabla de instituciones
            String tablaInstituciones = "CREATE TABLE IF NOT EXISTS instituciones ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "nombre VARCHAR(150) NOT NULL UNIQUE);";
            stmt.execute(tablaInstituciones);

            //AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
            //DATOS DE PRUEBA PARA INSTITUCIONES
            String insertarInstituciones = "INSERT IGNORE INTO instituciones (nombre) VALUES "
                    + "('HOSPITAL ITALIANO RIO CUARTO'), "
                    + "('CLINICA RIO CUARTO S.A.'), "
                    + "('CENTRO DE DIAGNOSTICO Y TRATAMIENTO MEDICO SRL');";
            stmt.execute(insertarInstituciones);
            //AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA

            // Crear tabla del catálogo
            String tablaCatalogo = "CREATE TABLE IF NOT EXISTS catalogo_descripciones ("
                    + "codigo INT PRIMARY KEY, "
                    + "descripcion VARCHAR(255) NOT NULL);";

            // SEGUNDO: Crear tabla de equipos (referencia a clientes y profesionales)
            String tablaEquipos = "CREATE TABLE IF NOT EXISTS equipos ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "nro_cliente INT NOT NULL, "
                    + "cliente_nombre VARCHAR(100), "
                    + "nro_profesional INT, "
                    + "paciente VARCHAR(150), "
                    + "nro_institucion INT NOT NULL, "
                    + "nro_operador INT, "
                    + "operador_nombre VARCHAR(150), "
                    + "estado VARCHAR(50) DEFAULT 'Nuevo', "
                    + "fecha_ingreso TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (nro_cliente) REFERENCES clientes(id) ON DELETE RESTRICT, "
                    + "FOREIGN KEY (nro_profesional) REFERENCES profesionales(id) ON DELETE RESTRICT, "
                    + "FOREIGN KEY (nro_institucion) REFERENCES instituciones(id) ON DELETE RESTRICT);";

            // TERCERO: Crear tabla de materiales de equipos (referencia a equipos)
            String tablaMateriales = "CREATE TABLE IF NOT EXISTS equipo_materiales ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "equipo_id INT NOT NULL, "
                    + "codigo_catalogo INT, "
                    + "descripcion_copia VARCHAR(255), "
                    + "cantidad INT, "
                    + "estado VARCHAR(50) DEFAULT 'Nuevo', "
                    + "FOREIGN KEY (equipo_id) REFERENCES equipos(id) ON DELETE CASCADE)";

            stmt.execute(tablaCatalogo);
            stmt.execute(tablaEquipos);
            stmt.execute(tablaMateriales);

            // Verificar si el catálogo está vacío para cargar los datos iniciales
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM catalogo_descripciones");
            if (rs.next() && rs.getInt(1) == 0) {
                cargarDatosCatalogo(conn);
            }

            // Cargar clientes iniciales si corresponde
            ClientesInitializer.cargarDatosIniciales(conn);
            System.out.println("Estructura de base de datos verificada.");
        } catch (SQLException e) {
            System.out.println("Error al inicializar tablas: " + e.getMessage());
        }
    }

    private static void cargarDatosCatalogo(Connection conn) throws SQLException {
        String sql = "INSERT INTO catalogo_descripciones (codigo, descripcion) VALUES (?, ?)";
        Object[][] datos = {
            {400, "Tornillera"}, {401, "Caja de Cirugía"}, {402, "Caja de Cirugía tamaño \"M\""},
            {403, "Caja de Cirugía tamaño \"L\""}, {404, "Caja de Cirugía tamaño \"XL\""},
            {405, "Caja de Cirugía tamaño \"XXL\""}, {406, "Makita - Perforador - Taladro"},
            {407, "Sierra BTR o simil, con hojas + accesorios"}, {408, "Micromotores"},
            {409, "Guias Metálicas - Instrumental pequeño"}, {410, "Instrumental grande"},
            {411, "Prótesis"}, {412, "Baterías extras"}, {413, "Implantes"},
            {414, "atornilladores/ dremmel"}, {415, "clavijas"}, {416, "alambre"},
            {417, "SUPER XXL"}, {418, "clavos - placas"}, {419, "Makita APTIUM"},
            {420, "Tornillo - elemento pequeño"}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Object[] fila : datos) {
                pstmt.setInt(1, (int) fila[0]);
                pstmt.setString(2, (String) fila[1]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            System.out.println("Catálogo inicial cargado con éxito.");
        }
    }
}