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
    private boolean requiereLavado;
    private boolean requiereEmpaque;

    // Constructor
    public Equipo() {
        this.materiales = new ArrayList<>();
        this.estado = EstadoEquipo.NUEVO; // Estado inicial por defecto
        this.requiereLavado = true;
        this.requiereEmpaque = true;
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

    /**
     * Obtiene el siguiente estado segun el flujo configurado del equipo.
     * Salta pasos deshabilitados (lavado o empaque).
     */
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
                // ESTERILIZADO es el estado final para avance manual.
                // La entrega se realiza de forma masiva por institución.
                return null;
            default:
                return null;
        }
    }

    /**
     * Aplica un movimiento de subcantidad en memoria para previsualizacion.
     * No persiste en BD; solo ajusta el listado actual de materiales.
     */
    public void aplicarMovimientoPreview(Material material, int cantidad, EstadoEquipo estadoDestino) {
        int cantidadActual = material.getCantidad();
        if (cantidad >= cantidadActual) {
            material.setEstado(estadoDestino);
            return;
        }

        material.setCantidad(cantidadActual - cantidad);
        Material nuevoLote = new Material(
            null,
            material.getCodigo(),
            material.getDescripcion(),
            cantidad,
            estadoDestino
        );
        agregarMaterial(nuevoLote);
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
    public boolean isRequiereLavado() { return requiereLavado; }
    public void setRequiereLavado(boolean requiereLavado) { this.requiereLavado = requiereLavado; }
    public boolean isRequiereEmpaque() { return requiereEmpaque; }
    public void setRequiereEmpaque(boolean requiereEmpaque) { this.requiereEmpaque = requiereEmpaque; }
}