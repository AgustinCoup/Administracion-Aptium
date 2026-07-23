package com.example.features.lotes.controller.helpers;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import com.example.features.lotes.view.helpers.MaterialLoteItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma la lista de materiales que se pueden cargar a un autoclave.
 *
 * <p>Un material está disponible cuando su próxima transición es a
 * ESTERILIZANDO. A esa lista se le descuenta lo que el usuario ya arrastró a
 * algún autoclave y todavía no lanzó (lo "pendiente"), para que no aparezca
 * dos veces.
 *
 * <p>Lógica pura: sin Swing, sin base de datos y sin depender de campos del
 * controller — todo lo que necesita entra por parámetro.
 */
public class ConstructorMaterialesDisponibles {

    private static final int VOLUMEN_POR_DEFECTO = 1;

    /** Descripción de la fila única que representa un REMITO sin materiales detallados. */
    private static final String DESCRIPCION_REMITO = "Elementos";

    /**
     * @param equipos                equipos de ortopedia del snapshot
     * @param equiposOtros           equipos "otros" del snapshot
     * @param volumenesCatalogo      volumen unitario por código de catálogo
     * @param pendientesPorAutoclave lo ya arrastrado a cada autoclave, a descontar
     * @return los materiales que la tabla "Disponibles" debe mostrar
     */
    public List<MaterialLoteItem> construir(
            List<Equipo> equipos,
            List<EquipoOtros> equiposOtros,
            Map<Integer, Integer> volumenesCatalogo,
            Map<String, List<MaterialLoteItem>> pendientesPorAutoclave) {

        List<MaterialLoteItem> disponibles = new ArrayList<>();
        agregarOrtopedias(equipos, volumenesCatalogo, disponibles);
        agregarOtros(equiposOtros, disponibles);
        return descontarPendientes(disponibles, pendientesPorAutoclave);
    }

    private static void agregarOrtopedias(List<Equipo> equipos,
                                          Map<Integer, Integer> volumenesCatalogo,
                                          List<MaterialLoteItem> destino) {
        for (Equipo equipo : equipos) {
            if (equipo.getMateriales() == null) continue;
            String clienteNombre = equipo.getClienteNombre() != null ? equipo.getClienteNombre() : "";

            for (Material material : equipo.getMateriales()) {
                if (equipo.getSiguienteEstado(material.getEstado()) != EstadoEquipo.ESTERILIZANDO) continue;

                Integer volumen = volumenesCatalogo.get(material.getCodigo());
                destino.add(new MaterialLoteItem(
                    material.getId(),
                    equipo.getId(),
                    material.getDescripcion(),
                    material.getCantidad(),
                    volumen != null ? volumen : VOLUMEN_POR_DEFECTO,
                    clienteNombre));
            }
        }
    }

    private static void agregarOtros(List<EquipoOtros> equiposOtros, List<MaterialLoteItem> destino) {
        for (EquipoOtros equipo : equiposOtros) {
            String clienteNombre = equipo.getClienteNombre() != null ? equipo.getClienteNombre() : "";
            List<MaterialOtros> materiales = equipo.getMateriales();
            boolean remitoSinFilas = equipo.getTipoIngreso() == TipoIngresoOtros.REMITO
                                     && (materiales == null || materiales.isEmpty());

            if (remitoSinFilas) {
                if (equipo.getSiguienteEstado(equipo.getEstado()) != EstadoEquipo.ESTERILIZANDO) continue;
                int cantidad = equipo.getRemitoCantidad() != null ? equipo.getRemitoCantidad() : 1;
                // materialId negativo = -equipoId, señal única de REMITO para el DAO
                destino.add(new MaterialLoteItem(
                    -equipo.getId(), equipo.getId(), DESCRIPCION_REMITO, cantidad,
                    VOLUMEN_POR_DEFECTO, clienteNombre, true));
                continue;
            }

            if (materiales == null) continue;
            for (MaterialOtros material : materiales) {
                if (equipo.getSiguienteEstado(material.getEstado()) != EstadoEquipo.ESTERILIZANDO) continue;
                if (material.getId() == null) continue;
                destino.add(new MaterialLoteItem(
                    material.getId(), equipo.getId(), material.getDescripcion(),
                    material.getCantidad(), VOLUMEN_POR_DEFECTO, clienteNombre, true));
            }
        }
    }

    /**
     * Resta de los disponibles lo que ya está cargado en algún autoclave.
     * Si lo pendiente cubre todo el material, la fila desaparece.
     */
    private static List<MaterialLoteItem> descontarPendientes(
            List<MaterialLoteItem> disponibles,
            Map<String, List<MaterialLoteItem>> pendientesPorAutoclave) {

        Map<String, MaterialLoteItem> porClave = new LinkedHashMap<>();
        for (MaterialLoteItem item : disponibles) {
            porClave.put(ReconciliadorPendientes.claveItem(item), item);
        }

        for (List<MaterialLoteItem> pendientes : pendientesPorAutoclave.values()) {
            for (MaterialLoteItem pendiente : pendientes) {
                String clave = ReconciliadorPendientes.claveItem(pendiente);
                MaterialLoteItem disponible = porClave.get(clave);
                if (disponible == null) continue;

                int restante = disponible.getCantidad() - pendiente.getCantidad();
                if (restante <= 0) porClave.remove(clave);
                else disponible.setCantidad(restante);
            }
        }

        return new ArrayList<>(porClave.values());
    }
}
