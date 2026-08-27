package com.example.common.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Clase centralizada de validaciones para la aplicación.
 * Encapsula todas las reglas de validación de negocio.
 *
 * Diseñada para ser reutilizada en múltiples formularios y controladores.
 * Las validaciones son estáticas para facilitar el acceso desde cualquier lugar.
 *
 * <p>Reglas puras: no depende de Swing ni de ninguna capa externa. Las
 * restricciones de tecleo sobre widgets viven en
 * {@code com.example.ui.common.RestriccionesCampo}.
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

    /**
     * Retorna el conjunto de valores que aparecen más de una vez en la colección.
     * Ignora strings vacíos. No modifica la colección original.
     */
    public static Set<String> detectarDuplicados(Collection<String> valores) {
        Map<String, Long> freq = valores.stream()
            .filter(v -> v != null && !v.isEmpty())
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        return freq.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }
}


