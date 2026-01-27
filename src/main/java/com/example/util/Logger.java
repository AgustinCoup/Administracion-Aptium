package com.example.util;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Clase Logger centralizada para registrar eventos, errores e información de la aplicación.
 * Proporciona métodos para diferentes niveles de severidad.
 * 
 * Uso: Logger.info("Mensaje"), Logger.error("Error", excepción)
 */
public class Logger {
    
    private static final String LOG_FILE = "app_logs.txt";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final boolean ESCRIBIR_EN_ARCHIVO = true; // Cambiar a false si solo quieres consola
    
    // Niveles de logging (constantes)
    private static final String DEBUG = "DEBUG";
    private static final String INFO = "INFO";
    private static final String WARNING = "WARNING";
    private static final String ERROR = "ERROR";
    private static final String FATAL = "FATAL";
    
    /**
     * Registra un mensaje DEBUG (información detallada para debugging).
     */
    public static void debug(String mensaje) {
        registrar(DEBUG, mensaje, null);
    }
    
    /**
     * Registra un mensaje DEBUG con excepción.
     */
    public static void debug(String mensaje, Throwable excepcion) {
        registrar(DEBUG, mensaje, excepcion);
    }
    
    /**
     * Registra un mensaje INFO (eventos importantes).
     */
    public static void info(String mensaje) {
        registrar(INFO, mensaje, null);
    }
    
    /**
     * Registra un mensaje INFO con excepción.
     */
    public static void info(String mensaje, Throwable excepcion) {
        registrar(INFO, mensaje, excepcion);
    }
    
    /**
     * Registra un mensaje WARNING (algo inesperado pero no grave).
     */
    public static void warning(String mensaje) {
        registrar(WARNING, mensaje, null);
    }
    
    /**
     * Registra un mensaje WARNING con excepción.
     */
    public static void warning(String mensaje, Throwable excepcion) {
        registrar(WARNING, mensaje, excepcion);
    }
    
    /**
     * Registra un mensaje ERROR (error que necesita atención).
     */
    public static void error(String mensaje) {
        registrar(ERROR, mensaje, null);
    }
    
    /**
     * Registra un mensaje ERROR con excepción.
     */
    public static void error(String mensaje, Throwable excepcion) {
        registrar(ERROR, mensaje, excepcion);
    }
    
    /**
     * Registra un mensaje FATAL (error crítico).
     */
    public static void fatal(String mensaje) {
        registrar(FATAL, mensaje, null);
    }
    
    /**
     * Registra un mensaje FATAL con excepción.
     */
    public static void fatal(String mensaje, Throwable excepcion) {
        registrar(FATAL, mensaje, excepcion);
    }
    
    /**
     * Método privado que centraliza toda la lógica de logging.
     */
    private static void registrar(String nivel, String mensaje, Throwable excepcion) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String linea = String.format("[%s] %s: %s", timestamp, nivel, mensaje);
        
        // Imprime en consola
        System.out.println(linea);
        
        // Si hay excepción, imprime el stack trace
        if (excepcion != null) {
            excepcion.printStackTrace();
        }
        
        // Opcionalmente escribe en archivo
        if (ESCRIBIR_EN_ARCHIVO) {
            escribirEnArchivo(linea, excepcion);
        }
    }
    
    /**
     * Escribe el log en un archivo para registro permanente.
     */
    private static void escribirEnArchivo(String linea, Throwable excepcion) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(linea + "\n");
            
            if (excepcion != null) {
                fw.write("Stack Trace: " + excepcion.toString() + "\n");
                for (StackTraceElement elemento : excepcion.getStackTrace()) {
                    fw.write("  at " + elemento + "\n");
                }
            }
            fw.write("\n");
            
        } catch (IOException e) {
            // Si falla escribir en archivo, solo imprime en consola
            System.err.println("No se pudo escribir en archivo de logs: " + e.getMessage());
        }
    }
}
