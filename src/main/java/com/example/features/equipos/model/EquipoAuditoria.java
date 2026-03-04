package com.example.features.equipos.model;

import java.time.LocalDateTime;

/**
 * Modelo para registros de auditoría en correcciones de equipos.
 * Registra toda modificación, cambio o eliminación de equipos y materiales.
 */
public class EquipoAuditoria {
    private Integer id;
    private Integer equipoId;
    private Integer materialId;
    private String tipoCambio;  // MODIFICACION_CANTIDAD, MODIFICACION_CODIGO, ELIMINACION_EQUIPO
    private String campoModificado;  // ej: "cantidad", "codigo_catalogo"
    private String valorAnterior;
    private String valorNuevo;
    private String motivo;
    private LocalDateTime fechaCambio;

    public EquipoAuditoria() {
    }

    public EquipoAuditoria(Integer equipoId, Integer materialId, String tipoCambio, 
                          String campoModificado, String valorAnterior, String valorNuevo, String motivo) {
        this.equipoId = equipoId;
        this.materialId = materialId;
        this.tipoCambio = tipoCambio;
        this.campoModificado = campoModificado;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.motivo = motivo;
        this.fechaCambio = LocalDateTime.now();
    }

    // Getters y Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getEquipoId() {
        return equipoId;
    }

    public void setEquipoId(Integer equipoId) {
        this.equipoId = equipoId;
    }

    public Integer getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Integer materialId) {
        this.materialId = materialId;
    }

    public String getTipoCambio() {
        return tipoCambio;
    }

    public void setTipoCambio(String tipoCambio) {
        this.tipoCambio = tipoCambio;
    }

    public String getCampoModificado() {
        return campoModificado;
    }

    public void setCampoModificado(String campoModificado) {
        this.campoModificado = campoModificado;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public void setValorNuevo(String valorNuevo) {
        this.valorNuevo = valorNuevo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaCambio() {
        return fechaCambio;
    }

    public void setFechaCambio(LocalDateTime fechaCambio) {
        this.fechaCambio = fechaCambio;
    }

    @Override
    public String toString() {
        return "EquipoAuditoria{" +
                "id=" + id +
                ", equipoId=" + equipoId +
                ", materialId=" + materialId +
                ", tipoCambio='" + tipoCambio + '\'' +
                ", campoModificado='" + campoModificado + '\'' +
                ", valorAnterior='" + valorAnterior + '\'' +
                ", valorNuevo='" + valorNuevo + '\'' +
                ", motivo='" + motivo + '\'' +
                ", fechaCambio=" + fechaCambio +
                '}';
    }
}
