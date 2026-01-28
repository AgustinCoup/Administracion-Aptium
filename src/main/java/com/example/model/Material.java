package com.example.model;

public class Material {
    private int codigo;           // Ejemplo: 400
    private String descripcion;   // Traído de tu tabla catalogo_descripciones
    private int cantidad;
    private EstadoEquipo estado;  // Estado del material individual

    public Material(int codigo, String descripcion, int cantidad) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.estado = EstadoEquipo.NUEVO; // Estado inicial por defecto
    }

    // Constructor completo
    public Material(int codigo, String descripcion, int cantidad, EstadoEquipo estado) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.estado = estado;
    }

    // Getters y Setters
    public int getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public int getCantidad() { return cantidad; }
    public EstadoEquipo getEstado() { return estado; }
    public void setEstado(EstadoEquipo estado) { this.estado = estado; }
}