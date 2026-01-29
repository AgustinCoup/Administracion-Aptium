package com.example.model;

import java.util.ArrayList;
import java.util.List;

import com.example.constants.Constantes;

public class Equipo {
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

    // Constructor
    public Equipo() {
        this.materiales = new ArrayList<>();
        this.estado = EstadoEquipo.NUEVO; // Estado inicial por defecto
    }

    // Método para facilitar la carga de materiales desde el Controlador
    public void agregarMaterial(Material material) {
        this.materiales.add(material);
    }

    /**
     * Calcula y retorna el estado del equipo basado en el material más atrasado.
     * El estado del equipo = estado del material con el menor orden (más atrasado).
     * 
     * Si no hay materiales, retorna NUEVO.
     * Si hay materiales, busca el que tenga el menor número de orden.
     * 
     * Orden: NUEVO(1) < LAVANDO(2) < LAVADO(3) < EMPAQUETADO(4) < ESTERILIZANDO(5) < ESTERILIZADO(6) < ENTREGADO(7)
     */
    public EstadoEquipo calcularEstado() {
        if (materiales.isEmpty()) {
            return EstadoEquipo.NUEVO;
        }
        
        // Buscar el material con el menor orden (más atrasado)
        EstadoEquipo estadoMasAtrasado = EstadoEquipo.ENTREGADO; // Comienza con el más avanzado
        
        for (Material material : materiales) {
            EstadoEquipo estadoMaterial = material.getEstado();
            // Si el estado actual es más atrasado que el guardado, actualiza
            if (estadoMaterial.getOrden() < estadoMasAtrasado.getOrden()) {
                estadoMasAtrasado = estadoMaterial;
            }
        }
        
        return estadoMasAtrasado;
    }

    // --- Getters y Setters ---

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public int getNroCliente() { return nroCliente; }
    public void setNroCliente(int nroCliente) { this.nroCliente = nroCliente; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public Integer getNroProfesional() { return nroProfesional; }
    public void setNroProfesional(Integer nroProfesional) { this.nroProfesional = nroProfesional; }

    public String getProfesionalNombre() { return profesionalNombre; }
    public void setProfesionalNombre(String profesionalNombre) { this.profesionalNombre = profesionalNombre; }

    public String getPacienteNombre() { return pacienteNombre; }
    public void setPacienteNombre(String pacienteNombre) { this.pacienteNombre = pacienteNombre; }

    public Integer getNroInstitucion() { return nroInstitucion; }
    public void setNroInstitucion(Integer nroInstitucion) { this.nroInstitucion = nroInstitucion; }

    public String getInstitucionNombre() { return institucionNombre; }
    public void setInstitucionNombre(String institucionNombre) { this.institucionNombre = institucionNombre; }

    public EstadoEquipo getEstado() { return estado; }
    public void setEstado(EstadoEquipo estado) { this.estado = estado; }

    public List<Material> getMateriales() { return materiales; }
    public void setMateriales(List<Material> materiales) { this.materiales = materiales; }
}