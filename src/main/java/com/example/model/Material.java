package com.example.model;

public class Material {
    private String idRelacionado; // Ejemplo: "20263-400"
    private int codigo;           // Ejemplo: 400
    private String descripcion;   // Traído de tu tabla catalogo_descripciones
    private int cantidad;

    public Material(String idRelacionado, int codigo, String descripcion, int cantidad) {
        this.idRelacionado = idRelacionado;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
    }

    // Getters y Setters
    public String getIdRelacionado() { return idRelacionado; }
    public int getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public int getCantidad() { return cantidad; }
}