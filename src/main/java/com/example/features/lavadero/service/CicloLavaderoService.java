package com.example.features.lavadero.service;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ConfiguracionCiclo;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.LanzamientoCiclo;
import com.example.features.lavadero.model.LineaLanzamiento;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CicloLavaderoService {

    private final CicloLavaderoDAO dao;

    public CicloLavaderoService(CicloLavaderoDAO dao) {
        if (dao == null) throw new IllegalArgumentException("CicloLavaderoDAO no puede ser nulo");
        this.dao = dao;
    }

    public Map<Integer, CicloLavadero> obtenerCiclosActivosPorLavarropas() {
        return dao.obtenerCiclosActivosPorLavarropas();
    }

    public List<ElementoCicloItem> obtenerElementosDisponiblesParaCiclo() {
        return dao.obtenerElementosDisponiblesParaCiclo();
    }

    /**
     * Valida la tanda entera antes de tocar la base: si un solo ciclo está mal, no se lanza
     * ninguno. La transacción es del DAO; acá sólo se rechaza lo que no debería llegar.
     */
    public void lanzarTanda(List<LanzamientoCiclo> tanda) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(tanda == null || tanda.isEmpty(), "Debe haber al menos un ciclo para lanzar.");
        if (tanda != null) {
            for (LanzamientoCiclo ciclo : tanda) validar(ciclo, v);
        }
        v.throwIfHasErrors();
        dao.lanzarTanda(tanda);
    }

    /** Los mensajes van prefijados con el lavarropas: en una tanda, "falta el jabón" solo no ubica. */
    private static void validar(LanzamientoCiclo ciclo, ValidationException.Builder v) {
        int numero = ciclo.lavarropasNumero();
        String prefijo = "Lavarropas #" + numero + ": ";

        v.addErrorIf(numero < 1 || numero > Constantes.Lavadero.CANTIDAD_LAVARROPAS,
            "El número de lavarropas debe estar entre 1 y " + Constantes.Lavadero.CANTIDAD_LAVARROPAS + ".");

        ConfiguracionCiclo config = ciclo.config();
        v.addErrorIf(config == null, prefijo + "debe configurar el ciclo.");
        if (config != null) {
            v.addErrorIf(config.tipoLavado() == null,
                prefijo + "debe seleccionar el tipo de lavado.");
            v.addErrorIf(config.jabon() == null,
                prefijo + "debe seleccionar un jabón.");
            v.addErrorIf(config.litrosJabon() == null || config.litrosJabon().compareTo(BigDecimal.ZERO) <= 0,
                prefijo + "los mililitros de jabón deben ser mayores a cero.");
        }

        List<LineaLanzamiento> lineas = ciclo.lineas();
        v.addErrorIf(lineas == null || lineas.isEmpty(),
            prefijo + "debe agregar al menos un elemento al ciclo.");
        if (lineas != null) {
            for (LineaLanzamiento linea : lineas) {
                v.addErrorIf(linea.cantidad() <= 0,
                    prefijo + "la cantidad de cada elemento debe ser mayor a cero.");
                v.addErrorIf(linea.totalPartes() <= 0,
                    prefijo + "el total de partes de un equipo repartido debe ser mayor a cero.");
            }
        }
    }

    public List<CicloLavadero> obtenerCiclosFinalizados() {
        return dao.obtenerCiclosFinalizados();
    }

    public List<CicloLavadero> obtenerTodosLosCiclos() {
        return dao.obtenerTodosLosCiclos();
    }

    public List<ElementoCicloItem> obtenerElementosDeCiclo(int cicloId) {
        return dao.obtenerElementosDeCiclo(cicloId);
    }

    public void finalizarCiclo(int cicloId) {
        ValidationException.Builder v = ValidationException.builder();
        v.addErrorIf(cicloId <= 0, "ID de ciclo inválido.");
        v.throwIfHasErrors();
        dao.finalizarCiclo(cicloId);
    }
}
