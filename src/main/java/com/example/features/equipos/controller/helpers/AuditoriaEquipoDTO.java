package com.example.features.equipos.controller.helpers;

import java.time.LocalDateTime;

/**
 * DTO para mostrar registros de auditoría en la pantalla de historial de cambios.
 */
public class AuditoriaEquipoDTO {
    private Integer id;
    private Integer equipoId;
    private Integer materialId;
    private String tipoCambio;
    private String campoModificado;
    private String valorAnterior;
    private String valorNuevo;
    private String motivo;
    private LocalDateTime fechaCambio;

    public AuditoriaEquipoDTO() {
    }

    public AuditoriaEquipoDTO(Integer id, Integer equipoId, Integer materialId, String tipoCambio,
                             String campoModificado, String valorAnterior, String valorNuevo,
                             String motivo, LocalDateTime fechaCambio) {
        this.id = id;
        this.equipoId = equipoId;
        this.materialId = materialId;
        this.tipoCambio = tipoCambio;
        this.campoModificado = campoModificado;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.motivo = motivo;
        this.fechaCambio = fechaCambio;
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
        return "AuditoriaEquipoDTO{" +
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
