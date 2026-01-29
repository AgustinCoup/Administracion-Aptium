package com.example.model;

/**
 * Representa un cliente del sistema.
 * Este modelo se usa para el autocompletado en el formulario de ingreso.
 */
public class Cliente {
    private int id;
    private String nombre;

    public Cliente() {
    }

    public Cliente(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String toString() {
        // Este método se usa para mostrar el cliente en el JList del autocompletado
        return nombre;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Cliente cliente = (Cliente) obj;
        return id == cliente.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
