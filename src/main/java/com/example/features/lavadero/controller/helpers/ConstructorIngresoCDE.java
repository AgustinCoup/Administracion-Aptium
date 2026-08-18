package com.example.features.lavadero.controller.helpers;

import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lavadero.model.SalidaLista;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma los ingresos de CDE que corresponden a un conjunto de salidas listas.
 *
 * <p>Agrupa por el cliente que decide el {@link AsignadorClienteCDE} (un {@link EquipoOtros}
 * por cliente asignado) y, dentro de cada grupo, unifica las salidas del mismo elemento
 * sumando cantidades. Con el asignador de APTIUM todas las salidas caen en el mismo grupo,
 * vengan del cliente que vengan: la agrupación no necesita una regla aparte.
 *
 * <p>El ingreso creado no vuelve a lavarse en el CDE: nace con {@code requiereLavado = false},
 * así que {@code getSiguienteEstado(NUEVO)} devuelve {@code EMPAQUETADO}. Es la traducción de
 * "esto ya salió del lavadero" al vocabulario de la máquina de estados del CDE.
 *
 * <p>Sin dependencias de Swing ni de JDBC: es lógica de armado y se testea sola.
 */
public final class ConstructorIngresoCDE {

    /**
     * @param salidas   salidas listas a derivar; vacía o nula devuelve un resultado vacío
     * @param asignador de dónde sale el {@code nro_cliente} de cada ingreso
     */
    public IngresosCDE construir(List<SalidaLista> salidas, AsignadorClienteCDE asignador) {
        Map<Integer, EquipoOtros>            ingresoPorCliente = new LinkedHashMap<>();
        Map<EquipoOtros, List<Integer>>      salidaIds         = new LinkedHashMap<>();
        Map<EquipoOtros, Map<String, MaterialOtros>> materialPorNombre = new LinkedHashMap<>();

        if (salidas != null) {
            for (SalidaLista salida : salidas) {
                int cliente = asignador.clientePara(salida);
                EquipoOtros ingreso = ingresoPorCliente.computeIfAbsent(cliente, id -> {
                    EquipoOtros nuevo = nuevoIngreso(id);
                    salidaIds.put(nuevo, new ArrayList<>());
                    materialPorNombre.put(nuevo, new LinkedHashMap<>());
                    return nuevo;
                });

                acumularMaterial(ingreso, materialPorNombre.get(ingreso), salida);
                salidaIds.get(ingreso).add(salida.salidaId());
            }
        }

        return new IngresosCDE(new ArrayList<>(ingresoPorCliente.values()), salidaIds);
    }

    /** Ingreso vacío con las banderas que corresponden a algo que ya pasó por el lavadero. */
    private EquipoOtros nuevoIngreso(int nroCliente) {
        EquipoOtros ingreso = new EquipoOtros();
        ingreso.setNroCliente(nroCliente);
        ingreso.setTipoIngreso(TipoIngresoOtros.DETALLES);
        ingreso.setRequiereLavado(false);
        ingreso.setRequiereEmpaque(true);
        return ingreso;
    }

    /**
     * Suma la salida al material del mismo nombre, o crea uno nuevo.
     * Dos tandas de "Batas" del mismo cliente lavadas en ciclos distintos son una sola línea
     * en la cola del CDE, igual que hace {@code EquipoOtros.unificarEnMemoria}.
     */
    private void acumularMaterial(EquipoOtros ingreso,
                                  Map<String, MaterialOtros> materiales,
                                  SalidaLista salida) {
        MaterialOtros existente = materiales.get(salida.elementoNombre());
        if (existente != null) {
            existente.setCantidad(existente.getCantidad() + salida.cantidad());
            return;
        }
        MaterialOtros material = new MaterialOtros(salida.elementoNombre(), salida.cantidad());
        materiales.put(salida.elementoNombre(), material);
        ingreso.agregarMaterial(material);
    }
}
