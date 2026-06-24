package com.example.features.equipos.otros.model;

import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;

import java.time.LocalDateTime;

/**
 * Representa un material perteneciente a un {@link EquipoOtros}.
 *
 * A diferencia de {@link com.example.features.equipos.ortopedias.model.Material}, no tiene
 * un código numérico de catálogo: se identifica por su descripción libre.
 * Al guardar, se crea o reutiliza una entrada en {@code catalogo_otros}.
 */
public class MaterialOtros implements MaterialRegistrableInterface {

    private Integer id;                // PK en equipo_otros_materiales (null si no persistido)
    private Integer catalogoOtrosId;   // FK a catalogo_otros; null si la entrada aún no existe
    private String  descripcion;       // Texto libre ingresado por el usuario
    private int     cantidad;
    private EstadoEquipo estado;
    private LocalDateTime ultimoMovimiento;
    private String loteIdNegocio;

    // ── Constructores ──────────────────────────────────────────────────────────

    /** Constructor para uso en el formulario (no persistido aún). */
    public MaterialOtros(String descripcion, int cantidad) {
        this(null, null, descripcion, cantidad, EstadoEquipo.NUEVO, null);
    }

    /** Constructor completo, sin ID de BD. */
    public MaterialOtros(Integer catalogoOtrosId, String descripcion,
                         int cantidad, EstadoEquipo estado) {
        this(null, catalogoOtrosId, descripcion, cantidad, estado, null);
    }

    /** Constructor completo con todos los campos. */
    public MaterialOtros(Integer id, Integer catalogoOtrosId, String descripcion,
                         int cantidad, EstadoEquipo estado,
                         LocalDateTime ultimoMovimiento) {
        this.id               = id;
        this.catalogoOtrosId  = catalogoOtrosId;
        this.descripcion      = descripcion;
        this.cantidad         = cantidad;
        this.estado           = estado != null ? estado : EstadoEquipo.NUEVO;
        this.ultimoMovimiento = ultimoMovimiento;
    }

    // ── IMaterialRegistrable ──────────────────────────────────────────────────

    @Override public Integer      getId()                { return id; }
    @Override public String       getDescripcion()       { return descripcion; }
    @Override public int          getCantidad()          { return cantidad; }
    @Override public void         setCantidad(int c)     { this.cantidad = c; }
    @Override public EstadoEquipo getEstado()            { return estado; }
    @Override public void         setEstado(EstadoEquipo e) { this.estado = e; }
    @Override public boolean      esPersistido()         { return id != null; }
    @Override public LocalDateTime getUltimoMovimiento() { return ultimoMovimiento; }
    @Override public void setUltimoMovimiento(LocalDateTime t) { this.ultimoMovimiento = t; }

    // ── Getters / Setters propios ─────────────────────────────────────────────

    public void    setId(Integer id)                    { this.id = id; }
    public Integer getCatalogoOtrosId()                  { return catalogoOtrosId; }
    public void    setCatalogoOtrosId(Integer catalogoOtrosId) { this.catalogoOtrosId = catalogoOtrosId; }
    public void    setDescripcion(String descripcion)   { this.descripcion = descripcion; }
    public String  getLoteIdNegocio()                   { return loteIdNegocio; }
    public void    setLoteIdNegocio(String lote)        { this.loteIdNegocio = lote; }
}