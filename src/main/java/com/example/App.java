package com.example;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.example.database.Conexion;
import com.example.view.PantallaPrincipal;

import javax.swing.JOptionPane;
import java.sql.Connection;

public class App {
    public static void main(String[] args) {
        // 1. Verificamos la conexión antes de abrir la ventana
        Connection pruebaConn = Conexion.conectar();
        
        if (pruebaConn != null) {
            // Si la conexión es exitosa, cerramos la prueba y abrimos la interfaz
            try { pruebaConn.close(); } catch (Exception e) {}
            
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            SwingUtilities.invokeLater(() -> {
                new PantallaPrincipal().setVisible(true);
            });
        } else {
            // Si falla la conexión, avisamos al usuario con un cartel profesional
            JOptionPane.showMessageDialog(null, 
                "No se pudo conectar con el servidor de base de datos.\n" +
                "Por favor, verifique que la PC Servidor esté encendida.", 
                "Error de Conexión", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(0); // Cerramos el programa porque no puede funcionar sin DB
        }
    }
}