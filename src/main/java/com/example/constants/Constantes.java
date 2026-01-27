package com.example.constants;

/**
 * Clase centralizada de constantes para toda la aplicación.
 * Evita el uso de "magic strings" y facilita el mantenimiento.
 * 
 * Organizada por categorías para mejor navegabilidad.
 */
public final class Constantes {
    
    // Prevenir instanciación
    private Constantes() {
        throw new UnsupportedOperationException("Clase de constantes no instanciable");
    }
    
    /**
     * Nombres de las pantallas en el CardLayout.
     * Usar estas constantes asegura que no haya errores de tipeo al navegar.
     */
    public static final class Pantallas {
        public static final String MENU_PRINCIPAL = "MENU_PRINCIPAL";
        public static final String ESTERILIZACION = "ESTERILIZACION";
        public static final String ES_ORTOPEDIA = "ES_ORTOPEDIA";
        public static final String INGRESO_ORTOPEDIA = "INGRESO_ORTOPEDIA";
        public static final String VER_CDE = "VER_CDE";
        public static final String VER_CDE_V2 = "VER_CDE_V2";
        
        private Pantallas() {}
    }
    
    /**
     * Mensajes de usuario predefinidos.
     * Centraliza textos para facilitar internacionalización futura.
     */
    public static final class Mensajes {
        // Mensajes de éxito
        public static final String DATOS_GUARDADOS = "Datos guardados correctamente.";
        public static final String OPERACION_EXITOSA = "Operación completada exitosamente.";
        
        // Mensajes de validación
        public static final String CAMPOS_INCOMPLETOS = "Por favor, complete todos los campos obligatorios.";
        public static final String FORMATO_PROFESIONAL_INVALIDO = "El Profesional debe seguir el formato: Apellido Nombre";
        public static final String FORMATO_PACIENTE_INVALIDO = "El Paciente debe seguir el formato: Apellido Nombre";
        public static final String DEBE_AGREGAR_MATERIAL = "Debe agregar al menos un material al equipo.";
        
        // Mensajes de error
        public static final String ERROR_CONEXION_BD = "No se pudo conectar con el servidor de base de datos.\n" +
                "Por favor, verifique que la PC Servidor esté encendida.";
        public static final String ERROR_GUARDAR_DATOS = "Error al guardar los datos. Intente nuevamente.";
        public static final String ERROR_CARGAR_DATOS = "Error al cargar los datos.";
        
        // Títulos de diálogo
        public static final String TITULO_EXITO = "Éxito";
        public static final String TITULO_ERROR = "Error";
        public static final String TITULO_ADVERTENCIA = "Advertencia";
        public static final String TITULO_ERROR_CONEXION = "Error de Conexión";
        
        private Mensajes() {}
    }
    
    /**
     * Títulos de las pantallas.
     */
    public static final class Titulos {
        public static final String MENU_PRINCIPAL = "Menú Principal de Gestión";
        public static final String CENTRO_ESTERILIZACION = "CENTRO DE ESTERILIZACIÓN";
        public static final String INGRESO = "INGRESO";
        public static final String INGRESO_ORTOPEDIA = "INGRESO ORTOPEDIA";
        public static final String ESTADO_PROCESOS = "ESTADO DE PROCESOS EN TIEMPO REAL";
        
        private Titulos() {}
    }
    
    /**
     * Etiquetas de botones.
     */
    public static final class Botones {
        public static final String VOLVER = "<- Volver";
        public static final String GUARDAR = "Guardar";
        public static final String CANCELAR = "Cancelar";
        public static final String VER = "Ver";
        public static final String REGISTRAR = "Registrar";
        public static final String INGRESAR = "Ingresar";
        public static final String AGREGAR = "+";
        public static final String ELIMINAR = "-";
        
        private Botones() {}
    }
    
    /**
     * Configuración de base de datos.
     */
    public static final class BaseDatos {
        public static final String NOMBRE_BD = "sistema_empresa";
        public static final String TABLA_CATALOGO = "catalogo_descripciones";
        public static final String TABLA_EQUIPOS = "equipos";
        public static final String TABLA_MATERIALES = "equipo_materiales";
        
        // Valores por defecto de configuración
        public static final String DB_HOST_DEFAULT = "localhost";
        public static final String DB_USER_DEFAULT = "root";
        public static final String DB_PORT = "3306";
        public static final String DB_TIMEZONE = "UTC";
        
        private BaseDatos() {}
    }
    
    /**
     * Formatos y patrones de validación.
     */
    public static final class Formatos {
        // Regex: Al menos una palabra (apellido) + espacio + al menos una palabra (nombre)
        public static final String REGEX_NOMBRE_APELLIDO = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ]+\\s+[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$";
        
        // Formato de fecha para códigos de equipo
        public static final String FORMATO_CODIGO_EQUIPO = "%d%d"; // Año + número secuencial
        
        private Formatos() {}
    }
    
    /**
     * Valores por defecto de la aplicación.
     */
    public static final class Defaults {
        public static final String ESTADO_INICIAL = "Nuevo";
        public static final String FUENTE_PRINCIPAL = "Arial";
        public static final int FUENTE_TAMANO_TITULO = 26;
        public static final int FUENTE_TAMANO_BOTON = 24;
        public static final int FUENTE_TAMANO_LABEL = 18;
        public static final int FUENTE_TAMANO_INPUT = 18;
        
        private Defaults() {}
    }
}
