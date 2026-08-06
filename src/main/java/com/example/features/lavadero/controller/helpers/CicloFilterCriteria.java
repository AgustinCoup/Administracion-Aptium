package com.example.features.lavadero.controller.helpers;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Criterios de filtrado para la pantalla Ver Ciclos.
 *
 * - {@code estados} es una lista (multi-selección via CheckableComboBox);
 *   vacía significa "sin filtro" (mostrar todos).
 * - Un null en cualquiera de los extremos de fecha significa "abierto".
 */
public class CicloFilterCriteria {

    private final Integer      numeroLavarropas; // null → sin filtro de lavarropas
    private final List<String> estados;          // vacío → sin filtro de estado
    private final LocalDate    fechaDesde;       // null → sin límite inferior
    private final LocalDate    fechaHasta;       // null → sin límite superior

    public CicloFilterCriteria(Integer numeroLavarropas,
                               List<String> estados,
                               LocalDate fechaDesde,
                               LocalDate fechaHasta) {
        this.numeroLavarropas = numeroLavarropas;
        this.estados          = estados != null ? estados : Collections.emptyList();
        this.fechaDesde       = fechaDesde;
        this.fechaHasta       = fechaHasta;
    }

    public Integer      getNumeroLavarropas() { return numeroLavarropas; }
    public List<String> getEstados()          { return estados; }
    public LocalDate    getFechaDesde()       { return fechaDesde; }
    public LocalDate    getFechaHasta()       { return fechaHasta; }
}
