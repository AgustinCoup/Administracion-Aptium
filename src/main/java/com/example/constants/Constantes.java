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
        public static final String REGISTRAR_ESTADO = "REGISTRAR_ESTADO";
        
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

        // Mensajes de validacion de formularios
        public static final String CAMPO_CLIENTE_OBLIGATORIO = "El campo Cliente es obligatorio.";
        public static final String CAMPO_INSTITUCION_OBLIGATORIO = "El campo Institución es obligatorio.";
        public static final String CLIENTE_NO_SELECCIONADO = "Debe seleccionar un cliente de la lista de sugerencias.";
        public static final String INSTITUCION_NO_SELECCIONADA = "Debe seleccionar una institución de la lista de sugerencias.";
        public static final String PROFESIONAL_NO_SELECCIONADO = "Si ingresa un profesional, debe seleccionar uno de la lista de sugerencias.";

        // Mensajes de guardado
        public static final String ERROR_GUARDAR_EQUIPO = "Error al guardar el equipo. Por favor, intente de nuevo.";
        public static final String TITULO_ERROR_GUARDAR = "Error al Guardar";

        // Mensajes de autocompletado
        public static final String AUTOCOMPLETE_DESCONOCIDO = "Desconocido";
        public static final String NOMBRE_VACIO = "El nombre no puede estar vacío";
        public static final String ERROR_GUARDAR_ENTIDAD = "Error al guardar %s. Por favor, intente de nuevo.";
        public static final String ERROR_GUARDAR_ENTIDAD_DETALLE = "Error al guardar %s: %s";

        // Mensajes de estado de materiales
        public static final String SELECCIONE_MATERIAL_AVANZAR = "Por favor, seleccione un material para avanzar.";
        public static final String MATERIAL_CAMBIOS_PENDIENTES = "Este material tiene cambios pendientes. Confirme antes de continuar.";
        public static final String MATERIAL_CAMBIO_PENDIENTE_DUP = "Este material ya tiene un cambio pendiente. Confirme antes de avanzar nuevamente.";
        public static final String MATERIAL_ESTADO_FINAL = "El material ya está en el estado final: %s";
        public static final String CONFIRMAR_CANCELACION = "¿Está seguro de que desea cancelar todos los cambios pendientes?";
        public static final String CONFIRMAR_CAMBIOS = "¿Confirmar cambios?\nEsta operación actualizará la base de datos.";
        public static final String CAMBIOS_GUARDADOS_OK = "Todos los cambios se guardaron correctamente.";
        public static final String CAMBIOS_GUARDADOS_ERROR = "Algunos cambios no se pudieron guardar:\n%s";
        public static final String ERROR_ACTUALIZAR_EQUIPO_ID = "- Error al actualizar equipo ID: %d\n";

        // Mensajes de cantidad para avanzar
        public static final String CANTIDAD_AVANZAR_PROMPT = "Cantidad a avanzar para: %s (disponible: %d)";
        public static final String CANTIDAD_AVANZAR_VACIA = "Ingrese una cantidad válida.";
        public static final String CANTIDAD_AVANZAR_RANGO = "La cantidad debe estar entre 1 y %d.";
        public static final String CANTIDAD_AVANZAR_NUMERO = "La cantidad debe ser un número entero.";
        public static final String CANTIDAD_AVANZAR_TODOS = "Todos";

        // Confirmación de entrega de equipo
        public static final String CONFIRMAR_ENTREGA_EQUIPO = "¿Marcar este equipo como entregado?\nEsta acción actualizará todos sus materiales.";
        public static final String ENTREGA_EQUIPO_OK = "Equipo entregado correctamente.";
        public static final String ENTREGA_EQUIPO_ERROR = "No se pudo marcar el equipo como entregado.";
        
        // Títulos de diálogo
        public static final String TITULO_EXITO = "Éxito";
        public static final String TITULO_ERROR = "Error";
        public static final String TITULO_ADVERTENCIA = "Advertencia";
        public static final String TITULO_ERROR_CONEXION = "Error de Conexión";
        public static final String TITULO_CLIENTE_NO_SELECCIONADO = "Cliente no seleccionado";
        public static final String TITULO_INSTITUCION_NO_SELECCIONADA = "Institución no seleccionada";
        public static final String TITULO_PROFESIONAL_NO_SELECCIONADO = "Profesional no seleccionado";
        public static final String TITULO_CONFIRMAR_CANCELACION = "Confirmar Cancelación";
        public static final String TITULO_CONFIRMAR_CAMBIOS = "Confirmar Cambios";
        public static final String TITULO_AVANZAR_SUBCANTIDAD = "Avanzar subcantidad";
        public static final String TITULO_CONFIRMAR_ENTREGA = "Confirmar Entrega";
        
        private Mensajes() {}
    }
    
    /**
     * Títulos de las pantallas.
     */
    public static final class Titulos {
        public static final String APP = "Sistema Empresa - v1.0";
        public static final String MENU_PRINCIPAL = "Menú Principal de Gestión";
        public static final String CENTRO_ESTERILIZACION = "CENTRO DE ESTERILIZACIÓN";
        public static final String INGRESO = "INGRESO";
        public static final String INGRESO_ORTOPEDIA = "INGRESO ORTOPEDIA";
        public static final String ESTADO_PROCESOS = "ESTADO DE PROCESOS EN TIEMPO REAL";
        public static final String REGISTRAR_ESTADO = "REGISTRAR ESTADO";
        
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
        public static final String AVANZAR_MATERIAL = "Avanzar Material Seleccionado";
        public static final String CONFIRMAR_GUARDAR = "Confirmar y Guardar";
        public static final String ENTREGAR_EQUIPO = "Marcar equipo como entregado";
        public static final String AGREGAR_TEXTO = "Agregar";
        public static final String ELIMINAR_FILA = "X";
        public static final String CENTRO_ESTERILIZACION = "Centro de Esterilización";
        public static final String LAVADERO = "Lavadero";
        public static final String DESINFECTADORA = "Desinfectadora";
        public static final String DISTRIBUIDORA = "Distribuidora";
        public static final String ORTOPEDIA = "Ortopedia";
        public static final String OTROS = "Otros";
        
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
        public static final String FORMATO_SPINNER_CANTIDAD = "#0.##";
        public static final String FORMATO_FECHA_HORA = "dd/MM/yyyy HH:mm";
        
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

    /**
     * Textos reutilizables de UI (labels, cabeceras, placeholders).
     */
    public static final class Textos {
        public static final String CAMBIOS_PENDIENTES = "Cambios pendientes: %d";
        public static final String TABLA_EQUIPOS_TITULO = "Equipos / Clientes";
        public static final String TABLA_MATERIALES_TITULO = "Materiales del Equipo (Seleccione para avanzar)";
        public static final String TABLA_MATERIALES_SELECCIONADO_TITULO = "Materiales del Equipo Seleccionado";
        public static final String COLUMNA_CLIENTE = "Cliente";
        public static final String COLUMNA_INSTITUCION = "Institución";
        public static final String COLUMNA_ESTADO = "Estado";
        public static final String COLUMNA_MATERIAL = "Material";
        public static final String COLUMNA_CANTIDAD = "Cantidad";
        public static final String COLUMNA_ULTIMO_MOVIMIENTO = "Último movimiento";
        public static final String TOTAL_ELEMENTOS = "Total Elementos: %d";
        public static final String CODIGO_INVALIDO = "Código inválido";
        public static final String SIN_MOVIMIENTO = "-";
        public static final String TOOLTIP_ELIMINAR_FILA = "Eliminar esta fila";
        public static final String LABEL_CLIENTE = "Cliente / Empresa:";
        public static final String LABEL_PROFESIONAL = "Profesional a cargo:";
        public static final String LABEL_PACIENTE = "Nombre del Paciente:";
        public static final String LABEL_INSTITUCION = "Institución:";
        public static final String LABEL_MATERIAL = "Material:";
        public static final String CHECK_REQUIERE_LAVADO = "Requiere lavado";
        public static final String CHECK_REQUIERE_EMPAQUE = "Requiere empaquetado";
        public static final String AYUDA_FORMATO_APELLIDO_NOMBRE = "Formato: Apellido Nombre";
        public static final String DIALOG_TITULO_AGREGAR = "Agregar nuevo/a %s";
        public static final String DIALOG_MENSAJE_AGREGAR = "<html>El/la %s no existe.<br>¿Desea agregarlo/la a la base de datos?</html>";
        public static final String LABEL_NOMBRE = "Nombre:";
        public static final String EJEMPLO_CAJA_INSTRUMENTAL = "Caja de Instrumental #102";
        public static final String EJEMPLO_SET_CIRUGIA = "Set de Cirugía Menor #05";
        public static final String ENTIDAD_CLIENTE = "Cliente";
        public static final String ENTIDAD_PROFESIONAL = "Profesional";
        public static final String ENTIDAD_INSTITUCION = "Institución";
        public static final String BOTON_PASAR_A = "Pasar a %s";
        public static final String BOTON_SELECCIONE_MATERIAL = "Seleccione un material";
        public static final String BOTON_ESTADO_FINAL = "Material en estado final";

        private Textos() {}
    }
}
