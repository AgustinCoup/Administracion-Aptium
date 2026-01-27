package com.example.model;

import java.util.ArrayList;
import java.util.List;

import com.example.constants.Constantes;

public class Equipo {
    // Identificación de Negocio (Ej: 20261)
    private String codigoEquipo;

    // Datos del Cliente
    private int nroCliente;
    private String clienteNombre;

    // Datos Médicos
    private String profesionalNombre;
    private String pacienteNombre;

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

    // --- Getters y Setters ---

    public String getCodigoEquipo() { return codigoEquipo; }
    public void setCodigoEquipo(String codigoEquipo) { this.codigoEquipo = codigoEquipo; }

    public int getNroCliente() { return nroCliente; }
    public void setNroCliente(int nroCliente) { this.nroCliente = nroCliente; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getProfesionalNombre() { return profesionalNombre; }
    public void setProfesionalNombre(String profesionalNombre) { this.profesionalNombre = profesionalNombre; }

    public String getPacienteNombre() { return pacienteNombre; }
    public void setPacienteNombre(String pacienteNombre) { this.pacienteNombre = pacienteNombre; }

    public EstadoEquipo getEstado() { return estado; }
    public void setEstado(EstadoEquipo estado) { this.estado = estado; }

    public List<Material> getMateriales() { return materiales; }
    public void setMateriales(List<Material> materiales) { this.materiales = materiales; }
}