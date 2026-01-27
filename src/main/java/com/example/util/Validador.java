package com.example.util;

import java.util.regex.Pattern;
import javax.swing.JTextField;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import com.example.constants.Constantes;

/**
 * Clase centralizada de validaciones para la aplicación.
 * Encapsula todas las reglas de validación de negocio.
 * 
 * Diseñada para ser reutilizada en múltiples formularios y controladores.
 * Las validaciones son estáticas para facilitar el acceso desde cualquier lugar.
 */
public final class Validador {
    
    // Prevenir instanciación
    private Validador() {
        throw new UnsupportedOperationException("Clase de validador no instanciable");
    }
    
    // Patrones precompilados para mejor rendimiento
    private static final Pattern PATRON_NOMBRE = 
        Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚñÑ]+(?:\\s+[a-zA-ZáéíóúÁÉÍÓÚñÑ]+)+$");
    
    /**
     * Valida que un campo de texto no esté vacío.
     * 
     * @param texto Texto a validar
     * @return true si el texto no está vacío (después de trim)
     */
    public static boolean noEstaVacio(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }
    
    /**
     * Valida que un nombre siga el formato "Apellido Nombre".
     * Permite letras con acentos, ñ y espacios entre palabras.
     * Requiere al menos dos palabras separadas por espacios.
     * 
     * @param nombre Nombre a validar
     * @return true si sigue el formato "Apellido Nombre"
     */
    public static boolean esFormatoNombre(String nombre) {
        if (!noEstaVacio(nombre)) {
            return false;
        }
        return PATRON_NOMBRE.matcher(nombre).matches();
    }
    
    /**
     * Valida que un campo de correo electrónico sea válido.
     * Validación básica que verifica formato estándar.
     * 
     * @param email Correo a validar
     * @return true si tiene formato válido de email
     */
    public static boolean esEmailValido(String email) {
        if (!noEstaVacio(email)) {
            return false;
        }
        String patronEmail = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(patronEmail);
    }
    
    /**
     * Valida que un valor sea un número entero positivo.
     * 
     * @param valor Valor a validar
     * @return true si es un número entero positivo
     */
    public static boolean esNumeroPositivo(String valor) {
        if (!noEstaVacio(valor)) {
            return false;
        }
        try {
            int num = Integer.parseInt(valor.trim());
            return num > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Valida que un valor sea un número decimal positivo.
     * 
     * @param valor Valor a validar
     * @return true si es un número decimal positivo
     */
    public static boolean esDecimalPositivo(String valor) {
        if (!noEstaVacio(valor)) {
            return false;
        }
        try {
            double num = Double.parseDouble(valor.trim());
            return num > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Valida que un teléfono sea válido.
     * Acepta formatos: 1234567890 o +34123456789 o 123-456-7890
     * 
     * @param telefono Teléfono a validar
     * @return true si tiene formato válido
     */
    public static boolean esTelefonoValido(String telefono) {
        if (!noEstaVacio(telefono)) {
            return false;
        }
        // Acepta números, +, y guiones
        String patronTelefono = "^[+]?[0-9\\-\\s]{9,}$";
        return telefono.matches(patronTelefono);
    }
    
    /**
     * Valida que una URL sea válida.
     * 
     * @param url URL a validar
     * @return true si tiene formato válido de URL
     */
    public static boolean esURLValida(String url) {
        if (!noEstaVacio(url)) {
            return false;
        }
        try {
            new java.net.URL(url);
            return true;
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }
    
    /**
     * Valida la longitud de un texto.
     * 
     * @param texto Texto a validar
     * @param minimo Longitud mínima (inclusiva)
     * @param maximo Longitud máxima (inclusiva)
     * @return true si la longitud está dentro del rango
     */
    public static boolean longitudValida(String texto, int minimo, int maximo) {
        if (!noEstaVacio(texto)) {
            return false;
        }
        int longitud = texto.trim().length();
        return longitud >= minimo && longitud <= maximo;
    }
    
    /**
     * Aplica una restricción a un JTextField para que solo acepte números.
     * Se ejecuta en tiempo de escritura (KeyListener).
     * 
     * @param campo JTextField al que se le aplicará la restricción
     */
    public static void aplicarSoloNumeros(JTextField campo) {
        campo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE) {
                    e.consume(); // Ignora la tecla si no es número
                }
            }
        });
    }
    
    /**
     * Aplica una restricción a un JTextField para que solo acepte letras y espacios.
     * Se ejecuta en tiempo de escritura (KeyListener).
     * Permite letras con acentos, ñ, Ñ y espacios.
     * 
     * @param campo JTextField al que se le aplicará la restricción
     */
    public static void aplicarSoloLetrasYEspacios(JTextField campo) {
        campo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                // Permitir: letras, espacios, y backspace
                if (!Character.isLetter(c) && c != ' ' && c != KeyEvent.VK_BACK_SPACE) {
                    e.consume(); // Ignora la tecla si no es letra ni espacio
                }
            }
        });
    }
    
    /**
     * Valida que un texto contenga solo letras y espacios.
     * 
     * @param texto Texto a validar
     * @return true si contiene solo letras y espacios
     */
    public static boolean soloLetrasYEspacios(String texto) {
        if (!noEstaVacio(texto)) {
            return false;
        }
        return texto.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$");
    }
    
    /**
     * Valida que un texto contenga solo números.
     * 
     * @param texto Texto a validar
     * @return true si contiene solo números
     */
    public static boolean soloNumeros(String texto) {
        if (!noEstaVacio(texto)) {
            return false;
        }
        return texto.matches("^[0-9]+$");
    }
}
