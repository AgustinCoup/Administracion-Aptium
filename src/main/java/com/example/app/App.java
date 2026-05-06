package com.example.app;

import com.example.app.ui.AppController;
import com.example.infrastructure.db.ConnectionPool;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal de la aplicación.
 * 
 * ARQUITECTURA CON DEPENDENCY INJECTION:
 * 1. AppContext arma DAOs y Services
 * 2. AppModel recibe dependencias
 * 3. AppController recibe AppModel
 *
 * STARTUP SEQUENCE:
 * 1. Registrar shutdown hook (cierre graceful)
 * 2. Inicializar Connection Pool (conectar a BD)
 * 3. Inicializar esquema BD (crear tablas si faltan)
 * 4. Crear AppContext (DAOs y Services)
 * 5. Crear AppModel (lógica de negocio)
 * 6. Crear AppController (UI)
 * 7. Iniciar aplicación
 *
 * ERROR HANDLING:
 * - Errores de BD: Mostrar diálogo de error
 * - Errores de contexto: Mostrar diálogo de error
 * - Errores de UI: Capturar en AppController
 *
 * En proyectos más grandes usarías un framework DI (Spring, Guice).
 */
public class App {
    
    private static final Logger log = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) {
        // Handler global para excepciones no manejadas (incluyendo las del EDT de Swing)
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Excepción no manejada en hilo '{}'", thread.getName(), throwable);
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null,
                    "Ocurrió un error inesperado. Intentá de nuevo.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE)
            );
        });

        try {
            // ==================== PASO 1: SHUTDOWN HOOK ====================
            // Registrar shutdown hook para cerrar el Connection Pool correctamente
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Cerrando aplicación...");
                try {
                    ConnectionPool.shutdown();
                } catch (Exception e) {
                    log.error("Error al cerrando Connection Pool", e);
                }
            }));
            
            log.info("╔════════════════════════════════════════════════════════════╗");
            log.info("║  Iniciando Administración de Aptium                        ║");
            log.info("║  Versión 1.0 - PRODUCTION READY                           ║");
            log.info("╚════════════════════════════════════════════════════════════╝");
            
            // ==================== PASO 2: INICIALIZACIÓN DE BASE DE DATOS ====================
            log.info("PASO 1/4: Conectando a base de datos...");
            // ConnectionPool se inicializa automáticamente al cargar la clase
            // Valida configuración y abre el pool
            log.info("✓ Connection Pool inicializado");
            log.info(ConnectionPool.getStats());
            
            // ==================== PASO 3: ESQUEMA BD ====================
            log.info("PASO 2/4: Inicializando esquema de base de datos...");
            try {
                ConnectionPool.inicializarEsquema();
                log.info("✓ Esquema BD verificado/creado");
            } catch (Exception e) {
                log.error("✗ Error inicializando esquema BD", e);
                mostrarErrorYSalir("Error en Base de Datos",
                    "No se pudo inicializar el esquema de la base de datos:\n" +
                    e.getMessage() + "\n\nVerifica que MySQL está accesible y configurado.");
                return;
            }
            
            // ==================== PASO 4: CONTEXTO Y DEPENDENCIAS ====================
            log.info("PASO 3/4: Creando contexto de dependencias...");
            AppContext context = null;
            try {
                context = AppContext.createDefault();
                log.info("✓ Contexto creado con DAOs y Services");
            } catch (Exception e) {
                log.error("✗ Error creando contexto", e);
                mostrarErrorYSalir("Error de Contexto",
                    "No se pudo inicializar las dependencias:\n" +
                    e.getMessage());
                return;
            }
            
            AppModel model = null;
            try {
                model = new AppModel(context);
                log.info("✓ AppModel creado");
            } catch (Exception e) {
                log.error("✗ Error creando AppModel", e);
                mostrarErrorYSalir("Error del Modelo",
                    "No se pudo crear el modelo de la aplicación:\n" +
                    e.getMessage());
                return;
            }
            
            // ==================== PASO 5: UI ====================
            log.info("PASO 4/4: Iniciando interfaz de usuario...");
            AppController controller = null;
            try {
                controller = new AppController(model);
                log.info("✓ AppController creado");
            } catch (Exception e) {
                log.error("✗ Error creando AppController", e);
                mostrarErrorYSalir("Error de UI",
                    "No se pudo crear la interfaz de usuario:\n" +
                    e.getMessage());
                return;
            }
            
            // ==================== PASO 6: INICIAR APLICACIÓN ====================
            try {
                log.info("═══════════════════════════════════════════════════════════");
                log.info("Aplicación inicializada correctamente");
                log.info("═══════════════════════════════════════════════════════════");
                controller.iniciarAplicacion();
            } catch (Exception e) {
                log.error("✗ Error durante ejecución de la aplicación", e);
                mostrarErrorYSalir("Error de Ejecución",
                    "Error durante la ejecución de la aplicación:\n" +
                    e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("✗ ERROR CRÍTICO: Fallo no previsto en startup", e);
            mostrarErrorYSalir("Error Crítico",
                "Ha ocurrido un error inesperado:\n" +
                e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Muestra un diálogo de error y termina la aplicación.
     * Usado durante startup si algo falla críticamente.
     */
    private static void mostrarErrorYSalir(String titulo, String mensaje) {
        log.error("╔════════════════════════════════════════════════════════════╗");
        log.error("║  ✗ ERROR DE STARTUP                                       ║");
        log.error("╚════════════════════════════════════════════════════════════╝");
        log.error(mensaje);
        
        try {
            // Mostrar diálogo al usuario
            JOptionPane.showMessageDialog(
                null,
                mensaje + "\n\nLa aplicación se cerrará.",
                titulo,
                JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            log.error("No se pudo mostrar diálogo", e);
        }
        
        // Terminar con código de error
        System.exit(1);
    }
}


