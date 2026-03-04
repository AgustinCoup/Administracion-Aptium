package com.example.features.equipos.controller.helpers;

/**
 * DTO para mostrar/editar información de materiales en la pantalla de correcciones.
 * Contiene solo los datos necesarios para la UI de correcciones.
 */
public class MaterialCorreccionDTO {
    private Integer materialId;
    private Integer equipoId;
    private Integer codigoActual;
    private String descripcionActual;
    private Integer cantidadActual;
    private String estadoMaterial;

    public MaterialCorreccionDTO() {
    }

    public MaterialCorreccionDTO(Integer materialId, Integer equipoId, Integer codigoActual,
                                String descripcionActual, Integer cantidadActual, String estadoMaterial) {
        this.materialId = materialId;
        this.equipoId = equipoId;
        this.codigoActual = codigoActual;
        this.descripcionActual = descripcionActual;
        this.cantidadActual = cantidadActual;
        this.estadoMaterial = estadoMaterial;
    }

    // Getters y Setters
    public Integer getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Integer materialId) {
        this.materialId = materialId;
    }

    public Integer getEquipoId() {
        return equipoId;
    }

    public void setEquipoId(Integer equipoId) {
        this.equipoId = equipoId;
    }

    public Integer getCodigoActual() {
        return codigoActual;
    }

    public void setCodigoActual(Integer codigoActual) {
        this.codigoActual = codigoActual;
    }

    public String getDescripcionActual() {
        return descripcionActual;
    }

    public void setDescripcionActual(String descripcionActual) {
        this.descripcionActual = descripcionActual;
    }

    public Integer getCantidadActual() {
        return cantidadActual;
    }

    public void setCantidadActual(Integer cantidadActual) {
        this.cantidadActual = cantidadActual;
    }

    public String getEstadoMaterial() {
        return estadoMaterial;
    }

    public void setEstadoMaterial(String estadoMaterial) {
        this.estadoMaterial = estadoMaterial;
    }

    @Override
    public String toString() {
        return "MaterialCorreccionDTO{" +
                "materialId=" + materialId +
                ", equipoId=" + equipoId +
                ", codigoActual=" + codigoActual +
                ", descripcionActual='" + descripcionActual + '\'' +
                ", cantidadActual=" + cantidadActual +
                ", estadoMaterial='" + estadoMaterial + '\'' +
                '}';
    }
}
