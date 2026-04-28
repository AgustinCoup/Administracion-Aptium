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
 * No tiene profesional, paciente ni institución. Admite dos modalidades:
 * <ul>
 *   <li>{@link TipoIngresoOtros#DETALLES}: materiales de texto libre gestionados
 *       a través de {@link MaterialOtros}.</li>
 *   <li>{@link TipoIngresoOtros#REMITO}: se registra un identificador único
 *       ({@code remito_id}), una cantidad global y observaciones opcionales.
 *       La lista de materiales permanece vacía.</li>
 * </ul>
 *
 * Implementa {@link EquipoRegistrableInterface} para participar de forma transparente
 * en {@link com.example.features.equipos.ortopedias.controller.RegistrarEstadoController}
 * junto con equipos de ortopedia.
 */
public class EquipoOtros implements EquipoRegistrableInterface {

    private Integer          id;
    private int              nroCliente;
    private String           clienteNombre;
    private EstadoEquipo     estado;
    private boolean          requiereLavado;
    private boolean          requiereEmpaque;

    // ── Modalidad de ingreso ──────────────────────────────────────────────────
    private TipoIngresoOtros tipoIngreso        = TipoIngresoOtros.DETALLES;
    private String           remitoId;           // ddmmaaaa-{id}, generado por el DAO
    private Integer          remitoCantidad;     // cantidad global del remito
    private String           remitoObservaciones;

    private int              volumenEquipo       = 0; // litros acumulados en lotes exitosos

    private final List<MaterialOtros> materiales = new ArrayList<>();

    public EquipoOtros() {
        this.estado          = EstadoEquipo.NUEVO;
        this.requiereLavado  = true;
        this.requiereEmpaque = true;
    }

    public void agregarMaterial(MaterialOtros material) {
        materiales.add(material);
    }

    // ── IEquipoRegistrable ────────────────────────────────────────────────────

    @Override public TipoEquipo  getTipo()                    { return TipoEquipo.OTROS; }
    @Override public Integer     getId()                      { return id; }
    @Override public String      getClienteNombre()           { return clienteNombre; }
    @Override public int         getNroCliente()              { return nroCliente; }

    /**
     * Los equipos "Otros" no tienen institución; devuelve cadena vacía
     * para que la columna de la tabla quede en blanco.
     */
    @Override public String      getDescripcionSecundaria()   { return ""; }

    @Override public EstadoEquipo getEstado()                 { return estado; }
    @Override public void         setEstado(EstadoEquipo e)   { this.estado = e; }
    @Override public boolean      isRequiereLavado()          { return requiereLavado; }
    @Override public boolean      isRequiereEmpaque()         { return requiereEmpaque; }

    @Override
    public EstadoEquipo calcularEstado() {
        // REMITO sin filas reales: el estado vive en el campo 'estado' del equipo
        if (tipoIngreso == TipoIngresoOtros.REMITO && materiales.isEmpty()) return estado;
        if (materiales.isEmpty()) return estado;
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

    /**
     * Para REMITO sin filas reales: expone un material sintético con id=0
     * que señala al DAO para crear las filas al persistir.
     * Para REMITO con filas ya creadas (post-split): devuelve esas filas reales.
     * Para DETALLES: devuelve la lista real de materiales.
     */
    @Override
    public List<MaterialRegistrableInterface> getMaterialesRegistrables() {
        if (tipoIngreso == TipoIngresoOtros.REMITO && materiales.isEmpty()) {
            MaterialOtros sintetico = new MaterialOtros(
                0, null, "Elementos",
                remitoCantidad != null ? remitoCantidad : 1,
                estado, null
            );
            return Collections.singletonList(sintetico);
        }
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
        if (tipoIngreso == TipoIngresoOtros.REMITO && materiales.isEmpty()) {
            // El material sintético tiene id=0; simulamos el split en memoria.
            int total = remitoCantidad != null ? remitoCantidad : 1;
            if (cantidad >= total) {
                // Avanza todo: una sola fila sintética en el nuevo estado
                materiales.add(new MaterialOtros(0, null, "Elementos", total, estadoDestino, null));
            } else {
                // Split: fila restante en estado actual + fila nueva en estado destino
                materiales.add(new MaterialOtros(0,    null, "Elementos", total - cantidad, estado,       null));
                materiales.add(new MaterialOtros(null, null, "Elementos", cantidad,         estadoDestino, null));
            }
            return;
        }
        if (!(material instanceof MaterialOtros)) {
            throw new IllegalArgumentException(
                "Se esperaba MaterialOtros, se recibió: " + material.getClass().getSimpleName());
        }
        MaterialOtros m = (MaterialOtros) material;
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
    public List<MaterialOtros> getMateriales()              { return materiales; }

    public void    setId(Integer id)                        { this.id = id; }
    public void    setNroCliente(int nroCliente)            { this.nroCliente = nroCliente; }
    public void    setClienteNombre(String nombre)          { this.clienteNombre = nombre; }
    public void    setRequiereLavado(boolean v)             { this.requiereLavado = v; }
    public void    setRequiereEmpaque(boolean v)            { this.requiereEmpaque = v; }

    // ── Remito ────────────────────────────────────────────────────────────────

    public TipoIngresoOtros getTipoIngreso()                { return tipoIngreso; }
    public void    setTipoIngreso(TipoIngresoOtros t)       { this.tipoIngreso = t; }

    public String  getRemitoId()                            { return remitoId; }
    public void    setRemitoId(String remitoId)             { this.remitoId = remitoId; }

    public Integer getRemitoCantidad()                      { return remitoCantidad; }
    public void    setRemitoCantidad(Integer cant)          { this.remitoCantidad = cant; }

    public String  getRemitoObservaciones()                 { return remitoObservaciones; }
    public void    setRemitoObservaciones(String obs)       { this.remitoObservaciones = obs; }

    public int     getVolumenEquipo()                       { return volumenEquipo; }
    public void    setVolumenEquipo(int volumen)            { this.volumenEquipo = volumen; }
}