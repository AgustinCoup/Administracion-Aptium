package com.example.view;

import javax.swing.*;
import javax.swing.border.Border;

import java.awt.*;

/**
 * Clase centralizada de estilos visuales para toda la aplicación.
 * Define fuentes, bordes, márgenes y dimensiones reutilizables.
 * 
 * Evita la duplicación de código de estilo y facilita cambios globales de diseño.
 * Al modificar un estilo aquí, afecta automáticamente a toda la aplicación.
 */
public final class Estilos {
    
    // Prevenir instanciación
    private Estilos() {
        throw new UnsupportedOperationException("Clase de estilos no instanciable");
    }
    
    /**
     * FUENTES - Predefinidas para mantener consistencia visual
     */
    public static final class Fuentes {
        private static final String FUENTE_PRINCIPAL = "Arial";
        
        // Títulos de pantallas (grande y bold)
        public static final Font TITULO = new Font(FUENTE_PRINCIPAL, Font.BOLD, 26);
        
        // Bienvenida y encabezados principales
        public static final Font TITULO_SECUNDARIO = new Font(FUENTE_PRINCIPAL, Font.BOLD, 24);
        
        // Labels de formularios
        public static final Font LABEL = new Font(FUENTE_PRINCIPAL, Font.BOLD, 18);
        
        // Botones estándar
        public static final Font BOTON = new Font(FUENTE_PRINCIPAL, Font.PLAIN, 24);
        
        // Botones pequeños (+ / -)
        public static final Font BOTON_PEQUENO = new Font(FUENTE_PRINCIPAL, Font.BOLD, 18);
        
        // Campos de entrada (TextFields, etc)
        public static final Font INPUT = new Font(FUENTE_PRINCIPAL, Font.PLAIN, 18);
        
        // Encabezados de tabla
        public static final Font TABLA_ENCABEZADO = new Font(FUENTE_PRINCIPAL, Font.BOLD, 16);
        
        // Contenido de tabla
        public static final Font TABLA_CONTENIDO = new Font(FUENTE_PRINCIPAL, Font.PLAIN, 14);
        
        // Contenido de tabla con énfasis (estado, etc)
        public static final Font TABLA_ENFASIS = new Font(FUENTE_PRINCIPAL, Font.BOLD, 14);
        
        // Texto pequeño de ayuda o aclaraciones
        public static final Font TEXTO_AYUDA = new Font(FUENTE_PRINCIPAL, Font.ITALIC, 10);
        
        private Fuentes() {}
    }
    
    /**
     * BORDES Y ESPACIADOS - Márgenes consistentes para paneles
     */
    public static final class Espaciados {
        
        // Borde principal para paneles de botones
        public static final Border BORDE_PRINCIPAL = 
            BorderFactory.createEmptyBorder(20, 50, 50, 50);
        
        // Borde para encabezados y títulos
        public static final Border BORDE_TITULO = 
            BorderFactory.createEmptyBorder(20, 0, 20, 0);
        
        // Borde para formularios
        public static final Border BORDE_FORMULARIO = 
            BorderFactory.createEmptyBorder(30, 50, 30, 50);
        
        // Borde para paneles secundarios
        public static final Border BORDE_SECUNDARIO = 
            BorderFactory.createEmptyBorder(5, 0, 5, 10);
        
        // Márgenes interiores de TextFields y componentes de entrada
        public static final Insets INSETS_INPUT = new Insets(2, 6, 2, 6);
        
        // Márgenes para botones en paneles compactos
        public static final Border BORDE_BOTONES = 
            BorderFactory.createEmptyBorder(20, 50, 50, 50);
        
        private Espaciados() {}
    }
    
    /**
     * DIMENSIONES - Tamaños predefinidos para componentes
     */
    public static final class Dimensiones {
        
        // Altura estándar de campos de entrada basada en fuente
        public static int calcularAlturaInput() {
            FontMetrics fm = new JLabel().getFontMetrics(Fuentes.INPUT);
            return fm.getHeight() + 6;
        }
        
        // Altura de filas en tablas
        public static final int ALTURA_FILA_TABLA = 30;
        
        // Ancho de un TextFields pequeño (para números)
        public static int calcularAnchoNumero(int caracteres) {
            FontMetrics fm = new JLabel().getFontMetrics(Fuentes.INPUT);
            return fm.charWidth('0') * caracteres + 12;
        }
        
        private Dimensiones() {}
    }
    
    /**
     * COLORES - Colores reutilizables de la aplicación
     */
    public static final class Colores {
        
        // Color de texto normal
        public static final Color TEXTO_NORMAL = Color.BLACK;
        
        // Color de texto ayuda/hint
        public static final Color TEXTO_AYUDA = Color.GRAY;
        
        // Fondo por defecto
        public static final Color FONDO_DEFECTO = Color.WHITE;
        
        private Colores() {}
    }
}
