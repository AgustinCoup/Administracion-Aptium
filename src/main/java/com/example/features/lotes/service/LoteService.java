package com.example.features.lotes.service;

import com.example.features.lotes.model.Lote;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.lotes.model.LoteMaterialInfo;
import com.example.features.lotes.model.LoteMovimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class LoteService {

    private static final Logger log = LoggerFactory.getLogger(LoteService.class);

    private final LoteDAO loteDAO;

    public LoteService(LoteDAO loteDAO) {
        if (loteDAO == null) {
            throw new IllegalArgumentException("LoteDAO no puede ser nulo");
        }
        this.loteDAO = loteDAO;
    }

    public Map<String, Lote> obtenerLotesActivosPorAutoclave() {
        try {
            return loteDAO.obtenerLotesActivosPorAutoclave();
        } catch (Exception e) {
            log.error("Error al obtener lotes activos", e);
            return Map.of();
        }
    }

    public List<Lote> obtenerLotesFinalizados() {
        try {
            return loteDAO.obtenerLotesFinalizados();
        } catch (Exception e) {
            log.error("Error al obtener lotes finalizados", e);
            return List.of();
        }
    }

    public List<Lote> obtenerTodosLosLotes() {
        try {
            return loteDAO.obtenerTodosLosLotes();
        } catch (Exception e) {
            log.error("Error al obtener todos los lotes", e);
            return List.of();
        }
    }

    public List<Lote> obtenerLotesEnRango(LocalDate desde, LocalDate hasta) {
        try {
            return loteDAO.obtenerLotesEnRango(desde, hasta);
        } catch (Exception e) {
            log.error("Error al obtener lotes en rango [{} - {}]", desde, hasta, e);
            return List.of();
        }
    }

    public List<String> obtenerClientesPorLote(int loteId) {
        try {
            return loteDAO.obtenerClientesPorLote(loteId);
        } catch (Exception e) {
            log.error("Error al obtener clientes del lote: {}", loteId, e);
            return List.of();
        }
    }

    public Map<String, List<String>> obtenerMaterialesPorClientePorLote(int loteId) {
        try {
            return loteDAO.obtenerMaterialesPorClientePorLote(loteId);
        } catch (Exception e) {
            log.error("Error al obtener materiales por cliente del lote: {}", loteId, e);
            return Map.of();
        }
    }

    public List<LoteMaterialInfo> obtenerMaterialesPorLote(int loteId) {
        try {
            return loteDAO.obtenerMaterialesPorLote(loteId);
        } catch (Exception e) {
            log.error("Error al obtener materiales del lote: {}", loteId, e);
            return List.of();
        }
    }

    public Lote lanzarLote(String autoclaveNombre, int capacidadTotal, int capacidadUsada,
                           List<LoteMovimiento> movimientos) {
        try {
            return loteDAO.lanzarLote(autoclaveNombre, capacidadTotal, capacidadUsada, movimientos);
        } catch (Exception e) {
            log.error("Error al lanzar lote para autoclave: {}", autoclaveNombre, e);
            return null;
        }
    }

    public boolean finalizarLote(int loteId) {
        try {
            return loteDAO.finalizarLote(loteId);
        } catch (Exception e) {
            log.error("Error al finalizar lote: {}", loteId, e);
            return false;
        }
    }

    public boolean marcarLoteFallo(int loteId) {
        try {
            return loteDAO.marcarLoteFallo(loteId);
        } catch (Exception e) {
            log.error("Error al marcar lote como fallido: {}", loteId, e);
            return false;
        }
    }
}


