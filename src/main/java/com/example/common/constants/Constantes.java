package com.example.common.constants;

/**
 * Clase centralizada de constantes para toda la aplicación.
 * Evita el uso de "magic strings" y facilita el mantenimiento.
 *
 * Organizada por categorías para mejor navegabilidad.
 */
public final class Constantes {

    private Constantes() {
        throw new UnsupportedOperationException("Clase de constantes no instanciable");
    }

    /**
     * Nombres de las pantallas en el CardLayout.
     */
    public static final class Pantallas {
        public static final String MENU_PRINCIPAL        = "MENU_PRINCIPAL";
        public static final String ESTERILIZACION        = "ESTERILIZACION";
        public static final String ES_ORTOPEDIA          = "ES_ORTOPEDIA";
        public static final String INGRESO_ORTOPEDIA     = "INGRESO_ORTOPEDIA";
        public static final String VER_CDE               = "VER_CDE";
        public static final String VER_CDE_V2            = "VER_CDE_V2";
        public static final String REGISTRAR_ESTADO      = "REGISTRAR_ESTADO";
        public static final String EQUIPOS_PARA_ENTREGAR = "EQUIPOS_PARA_ENTREGAR";
        public static final String LOTES                 = "LOTES";
        public static final String VER_LOTES             = "VER_LOTES";
        public static final String CORRECCIONES          = "CORRECCIONES";
        public static final String AUDITORIA             = "AUDITORIA";
        public static final String INGRESO_OTROS         = "INGRESO_OTROS";
        public static final String VER_EQUIPOS           = "VER_EQUIPOS";
        public static final String LAVADERO               = "LAVADERO";
        public static final String INGRESO_LAVADERO       = "INGRESO_LAVADERO";
        public static final String CLASIFICACION_LAVADERO = "CLASIFICACION_LAVADERO";
        public static final String CICLOS_LAVADERO       = "CICLOS_LAVADERO";
        public static final String AJUSTES              = "AJUSTES";

        private Pantallas() {}
    }

    /**
     * Mensajes de usuario predefinidos.
     */
    public static final class Mensajes {
        public static final String DATOS_GUARDADOS   = "Datos guardados correctamente.";
        public static final String OPERACION_EXITOSA = "Operación completada exitosamente.";

        public static final String CAMPOS_INCOMPLETOS           = "Por favor, complete todos los campos obligatorios.";
        public static final String FORMATO_PROFESIONAL_INVALIDO = "El Profesional debe seguir el formato: Apellido Nombre";
        public static final String FORMATO_PACIENTE_INVALIDO    = "El Paciente debe seguir el formato: Apellido Nombre";
        public static final String DEBE_AGREGAR_MATERIAL        = "Debe agregar al menos un material al equipo.";
        public static final String DEBE_AGREGAR_BOLSA           = "Debe agregar al menos una bolsa.";
        public static final String CODIGO_MATERIAL_DESCONOCIDO  = "Los siguientes códigos de material no existen en el catálogo: %s";

        public static final String ERROR_CONEXION_BD   = "No se pudo conectar con el servidor de base de datos.\n" +
                "Por favor, verifique que la PC Servidor esté encendida.";
        public static final String ERROR_GUARDAR_DATOS = "Error al guardar los datos. Intente nuevamente.";
        public static final String ERROR_CARGAR_DATOS  = "Error al cargar los datos.";

        public static final String DEBE_SELECCIONAR_EQUIPO      = "Debe seleccionar un equipo";
        public static final String DEBE_SELECCIONAR_MATERIAL    = "Debe seleccionar un material";
        public static final String MOTIVO_OBLIGATORIO           = "El motivo es obligatorio";
        public static final String MOTIVO_PROMPT                = "Ingrese el motivo de la eliminación:";
        public static final String OPERACION_MODIFICAR_CANTIDAD = "Modificar Cantidad";
        public static final String OPERACION_MODIFICAR_CODIGO   = "Modificar Código";

        public static final String CAMPO_CLIENTE_OBLIGATORIO     = "El campo Cliente es obligatorio.";
        public static final String CAMPO_INSTITUCION_OBLIGATORIO = "El campo Institución es obligatorio.";
        public static final String CLIENTE_NO_SELECCIONADO       = "Debe seleccionar un cliente de la lista de sugerencias.";
        public static final String INSTITUCION_NO_SELECCIONADA   = "Debe seleccionar una institución de la lista de sugerencias.";
        public static final String PROFESIONAL_NO_SELECCIONADO   = "Si ingresa un profesional, debe seleccionar uno de la lista de sugerencias.";

        public static final String ERROR_GUARDAR_EQUIPO = "Error al guardar el equipo. Por favor, intente de nuevo.";
        public static final String TITULO_ERROR_GUARDAR = "Error al Guardar";

        public static final String AUTOCOMPLETE_DESCONOCIDO      = "Desconocido";
        public static final String NOMBRE_VACIO                  = "El nombre no puede estar vacío";
        public static final String ERROR_GUARDAR_ENTIDAD         = "Error al guardar %s. Por favor, intente de nuevo.";
        public static final String ERROR_GUARDAR_ENTIDAD_DETALLE = "Error al guardar %s: %s";

        public static final String SELECCIONE_MATERIAL_AVANZAR  = "Por favor, seleccione un material para avanzar.";
        public static final String MATERIAL_CAMBIOS_PENDIENTES  = "Este material tiene cambios pendientes. Confirme antes de continuar.";
        public static final String MATERIAL_CAMBIO_PENDIENTE_DUP = "Este material ya tiene un cambio pendiente. Confirme antes de avanzar nuevamente.";
        public static final String MATERIAL_ESTADO_FINAL        = "El material ya está en el estado final: %s";
        public static final String ESTERILIZAR_DESDE_LOTES      = "✓ Para procesar materiales a través de esterilización, use la pantalla de LOTES desde el menú Centro de Esterilización.";
        public static final String CONFIRMAR_CANCELACION        = "¿Está seguro de que desea cancelar todos los cambios pendientes?";
        public static final String CONFIRMAR_CAMBIOS            = "¿Confirmar cambios?\nEsta operación actualizará la base de datos.";
        public static final String CAMBIOS_GUARDADOS_OK         = "Todos los cambios se guardaron correctamente.";
        public static final String CAMBIOS_GUARDADOS_ERROR      = "Algunos cambios no se pudieron guardar:\n%s";
        public static final String ERROR_ACTUALIZAR_EQUIPO_ID   = "- Error al actualizar equipo ID: %d\n";
        public static final String GUARD_REGISTRAR_ESTADO_CAMBIOS = "Tenés cambios sin confirmar. Si volvés ahora, se perderán.\n¿Querés salir de todas formas?";
        public static final String GUARD_LOTES_CAMBIOS          = "Tenés materiales cargados en un equipo de esterilización sin lanzar.\nSi volvés ahora, esos cambios se perderán.\n¿Querés salir de todas formas?";

        public static final String CANTIDAD_AVANZAR_PROMPT = "Cantidad a avanzar para: %s (disponible: %d)";
        public static final String CANTIDAD_AVANZAR_VACIA  = "Ingrese una cantidad válida.";
        public static final String CANTIDAD_AVANZAR_RANGO  = "La cantidad debe estar entre 1 y %d.";
        public static final String CANTIDAD_AVANZAR_NUMERO = "La cantidad debe ser un número entero.";
        public static final String CANTIDAD_AVANZAR_TODOS  = "Todos";

        public static final String CONFIRMAR_ENTREGA_EQUIPO = "¿Marcar este equipo como entregado?\nEsta acción actualizará todos sus materiales.";
        public static final String ENTREGA_EQUIPO_OK        = "Equipo entregado correctamente.";
        public static final String ENTREGA_EQUIPO_ERROR     = "No se pudo marcar el equipo como entregado.";

        public static final String TITULO_EXITO                       = "Éxito";
        public static final String TITULO_ERROR                       = "Error";
        public static final String TITULO_ADVERTENCIA                 = "Advertencia";
        public static final String TITULO_ERROR_CONEXION              = "Error de Conexión";
        public static final String TITULO_CLIENTE_NO_SELECCIONADO     = "Cliente no seleccionado";
        public static final String TITULO_INSTITUCION_NO_SELECCIONADA = "Institución no seleccionada";
        public static final String TITULO_PROFESIONAL_NO_SELECCIONADO = "Profesional no seleccionado";
        public static final String TITULO_CONFIRMAR_CANCELACION       = "Confirmar Cancelación";
        public static final String TITULO_CONFIRMAR_CAMBIOS           = "Confirmar Cambios";
        public static final String TITULO_CAMBIOS_SIN_CONFIRMAR       = "Cambios sin confirmar";
        public static final String TITULO_AVANZAR_SUBCANTIDAD         = "Avanzar subcantidad";
        public static final String TITULO_CONFIRMAR_ENTREGA           = "Confirmar Entrega";
        public static final String TITULO_LANZAR_LOTE                 = "Lanzar Lote";
        public static final String TITULO_FINALIZAR_LOTE              = "Finalizar Lote";

        public static final String INSTRUCCION_CREAR_LOTE         = "Cree un lote para: %s\nArrastre los materiales a un autoclave libre.";
        public static final String FINALIZAR_LOTE_ANTES_CONFIRMAR = "Complete la gestión de lotes antes de confirmar cambios.";
        public static final String SELECCIONE_AUTOCLAVE_LIBRE     = "Seleccione un autoclave libre para lanzar el lote.";
        public static final String SELECCIONE_AUTOCLAVE_CON_LOTE  = "Seleccione un autoclave con lote activo.";

        public static final String LOTE_VACIO                     = "El lote no tiene materiales. Agregue materiales antes de lanzar.";
        public static final String CAPACIDAD_INSUFICIENTE         = "Capacidad insuficiente en autoclave %s. Se necesitan %d litros.";
        public static final String MATERIAL_YA_CARGADO_EN_LOTE   = "El material %s ya está cargado en este lote.";
        public static final String LOTE_CREADO_OK                 = "Lote creado correctamente y lanzado al autoclave.";
        public static final String ERROR_CREAR_LOTE               = "Error al crear el lote. Intente nuevamente.";
        public static final String LOTE_FINALIZADO_OK             = "Lote finalizado correctamente.";
        public static final String ERROR_FINALIZAR_LOTE           = "Error al finalizar el lote. Intente nuevamente.";
        public static final String LOTE_FALLO_OK                  = "Lote marcado como fallido. Los equipos han vuelto a su estado anterior.";
        public static final String ERROR_MARCAR_LOTE_FALLO        = "Error al marcar el lote como fallido. Intente nuevamente.";
        public static final String LOTE_NO_ENCONTRADO             = "No se encontró el lote en el autoclave.";
        public static final String LOTE_NO_ESTERILIZADO           = "El lote aún no ha sido esterilizado. Estado actual: %s";
        public static final String CONFIRMAR_LANZAR_LOTE          = "¿Lanzar este lote al autoclave?";
        public static final String CONFIRMAR_FINALIZAR_LOTE       = "¿Marcar este lote como finalizado?";
        public static final String CONFIRMAR_MARCAR_LOTE_FALLO    = "¿Marcar este lote como fallido? Los equipos volverán a su estado anterior.";

        // ── Remito ────────────────────────────────────────────────────────────
        /** Mensaje de éxito al guardar un remito; incluye el ID generado. */
        public static final String REMITO_GUARDADO_OK = "Remito guardado correctamente.\nIdentificador: %s";

        public static final String GUARD_CICLOS_CAMBIOS      = "Tenés elementos cargados en un lavarropas sin lanzar.\nSi volvés ahora, esos cambios se perderán.\n¿Querés salir de todas formas?";
        public static final String CONFIRMAR_LANZAR_CICLO    = "¿Lanzar el ciclo de lavado?";
        public static final String CONFIRMAR_FINALIZAR_CICLO = "¿Marcar este ciclo como finalizado?";
        public static final String ERROR_LANZAR_CICLO        = "Error al lanzar el ciclo. Intente nuevamente.";
        public static final String ERROR_FINALIZAR_CICLO     = "Error al finalizar el ciclo. Intente nuevamente.";

        private Mensajes() {}
    }

    /**
     * Títulos de las pantallas.
     */
    public static final class Titulos {
        public static final String APP                   = "Sistema Empresa - v1.0";
        public static final String MENU_PRINCIPAL        = "Menú Principal de Gestión";
        public static final String CENTRO_ESTERILIZACION = "CENTRO DE ESTERILIZACIÓN";
        public static final String INGRESO               = "INGRESO";
        public static final String INGRESO_ORTOPEDIA     = "INGRESO ORTOPEDIA";
        public static final String ESTADO_PROCESOS       = "ESTADO DE PROCESOS EN TIEMPO REAL";
        public static final String REGISTRAR_ESTADO      = "REGISTRAR ESTADO";
        public static final String EQUIPOS_PARA_ENTREGAR = "EQUIPOS PARA ENTREGAR";
        public static final String LOTES                 = "LOTES DE ESTERILIZACIÓN";
        public static final String VER_LOTES             = "LOTES FINALIZADOS";
        public static final String INGRESO_OTROS         = "INGRESO OTROS";
        public static final String LAVADERO               = "LAVADERO";
        public static final String INGRESO_LAVADERO       = "INGRESO LAVADERO";
        public static final String CLASIFICACION_LAVADERO = "CLASIFICACIÓN LAVADERO";
        public static final String CICLOS_LAVADERO       = "CICLOS DE LAVADO";
        public static final String AJUSTES              = "Ajustes";

        private Titulos() {}
    }

    /**
     * Etiquetas de botones.
     */
    public static final class Botones {
        public static final String VOLVER                 = "<- Volver";
        public static final String GUARDAR                = "Guardar";
        public static final String ACEPTAR                = "Aceptar";
        public static final String SI                     = "Sí";
        public static final String NO                     = "No";
        public static final String CANCELAR               = "Cancelar";
        public static final String VER                    = "Ver";
        public static final String REGISTRAR              = "Registrar";
        public static final String INGRESAR               = "Ingresar";
        public static final String CLASIFICAR             = "Clasificar";
        public static final String PARA_ENTREGAR          = "Para entregar";
        public static final String LOTES                  = "Lotes";
        public static final String AGREGAR                = "+";
        public static final String ELIMINAR               = "-";
        public static final String AVANZAR_MATERIAL       = "Avanzar Material Seleccionado";
        public static final String CONFIRMAR_GUARDAR      = "Confirmar y Guardar";
        public static final String ENTREGAR_EQUIPO        = "Marcar equipo como entregado";
        public static final String ENTREGAR_INSTITUCION   = "Entregar Institución";
        public static final String LANZAR_LOTE            = "Lanzar Lote";
        public static final String MARCAR_LOTE_FINALIZADO = "Marcar Lote Finalizado";
        public static final String MARCAR_LOTE_FALLO      = "Lote Falló";
        public static final String QUITAR                 = "Quitar";
        public static final String AGREGAR_TEXTO          = "Agregar";
        public static final String ELIMINAR_FILA          = "X";
        public static final String CENTRO_ESTERILIZACION  = "Centro de Esterilización";
        public static final String LAVADERO               = "Lavadero";
        public static final String DESINFECTADORA         = "Desinfectadora";
        public static final String DISTRIBUIDORA          = "Distribuidora";
        public static final String ORTOPEDIA              = "Ortopedia";
        public static final String OTROS                  = "Otros";
        public static final String VER_LOTES              = "Ver Lotes";
        public static final String LIMPIAR_FILTROS        = "Limpiar filtros";
        public static final String IMPRIMIR               = "Imprimir";
        public static final String VER_EQUIPOS            = "Ver equipos";
        public static final String CERRAR                 = "Cerrar";
        public static final String CICLOS          = "Ciclos";
        public static final String LANZAR_CICLO    = "Lanzar Ciclo";
        public static final String FINALIZAR_CICLO = "Finalizar Ciclo";
        public static final String AJUSTES               = "Ajustes";

        private Botones() {}
    }

    /**
     * Configuración de base de datos.
     */
    public static final class BaseDatos {
        public static final String NOMBRE_BD        = "sistema_empresa";
        public static final String TABLA_CATALOGO   = "catalogo_descripciones";
        public static final String TABLA_EQUIPOS    = "equipos";
        public static final String TABLA_MATERIALES = "equipo_materiales";

        public static final String DB_HOST_DEFAULT = "localhost";
        public static final String DB_USER_DEFAULT = "root";
        public static final String DB_PORT         = "3306";
        public static final String DB_TIMEZONE     = "UTC";

        private BaseDatos() {}
    }

    /**
     * Formatos y patrones de validación.
     */
    public static final class Formatos {
        public static final String FORMATO_CODIGO_EQUIPO    = "%d%d";
        public static final String FORMATO_SPINNER_CANTIDAD = "#0.##";
        public static final String FORMATO_FECHA_HORA       = "dd/MM/yyyy HH:mm";

        /** Límite de caracteres para el campo Observaciones del remito. */
        public static final int    REMITO_OBS_MAX_CHARS     = 2000;

        private Formatos() {}
    }

    /**
     * Valores por defecto de la aplicación.
     */
    public static final class Defaults {
        public static final String ESTADO_INICIAL       = "Nuevo";
        public static final String FUENTE_PRINCIPAL     = "Arial";
        public static final int    FUENTE_TAMANO_TITULO = 26;
        public static final int    FUENTE_TAMANO_BOTON  = 24;
        public static final int    FUENTE_TAMANO_LABEL  = 18;
        public static final int    FUENTE_TAMANO_INPUT  = 18;

        private Defaults() {}
    }

    /**
     * Textos reutilizables de UI (labels, cabeceras, placeholders).
     */
    public static final class Textos {
        public static final String CAMBIOS_PENDIENTES                    = "Cambios pendientes: %d";
        public static final String TABLA_EQUIPOS_TITULO                  = "Equipos / Clientes";
        public static final String TABLA_MATERIALES_TITULO               = "Materiales del Equipo (Seleccione para avanzar)";
        public static final String TABLA_MATERIALES_SELECCIONADO_TITULO  = "Materiales del Equipo Seleccionado";
        public static final String TABLA_INSTITUCIONES_TITULO            = "Instituciones con equipos esterilizados";
        public static final String TABLA_MATERIALES_PARA_ENTREGAR_TITULO = "Materiales para entregar";
        public static final String TABLA_MATERIALES_DISPONIBLES          = "Materiales disponibles para esterilizar";
        public static final String TABLA_MATERIALES_AUTOCLAVE            = "Materiales cargados en autoclave";
        public static final String TITULO_GESTIONAR_LOTES                = "Gestión de lotes de esterilización";
        public static final String LISTA_AUTOCLAVES_TITULO               = "Autoclaves";
        public static final String CAPACIDAD_AUTOCLAVE                   = "Capacidad: 0/0";
        public static final String COLUMNA_CLIENTE                       = "Cliente";
        public static final String COLUMNA_INSTITUCION                   = "Institución";
        public static final String COLUMNA_ESTADO                        = "Estado";
        public static final String COLUMNA_EQUIPOS                       = "Equipos";
        public static final String COLUMNA_EQUIPO                        = "Equipo";
        public static final String COLUMNA_MATERIAL                      = "Material";
        public static final String COLUMNA_CANTIDAD                      = "Cantidad";
        public static final String COLUMNA_ENTREGADO                     = "Entregado";
        public static final String COLUMNA_ULTIMO_MOVIMIENTO             = "Último movimiento";
        public static final String TOTAL_ELEMENTOS                       = "Total Elementos: %d";
        public static final String CODIGO_INVALIDO                       = "Código inválido";
        public static final String SIN_MOVIMIENTO                        = "-";
        public static final String SIN_INSTITUCION                       = "Sin institucion";
        public static final String ENTREGADO_SI                          = "SI";
        public static final String ENTREGADO_NO                          = "NO";
        public static final String TOOLTIP_ELIMINAR_FILA                 = "Eliminar esta fila";
        public static final String LABEL_CLIENTE                         = "Cliente / Empresa:";
        public static final String LABEL_PROFESIONAL                     = "Profesional a cargo:";
        public static final String LABEL_PACIENTE                        = "Nombre del Paciente:";
        public static final String LABEL_INSTITUCION                     = "Institución:";
        public static final String LABEL_MATERIAL                        = "Material:";
        public static final String CHECK_REQUIERE_LAVADO                 = "Requiere lavado";
        public static final String CHECK_REQUIERE_EMPAQUE                = "Requiere empaquetado";
        public static final String AYUDA_FORMATO_APELLIDO_NOMBRE         = "Formato: Apellido Nombre";
        public static final String DIALOG_TITULO_AGREGAR                 = "Agregar nuevo/a %s";
        public static final String DIALOG_MENSAJE_AGREGAR                = "<html>El/la %s no existe.<br>¿Desea agregarlo/la a la base de datos?</html>";
        public static final String LABEL_NOMBRE                          = "Nombre:";
        public static final String EJEMPLO_CAJA_INSTRUMENTAL             = "Caja de Instrumental #102";
        public static final String EJEMPLO_SET_CIRUGIA                   = "Set de Cirugía Menor #05";
        public static final String ENTIDAD_CLIENTE                       = "Cliente";
        public static final String ENTIDAD_PROFESIONAL                   = "Profesional";
        public static final String ENTIDAD_INSTITUCION                   = "Institución";
        public static final String ENTIDAD_CATALOGO_OTROS                = "material del catálogo";
        public static final String BOTON_PASAR_A                         = "Pasar a %s";
        public static final String BOTON_SELECCIONE_MATERIAL             = "Seleccione un material";
        public static final String BOTON_ESTADO_FINAL                    = "Material en estado final";
        public static final String BOTON_ESTERILIZAR_DESDE_LOTES         = "Esterilizar desde Lotes";
        public static final String COLUMNA_LOTE_ID                       = "ID";
        public static final String COLUMNA_LOTE_EQUIPO                   = "Equipo";
        public static final String COLUMNA_LOTE_CAPACIDAD_USADA          = "Capacidad Usada";
        public static final String COLUMNA_LOTE_INICIO                   = "Inició";
        public static final String COLUMNA_LOTE_FIN                      = "Finalizó";
        public static final String COLUMNA_LOTE_ESTADO                   = "Estado";
        public static final String FILTRO_TODOS                          = "Todos";
        public static final String FILTRO_CLIENTE                        = "Cliente:";
        public static final String FILTRO_INSTITUCION                    = "Institución:";
        public static final String FILTRO_ID                             = "ID:";
        public static final String FILTRO_EQUIPO                         = "Equipo:";
        public static final String FILTRO_ESTADO                         = "Estado:";
        public static final String FILTRO_FECHA_INICIO                   = "Fecha inicio:";

        // ── Remito ────────────────────────────────────────────────────────────
        /** Label del selector de modalidad de ingreso. */
        public static final String LABEL_TIPO_INGRESO  = "Tipo de ingreso:";
        /** Texto del radio button para el modo Remito. */
        public static final String RADIO_REMITO        = "Remito";
        /** Texto del radio button para el modo Detalles. */
        public static final String RADIO_DETALLES      = "Detalles";
        /** Label del campo identificador de remito (readonly). */
        public static final String LABEL_REMITO_ID     = "N° de Remito:";
        /** Label del spinner de cantidad en el modo Remito. */
        public static final String LABEL_REMITO_CANTIDAD = "Cantidad:";
        /** Label del área de observaciones del remito. */
        public static final String LABEL_OBSERVACIONES = "Observaciones:";
        /** Tooltip del campo readonly de remito. */
        public static final String TOOLTIP_REMITO_ID   = "Generado automáticamente al guardar: fecha-id";

        private Textos() {}
    }
}