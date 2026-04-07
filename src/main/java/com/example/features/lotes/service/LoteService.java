package com.example.features.lotes.service;

import com.example.features.lotes.model.Lote;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class LoteService {

    private final LoteDAO loteDAO;

    public LoteService(LoteDAO loteDAO) {
        if (loteDAO == null) {
            throw new IllegalArgumentException("LoteDAO no puede ser nulo");
        }
        this.loteDAO = loteDAO;
    }

    public Map<String, Lote> obtenerLotesActivosPorAutoclave() {
        return loteDAO.obtenerLotesActivosPorAutoclave();
    }

    public List<Lote> obtenerLotesFinalizados() {
        return loteDAO.obtenerLotesFinalizados();
    }

    public List<Lote> obtenerTodosLosLotes() {
        return loteDAO.obtenerTodosLosLotes();
    }

    public List<Lote> obtenerLotesEnRango(LocalDate desde, LocalDate hasta) {
        return loteDAO.obtenerLotesEnRango(desde, hasta);
    }

    public List<String> obtenerClientesPorLote(int loteId) {
        return loteDAO.obtenerClientesPorLote(loteId);
    }

    public Map<String, List<String>> obtenerMaterialesPorClientePorLote(int loteId) {
        return loteDAO.obtenerMaterialesPorClientePorLote(loteId);
    }

    public List<LoteMaterialInfo> obtenerMaterialesPorLote(int loteId) {
        return loteDAO.obtenerMaterialesPorLote(loteId);
    }

    public Lote lanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                           List<LoteMovimiento> movimientos) {
        return loteDAO.lanzarLote(autoclaveNombre, capacidadTotal, capacidadUsada, movimientos);
    }

    public boolean finalizarLote(int loteId) {
        return loteDAO.finalizarLote(loteId);
    }

    public boolean marcarLoteFallo(int loteId) {
        return loteDAO.marcarLoteFallo(loteId);
    }
}
