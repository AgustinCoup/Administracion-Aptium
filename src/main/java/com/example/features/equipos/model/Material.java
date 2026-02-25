package com.example.features.equipos.model;

import java.time.LocalDateTime;

public class Material {
    private Integer id;           // ID de lote en BD (puede ser null para temporales)
    private int codigo;           // Ejemplo: 400
    private String descripcion;   // Traído de tu tabla catalogo_descripciones
    private int cantidad;
    private EstadoEquipo estado;  // Estado del material individual
    private LocalDateTime ultimoMovimiento;

    public Material(int codigo, String descripcion, int cantidad) {
        this(null, codigo, descripcion, cantidad, EstadoEquipo.NUEVO, null);
    }

    // Constructor completo sin ID
    public Material(int codigo, String descripcion, int cantidad, EstadoEquipo estado) {
        this(null, codigo, descripcion, cantidad, estado, null);
    }

    // Constructor completo con ID
    public Material(Integer id, int codigo, String descripcion, int cantidad, EstadoEquipo estado) {
        this(id, codigo, descripcion, cantidad, estado, null);
    }

    public Material(Integer id, int codigo, String descripcion, int cantidad, EstadoEquipo estado,
                    LocalDateTime ultimoMovimiento) {
        this.id = id;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.estado = estado;
        this.ultimoMovimiento = ultimoMovimiento;
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public int getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public EstadoEquipo getEstado() { return estado; }
    public void setEstado(EstadoEquipo estado) { this.estado = estado; }
    public LocalDateTime getUltimoMovimiento() { return ultimoMovimiento; }
    public void setUltimoMovimiento(LocalDateTime ultimoMovimiento) { this.ultimoMovimiento = ultimoMovimiento; }

    public boolean esPersistido() {
        return id != null;
    }
}


