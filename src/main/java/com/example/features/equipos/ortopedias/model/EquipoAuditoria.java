package com.example.features.equipos.ortopedias.model;

import java.time.LocalDateTime;

/**
 * Modelo para registros de auditoría en correcciones de equipos.
 * Registra toda modificación, cambio o eliminación de equipos y materiales.
 *
 * Los campos {@code clienteNombre} y {@code materialInfo} son populados únicamente
 * cuando se carga la auditoría completa (obtenerTodos). Cuando se carga la auditoría
 * de un equipo específico (obtenerPorEquipo) quedan en null, lo cual es correcto
 * porque el contexto del equipo ya es conocido.
 */
public class EquipoAuditoria {

    private Integer id;
    private Integer equipoId;
    private Integer materialId;
    private String tipoCambio;       // MODIFICACION_CANTIDAD, MODIFICACION_CODIGO, ELIMINACION_EQUIPO, ELIMINACION_MATERIAL
    private String campoModificado;  // ej: "cantidad", "codigo_catalogo"
    private String valorAnterior;
    private String valorNuevo;
    private String motivo;
    private LocalDateTime fechaCambio;

    /** Nombre del cliente dueño del equipo. Populado solo en obtenerTodos(). */
    private String clienteNombre;

    /** Código + descripción del material afectado (ej: "406 - Makita"). Populado solo en obtenerTodos(). */
    private String materialInfo;

    public EquipoAuditoria() {
    }

    public EquipoAuditoria(Integer equipoId, Integer materialId, String tipoCambio,
                           String campoModificado, String valorAnterior, String valorNuevo,
                           String motivo) {
        this.equipoId        = equipoId;
        this.materialId      = materialId;
        this.tipoCambio      = tipoCambio;
        this.campoModificado = campoModificado;
        this.valorAnterior   = valorAnterior;
        this.valorNuevo      = valorNuevo;
        this.motivo          = motivo;
        this.fechaCambio     = LocalDateTime.now();
    }

    // ── Getters y setters ────────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getEquipoId() { return equipoId; }
    public void setEquipoId(Integer equipoId) { this.equipoId = equipoId; }

    public Integer getMaterialId() { return materialId; }
    public void setMaterialId(Integer materialId) { this.materialId = materialId; }

    public String getTipoCambio() { return tipoCambio; }
    public void setTipoCambio(String tipoCambio) { this.tipoCambio = tipoCambio; }

    public String getCampoModificado() { return campoModificado; }
    public void setCampoModificado(String campoModificado) { this.campoModificado = campoModificado; }

    public String getValorAnterior() { return valorAnterior; }
    public void setValorAnterior(String valorAnterior) { this.valorAnterior = valorAnterior; }

    public String getValorNuevo() { return valorNuevo; }
    public void setValorNuevo(String valorNuevo) { this.valorNuevo = valorNuevo; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getMaterialInfo() { return materialInfo; }
    public void setMaterialInfo(String materialInfo) { this.materialInfo = materialInfo; }

    @Override
    public String toString() {
        return "EquipoAuditoria{id=" + id + ", equipoId=" + equipoId +
               ", tipoCambio='" + tipoCambio + "', fecha=" + fechaCambio + "}";
    }
}