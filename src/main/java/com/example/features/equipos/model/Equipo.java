package com.example.features.equipos.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.common.constants.Constantes;

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
     *
     * Después de aplicar el movimiento, unifica en memoria los materiales que
     * hayan quedado con el mismo código de catálogo y el mismo estado, de modo
     * que la vista previa sea consistente con lo que quedará en BD al confirmar.
     */
    public void aplicarMovimientoPreview(Material material, int cantidad, EstadoEquipo estadoDestino) {
        int cantidadActual = material.getCantidad();

        if (cantidad >= cantidadActual) {
            // El lote completo avanza: solo cambia el estado de la fila existente
            material.setEstado(estadoDestino);
        } else {
            // Subcantidad: reducir la fila original y crear (o fusionar) en destino
            material.setCantidad(cantidadActual - cantidad);

            // Buscar si ya existe un lote en destino con el mismo código para fusionar
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

        // Unificar en memoria cualquier duplicado que haya quedado para el estado destino
        unificarEnMemoria(material.getCodigo(), estadoDestino);
    }

    /**
     * Busca el primer Material del equipo con el código y estado indicados,
     * excluyendo la instancia {@code excluir}.
     * Usado por aplicarMovimientoPreview para detectar lotes fusionables.
     */
    private Material buscarMaterialConCodigoYEstado(int codigo, EstadoEquipo estado, Material excluir) {
        for (Material m : materiales) {
            if (m != excluir && m.getCodigo() == codigo && m.getEstado() == estado) {
                return m;
            }
        }
        return null;
    }

    /**
     * Unifica en la lista en memoria todos los materiales con el código y estado indicados.
     * Conserva el primero de la lista (superviviente) y suma las cantidades del resto.
     * Elimina las filas fusionadas.
     *
     * Criterio de superviviente: fila con {@code ultimoMovimiento} más reciente;
     * en caso de empate, la que aparece primero en la lista.
     */
    private void unificarEnMemoria(int codigo, EstadoEquipo estado) {
        List<Material> grupo = materiales.stream()
            .filter(m -> m.getCodigo() == codigo && m.getEstado() == estado)
            .collect(Collectors.toList());

        if (grupo.size() <= 1) {
            return;
        }

        // Elegir superviviente: el de ultimoMovimiento más reciente; si null, el primero de la lista
        Material superviviente = grupo.stream()
            .filter(m -> m.getUltimoMovimiento() != null)
            .max((a, b) -> a.getUltimoMovimiento().compareTo(b.getUltimoMovimiento()))
            .orElse(grupo.get(0));

        int cantidadTotal = grupo.stream().mapToInt(Material::getCantidad).sum();
        superviviente.setCantidad(cantidadTotal);

        // Eliminar el resto del grupo de la lista del equipo
        for (Material m : grupo) {
            if (m != superviviente) {
                materiales.remove(m);
            }
        }
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