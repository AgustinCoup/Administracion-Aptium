package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.common.constants.Constantes;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.view.helpers.InstitucionEntregaItem;
import com.example.features.equipos.ortopedias.view.helpers.MaterialEntregaItem;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Arma la vista de "equipos para entregar" a partir de un listado de equipos.
 *
 * <p>Ortopedias se agrupan por institución y "otros" por cliente, en dos grupos
 * de destinos que conviven en la misma tabla. Es lógica pura sin Swing ni base
 * de datos: entra el snapshot, sale lo que la pantalla tiene que mostrar.
 */
public class AgrupadorEntregas {

    private static final String SIN_CLIENTE = "Sin cliente";

    private final IEstadoValidator estadoValidator;

    public AgrupadorEntregas(IEstadoValidator estadoValidator) {
        this.estadoValidator = Objects.requireNonNull(estadoValidator, "estadoValidator");
    }

    /**
     * Lo que necesita la pantalla de entregas.
     *
     * @param filas                destinos ordenados por nombre, para la tabla de arriba
     * @param materialesPorDestino materiales pendientes de cada destino
     * @param volumenPorDestino    litros acumulados; solo aplica a destinos de tipo CLIENTE
     */
    public record Resultado(
        List<InstitucionEntregaItem>                      filas,
        Map<EntregaDestinoKey, List<MaterialEntregaItem>> materialesPorDestino,
        Map<EntregaDestinoKey, Integer>                   volumenPorDestino
    ) { }

    public Resultado agrupar(List<Equipo> equipos, List<EquipoOtros> equiposOtros) {
        Map<EntregaDestinoKey, InstitucionAcumulador>     destinos             = new LinkedHashMap<>();
        Map<EntregaDestinoKey, List<MaterialEntregaItem>> materialesPorDestino = new HashMap<>();
        Map<EntregaDestinoKey, Integer>                   volumenPorDestino    = new HashMap<>();

        for (Equipo equipo : equipos) {
            if (!estadoValidator.esEntregable(equipo.calcularEstado())) continue;

            List<MaterialEntregaItem> materiales = materialesDe(equipo);
            if (materiales.isEmpty()) continue;

            int institucionId = equipo.getNroInstitucion() != null ? equipo.getNroInstitucion() : -1;
            EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.INSTITUCION, institucionId);
            destinos.computeIfAbsent(key, k -> new InstitucionAcumulador(k, nombreInstitucion(equipo)))
                    .agregarEquipo(equipo.getId());
            materialesPorDestino.computeIfAbsent(key, k -> new ArrayList<>()).addAll(materiales);
        }

        for (EquipoOtros equipo : equiposOtros) {
            if (!estadoValidator.esEntregable(equipo.calcularEstado())) continue;

            List<MaterialEntregaItem> materiales = materialesDeOtros(equipo);
            if (materiales.isEmpty()) continue;

            EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.CLIENTE, equipo.getNroCliente());
            destinos.computeIfAbsent(key, k -> new InstitucionAcumulador(k, nombreCliente(equipo)))
                    .agregarEquipo(equipo.getId());
            materialesPorDestino.computeIfAbsent(key, k -> new ArrayList<>()).addAll(materiales);
            volumenPorDestino.merge(key, equipo.getVolumenEquipo(), Integer::sum);
        }

        List<InstitucionEntregaItem> filas = destinos.values().stream()
            .sorted(Comparator.comparing(InstitucionAcumulador::getNombre, String.CASE_INSENSITIVE_ORDER))
            .map(ac -> new InstitucionEntregaItem(ac.getKey(), ac.getNombre(), ac.getEquiposCount()))
            .toList();

        return new Resultado(filas, materialesPorDestino, volumenPorDestino);
    }

    /** Materiales de ortopedia agrupados por código, descontando lo ya entregado. */
    private List<MaterialEntregaItem> materialesDe(Equipo equipo) {
        List<MaterialEntregaItem> materiales = new ArrayList<>();
        if (equipo.getMateriales() == null) return materiales;

        Map<Integer, MaterialAgrupado> agrupados = new LinkedHashMap<>();
        for (Material material : equipo.getMateriales()) {
            if (!estadoValidator.esEntregable(material.getEstado())) continue;

            MaterialAgrupado agrupado = agrupados.computeIfAbsent(
                material.getCodigo(), codigo -> new MaterialAgrupado(material.getDescripcion()));
            agrupado.agregar(material.getCantidad(), material.getEstado() == EstadoEquipo.ENTREGADO);
        }

        for (MaterialAgrupado agrupado : agrupados.values()) {
            if (agrupado.todosEntregados()) continue;
            materiales.add(new MaterialEntregaItem(
                agrupado.getDescripcion(),
                agrupado.getCantidadTotal() - agrupado.getCantidadEntregada(),
                false));
        }
        return materiales;
    }

    /** Materiales de "otros": un REMITO sin filas reales rinde una sola fila "Elementos". */
    private List<MaterialEntregaItem> materialesDeOtros(EquipoOtros equipo) {
        List<MaterialEntregaItem> resultado = new ArrayList<>();
        List<MaterialOtros> mats = equipo.getMateriales();

        if (mats.isEmpty()) {
            if (equipo.getRemitoCantidad() != null && equipo.getRemitoCantidad() > 0) {
                resultado.add(new MaterialEntregaItem("Elementos", equipo.getRemitoCantidad(), false));
            }
            return resultado;
        }

        Map<String, int[]> agrupados = new LinkedHashMap<>();
        for (MaterialOtros material : mats) {
            if (!estadoValidator.esEntregable(material.getEstado())) continue;
            int[] contadores = agrupados.computeIfAbsent(material.getDescripcion(), k -> new int[2]);
            contadores[0] += material.getCantidad();
            if (material.getEstado() == EstadoEquipo.ENTREGADO) contadores[1] += material.getCantidad();
        }
        for (Map.Entry<String, int[]> entrada : agrupados.entrySet()) {
            int pendiente = entrada.getValue()[0] - entrada.getValue()[1];
            if (pendiente > 0) resultado.add(new MaterialEntregaItem(entrada.getKey(), pendiente, false));
        }
        return resultado;
    }

    private static String nombreInstitucion(Equipo equipo) {
        String nombre = equipo.getInstitucionNombre();
        return (nombre == null || nombre.isBlank()) ? Constantes.Textos.SIN_INSTITUCION : nombre;
    }

    private static String nombreCliente(EquipoOtros equipo) {
        return equipo.getClienteNombre() != null ? equipo.getClienteNombre() : SIN_CLIENTE;
    }
}
