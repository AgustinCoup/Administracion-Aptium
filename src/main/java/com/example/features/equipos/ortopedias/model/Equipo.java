package com.example.features.equipos.ortopedias.model;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.example.common.constants.Constantes;

public class Equipo implements EquipoRegistrableInterface {
    // Identificación de Base de Datos
    private Integer id;

    // Datos del Cliente
    private int nroCliente;
    private String clienteNombre;

    // Datos Médicos
    private Integer nroProfesional;
    private String profesionalNombre;
    private String pacienteNombre;
    private Integer nroInstitucion;
    private String institucionNombre;

    // Estado y Fecha
    private EstadoEquipo estado;
    private List<Material> materiales;
    private boolean requiereLavado;
    private boolean requiereEmpaque;

    // Constructor
    public Equipo() {
        this.materiales = new ArrayList<>();
        this.estado = EstadoEquipo.NUEVO;
        this.requiereLavado = true;
        this.requiereEmpaque = true;
    }

    public void agregarMaterial(Material material) {
        this.materiales.add(material);
    }

    // ── IEquipoRegistrable ────────────────────────────────────────────────────

    @Override public TipoEquipo  getTipo()                    { return TipoEquipo.ORTOPEDIA; }
    @Override public Integer     getId()                      { return id; }
    @Override public String      getClienteNombre()           { return clienteNombre; }
    @Override public int         getNroCliente()              { return nroCliente; }
    @Override public String      getDescripcionSecundaria()   { return institucionNombre != null ? institucionNombre : ""; }
    @Override public EstadoEquipo getEstado()                 { return estado; }
    @Override public void         setEstado(EstadoEquipo e)   { this.estado = e; }
    @Override public boolean      isRequiereLavado()          { return requiereLavado; }
    @Override public boolean      isRequiereEmpaque()         { return requiereEmpaque; }

    @Override
    public List<MaterialRegistrableInterface> getMaterialesRegistrables() {
        return Collections.unmodifiableList(materiales);
    }

    /**
     * Aplica un movimiento de subcantidad en memoria para previsualizacion.
     * El cast a Material es seguro porque Equipo solo contiene Material.
     */
    @Override
    public void aplicarMovimientoPreview(MaterialRegistrableInterface material,
                                         int cantidad,
                                         EstadoEquipo estadoDestino) {
        aplicarMovimientoPreview((Material) material, cantidad, estadoDestino);
    }

    /**
     * Sobrecarga tipada usada internamente (sin cast por parte del caller).
     */
    public void aplicarMovimientoPreview(Material material, int cantidad, EstadoEquipo estadoDestino) {
        int cantidadActual = material.getCantidad();

        if (cantidad >= cantidadActual) {
            material.setEstado(estadoDestino);
        } else {
            material.setCantidad(cantidadActual - cantidad);

            Material existenteEnDestino = buscarMaterialConCodigoYEstado(
                    material.getCodigo(), estadoDestino, material);

            if (existenteEnDestino != null) {
                existenteEnDestino.setCantidad(existenteEnDestino.getCantidad() + cantidad);
            } else {
                Material nuevoLote = new Material(
                    null,
                    material.getCodigo(),
                    material.getDescripcion(),
                    cantidad,
                    estadoDestino
                );
                agregarMaterial(nuevoLote);
            }
        }

        unificarEnMemoria(material.getCodigo(), estadoDestino);
    }

    @Override
    public EstadoEquipo calcularEstado() {
        if (materiales.isEmpty()) {
            return EstadoEquipo.NUEVO;
        }
        EstadoEquipo estadoMasAtrasado = EstadoEquipo.ENTREGADO;
        for (Material material : materiales) {
            EstadoEquipo estadoMaterial = material.getEstado();
            if (estadoMaterial.getOrden() < estadoMasAtrasado.getOrden()) {
                estadoMasAtrasado = estadoMaterial;
            }
        }
        return estadoMasAtrasado;
    }

    @Override
    public EstadoEquipo getSiguienteEstado(EstadoEquipo estadoActual) {
        return calcularSiguienteEstado(estadoActual, requiereLavado, requiereEmpaque);
    }

    public static EstadoEquipo calcularSiguienteEstado(EstadoEquipo estadoActual,
                                                       boolean requiereLavado,
                                                       boolean requiereEmpaque) {
        boolean empaqueEfectivo = requiereEmpaque || requiereLavado;

        switch (estadoActual) {
            case NUEVO:
                if (requiereLavado) {
                    return EstadoEquipo.LAVANDO;
                }
                return empaqueEfectivo ? EstadoEquipo.EMPAQUETADO : EstadoEquipo.ESTERILIZANDO;
            case LAVANDO:
                return EstadoEquipo.LAVADO;
            case LAVADO:
                return empaqueEfectivo ? EstadoEquipo.EMPAQUETADO : EstadoEquipo.ESTERILIZANDO;
            case EMPAQUETADO:
                return EstadoEquipo.ESTERILIZANDO;
            case ESTERILIZANDO:
                return EstadoEquipo.ESTERILIZADO;
            case ESTERILIZADO:
                return null;
            default:
                return null;
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Material buscarMaterialConCodigoYEstado(int codigo, EstadoEquipo estado, Material excluir) {
        for (Material m : materiales) {
            if (m != excluir && m.getCodigo() == codigo && m.getEstado() == estado) {
                return m;
            }
        }
        return null;
    }

    private void unificarEnMemoria(int codigo, EstadoEquipo estado) {
        List<Material> grupo = materiales.stream()
            .filter(m -> m.getCodigo() == codigo && m.getEstado() == estado)
            .collect(Collectors.toList());

        if (grupo.size() <= 1) return;

        Material superviviente = grupo.stream()
            .filter(m -> m.getUltimoMovimiento() != null)
            .max((a, b) -> a.getUltimoMovimiento().compareTo(b.getUltimoMovimiento()))
            .orElse(grupo.get(0));

        int cantidadTotal = grupo.stream().mapToInt(Material::getCantidad).sum();
        superviviente.setCantidad(cantidadTotal);

        for (Material m : grupo) {
            if (m != superviviente) {
                materiales.remove(m);
            }
        }
    }

    // ── Getters y Setters ─────────────────────────────────────────────────────

    public void setId(Integer id)                          { this.id = id; }
    public void setNroCliente(int nroCliente)              { this.nroCliente = nroCliente; }
    public void setClienteNombre(String clienteNombre)     { this.clienteNombre = clienteNombre; }

    public Integer getNroProfesional()                     { return nroProfesional; }
    public void    setNroProfesional(Integer nroProfesional){ this.nroProfesional = nroProfesional; }

    public String  getProfesionalNombre()                  { return profesionalNombre; }
    public void    setProfesionalNombre(String n)          { this.profesionalNombre = n; }

    public String  getPacienteNombre()                     { return pacienteNombre; }
    public void    setPacienteNombre(String pacienteNombre){ this.pacienteNombre = pacienteNombre; }

    public Integer getNroInstitucion()                     { return nroInstitucion; }
    public void    setNroInstitucion(Integer nroInstitucion){ this.nroInstitucion = nroInstitucion; }

    public String  getInstitucionNombre()                  { return institucionNombre; }
    public void    setInstitucionNombre(String institucionNombre){ this.institucionNombre = institucionNombre; }

    /** Acceso tipado para uso interno de DAO y ConstructorEquipo. */
    public List<Material> getMateriales()                  { return materiales; }

    public void    setRequiereLavado(boolean requiereLavado) { this.requiereLavado = requiereLavado; }
    public void    setRequiereEmpaque(boolean requiereEmpaque){ this.requiereEmpaque = requiereEmpaque; }
}