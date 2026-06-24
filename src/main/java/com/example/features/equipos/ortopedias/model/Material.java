package com.example.features.equipos.ortopedias.model;

import com.example.common.model.MaterialRegistrableInterface;
import java.time.LocalDateTime;

public class Material implements MaterialRegistrableInterface {
    private Integer id;           // ID de lote en BD (puede ser null para temporales)
    private int codigo;           // Ejemplo: 400
    private String descripcion;   // Traído de tu tabla catalogo_descripciones
    private int cantidad;
    private EstadoEquipo estado;  // Estado del material individual
    private LocalDateTime ultimoMovimiento;
    private String loteIdNegocio;

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

    // ── MaterialRegistrableInterface ──────────────────────────────────────────

    @Override public Integer       getId()                    { return id; }
    @Override public String        getDescripcion()           { return descripcion; }
    @Override public int           getCantidad()              { return cantidad; }
    @Override public void          setCantidad(int cantidad)  { this.cantidad = cantidad; }
    @Override public EstadoEquipo  getEstado()                { return estado; }
    @Override public void          setEstado(EstadoEquipo e)  { this.estado = e; }
    @Override public boolean       esPersistido()             { return id != null; }
    @Override public LocalDateTime getUltimoMovimiento()      { return ultimoMovimiento; }
    @Override public void setUltimoMovimiento(LocalDateTime t){ this.ultimoMovimiento = t; }

    // ── Getters propios de ortopedia (no en la interfaz) ─────────────────────

    public void setId(Integer id)   { this.id = id; }
    public int  getCodigo()         { return codigo; }
    public String getLoteIdNegocio()              { return loteIdNegocio; }
    public void   setLoteIdNegocio(String lote)   { this.loteIdNegocio = lote; }
}