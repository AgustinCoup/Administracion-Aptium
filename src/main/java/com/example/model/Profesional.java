package com.example.model;

import java.util.Objects;

/**
 * Representa un profesional médico en el sistema.
 * Usado para autocompletado en formularios de ingreso de equipos.
 */
public class Profesional implements Autocompletable {
    
    private Integer id;
    private String nombre;
    
    public Profesional() {
    }
    
    public Profesional(Integer id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getNombre() {
        return nombre;
    }
    
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    
    /**
     * toString() devuelve el nombre para que la JList lo muestre correctamente.
     */
    @Override
    public String toString() {
        return nombre;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profesional that = (Profesional) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
