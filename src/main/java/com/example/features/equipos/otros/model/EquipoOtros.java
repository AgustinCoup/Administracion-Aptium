package com.example.features.equipos.otros.model;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Representa un ingreso de tipo "Otros" (no ortopedia).
 *
 * No tiene profesional, paciente ni institución. Los materiales son de
 * texto libre, gestionados a través de {@link MaterialOtros}.
 *
 * Implementa {@link EquipoRegistrableInterface} para participar de forma transparente
 * en {@link com.example.features.equipos.ortopedias.controller.RegistrarEstadoController}
 * junto con equipos de ortopedia.
 */
public class EquipoOtros implements EquipoRegistrableInterface {

    private Integer id;
    private int     nroCliente;
    private String  clienteNombre;
    private EstadoEquipo estado;
    private boolean requiereLavado;
    private boolean requiereEmpaque;

    private final List<MaterialOtros> materiales = new ArrayList<>();

    public EquipoOtros() {
        this.estado        = EstadoEquipo.NUEVO;
        this.requiereLavado  = true;
        this.requiereEmpaque = true;
    }

    public void agregarMaterial(MaterialOtros material) {
        materiales.add(material);
    }

    // ── IEquipoRegistrable ────────────────────────────────────────────────────

    @Override public TipoEquipo getTipo()                    { return TipoEquipo.OTROS; }
    @Override public Integer    getId()                      { return id; }
    @Override public String     getClienteNombre()           { return clienteNombre; }
    @Override public int        getNroCliente()              { return nroCliente; }

    /**
     * Los equipos "Otros" no tienen institución; devuelve cadena vacía
     * para que la columna de la tabla quede en blanco.
     */
    @Override public String     getDescripcionSecundaria()   { return ""; }

    @Override public EstadoEquipo getEstado()                { return estado; }
    @Override public void         setEstado(EstadoEquipo e)  { this.estado = e; }
    @Override public boolean      isRequiereLavado()         { return requiereLavado; }
    @Override public boolean      isRequiereEmpaque()        { return requiereEmpaque; }

    @Override
    public EstadoEquipo calcularEstado() {
        if (materiales.isEmpty()) return EstadoEquipo.NUEVO;
        EstadoEquipo masAtrasado = EstadoEquipo.ENTREGADO;
        for (MaterialOtros m : materiales) {
            if (m.getEstado().getOrden() < masAtrasado.getOrden()) {
                masAtrasado = m.getEstado();
            }
        }
        return masAtrasado;
    }

    /**
     * Reutiliza la lógica estática de {@link Equipo#calcularSiguienteEstado},
     * que ya encapsula las reglas de lavado y empaque.
     */
    @Override
    public EstadoEquipo getSiguienteEstado(EstadoEquipo estadoActual) {
        return Equipo.calcularSiguienteEstado(estadoActual, requiereLavado, requiereEmpaque);
    }

    @Override
    public List<MaterialRegistrableInterface> getMaterialesRegistrables() {
        return Collections.unmodifiableList(materiales);
    }

    /**
     * Aplica un movimiento de subcantidad en memoria (preview visual).
     * Usa la descripción normalizada como clave de agrupación para split/merge,
     * replicando el mismo comportamiento que {@link Equipo#aplicarMovimientoPreview}.
     */
    @Override
    public void aplicarMovimientoPreview(MaterialRegistrableInterface material,
                                         int cantidad,
                                         EstadoEquipo estadoDestino) {
        MaterialOtros m = (MaterialOtros) material;  // seguro: EquipoOtros solo contiene MaterialOtros
        int cantidadActual = m.getCantidad();

        if (cantidad >= cantidadActual) {
            m.setEstado(estadoDestino);
        } else {
            m.setCantidad(cantidadActual - cantidad);

            MaterialOtros existenteEnDestino =
                    buscarMaterialConDescripcionYEstado(m.getDescripcion(), estadoDestino, m);

            if (existenteEnDestino != null) {
                existenteEnDestino.setCantidad(existenteEnDestino.getCantidad() + cantidad);
            } else {
                MaterialOtros nuevoLote = new MaterialOtros(
                        m.getCatalogoOtrosId(),
                        m.getDescripcion(),
                        cantidad,
                        estadoDestino
                );
                materiales.add(nuevoLote);
            }
        }

        unificarEnMemoria(m.getDescripcion(), estadoDestino);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private MaterialOtros buscarMaterialConDescripcionYEstado(String descripcion,
                                                              EstadoEquipo estado,
                                                              MaterialOtros excluir) {
        for (MaterialOtros m : materiales) {
            if (m != excluir
                    && m.getDescripcion().equalsIgnoreCase(descripcion)
                    && m.getEstado() == estado) {
                return m;
            }
        }
        return null;
    }

    private void unificarEnMemoria(String descripcion, EstadoEquipo estado) {
        List<MaterialOtros> grupo = materiales.stream()
                .filter(m -> m.getDescripcion().equalsIgnoreCase(descripcion)
                          && m.getEstado() == estado)
                .collect(Collectors.toList());

        if (grupo.size() <= 1) return;

        MaterialOtros superviviente = grupo.stream()
                .filter(m -> m.getUltimoMovimiento() != null)
                .max((a, b) -> a.getUltimoMovimiento().compareTo(b.getUltimoMovimiento()))
                .orElse(grupo.get(0));

        int cantidadTotal = grupo.stream().mapToInt(MaterialOtros::getCantidad).sum();
        superviviente.setCantidad(cantidadTotal);

        for (MaterialOtros m : grupo) {
            if (m != superviviente) {
                materiales.remove(m);
            }
        }
    }

    // ── Getters / Setters propios ─────────────────────────────────────────────

    /** Acceso tipado para el DAO (evita el cast desde IMaterialRegistrable). */
    public List<MaterialOtros> getMateriales() { return materiales; }

    public void    setId(Integer id)                  { this.id = id; }
    public void    setNroCliente(int nroCliente)      { this.nroCliente = nroCliente; }
    public void    setClienteNombre(String nombre)    { this.clienteNombre = nombre; }
    public void    setRequiereLavado(boolean v)       { this.requiereLavado = v; }
    public void    setRequiereEmpaque(boolean v)      { this.requiereEmpaque = v; }
}