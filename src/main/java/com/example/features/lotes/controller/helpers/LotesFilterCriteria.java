package com.example.features.lotes.controller.helpers;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Criterios de filtrado para la pantalla Ver Lotes.
 *
 * Cambios respecto a la versión anterior:
 * - {@code autoclaves} y {@code estados} son listas (multi-selección via CheckableComboBox).
 *   Una lista vacía significa "sin filtro" (mostrar todos).
 * - La fecha única {@code fechaInicio} se reemplazó por un rango {@code fechaDesde}/{@code fechaHasta}.
 *   Un valor null en cualquiera de los dos extremos significa "abierto".
 */
public class LotesFilterCriteria {

    private final String       id;
    private final List<String> autoclaves;  // vacío → sin filtro de autoclave
    private final List<String> estados;     // vacío → sin filtro de estado
    private final LocalDate    fechaDesde;  // null → sin límite inferior
    private final LocalDate    fechaHasta;  // null → sin límite superior

    public LotesFilterCriteria(String id,
                                List<String> autoclaves,
                                List<String> estados,
                                LocalDate fechaDesde,
                                LocalDate fechaHasta) {
        this.id         = id != null ? id.trim() : "";
        this.autoclaves = autoclaves != null ? autoclaves : Collections.emptyList();
        this.estados    = estados    != null ? estados    : Collections.emptyList();
        this.fechaDesde = fechaDesde;
        this.fechaHasta = fechaHasta;
    }

    public String       getId()         { return id; }
    public List<String> getAutoclaves() { return autoclaves; }
    public List<String> getEstados()    { return estados; }
    public LocalDate    getFechaDesde() { return fechaDesde; }
    public LocalDate    getFechaHasta() { return fechaHasta; }
}