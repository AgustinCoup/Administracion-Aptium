package com.example.features.equipos.ortopedias.controller.helpers;

import com.example.common.constants.Constantes;
import com.example.common.model.EntregaDestinoKey;
import com.example.common.model.EntregaDestinoKey.TipoDestino;
import com.example.common.model.FilaAEntregar;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.common.model.RemitoAEntregar;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.view.helpers.InstitucionEntregaItem;
import com.example.features.equipos.ortopedias.view.helpers.MaterialEntregaItem;
import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.MaterialOtros;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Arma la vista de "equipos para entregar" a partir de un listado de equipos.
 *
 * <p>Ortopedias se agrupan por institución y "otros" por cliente, en dos grupos
 * de destinos que conviven en la misma tabla. Es lógica pura sin Swing ni base
 * de datos: entra el snapshot, sale lo que la pantalla tiene que mostrar.
 *
 * <p><b>Qué entra.</b> Sólo las filas {@code ESTERILIZADO}: las {@code ENTREGADO} no participan.
 * Un equipo de ortopedias o de "otros" DETALLES entra si tiene al menos una, esté completo o no
 * — si no lo está, su ingreso lleva {@link Constantes.Textos#INGRESO_INCOMPLETO}, porque sin marca
 * el operador entregaría medio equipo creyendo que era "lo listo". Un REMITO entra <b>sólo</b>
 * completo, y como <b>un</b> ítem: se entrega siempre entero.
 *
 * <p>Cada ítem lleva las filas que se entregan si se lo elige (ver {@link MaterialEntregaItem}).
 */
public class AgrupadorEntregas {

    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern(Constantes.Formatos.FORMATO_FECHA);

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
            List<MaterialEntregaItem> materiales = materialesDe(equipo);
            if (materiales.isEmpty()) continue;

            int institucionId = equipo.getNroInstitucion() != null ? equipo.getNroInstitucion() : -1;
            EntregaDestinoKey key = new EntregaDestinoKey(TipoDestino.INSTITUCION, institucionId);
            destinos.computeIfAbsent(key, k -> new InstitucionAcumulador(k, nombreInstitucion(equipo)))
                    .agregarEquipo(equipo.getId());
            materialesPorDestino.computeIfAbsent(key, k -> new ArrayList<>()).addAll(materiales);
        }

        for (EquipoOtros equipo : equiposOtros) {
            List<MaterialEntregaItem> materiales = equipo.getTipoIngreso() == TipoIngresoOtros.REMITO
                ? remito(equipo)
                : materialesDeOtros(equipo);
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

    /** Ortopedias: un ítem por código, con todas sus filas {@code ESTERILIZADO}. */
    private List<MaterialEntregaItem> materialesDe(Equipo equipo) {
        if (equipo.getMateriales() == null) return List.of();
        String ingreso = marcarSiIncompleto(ingresoOrtopedia(equipo), equipo.calcularEstado());
        return itemsPorGrupo(equipo.getId(), equipo.getMateriales(), Material::getCodigo, ingreso);
    }

    /** "Otros" DETALLES: un ítem por descripción, con todas sus filas {@code ESTERILIZADO}. */
    private List<MaterialEntregaItem> materialesDeOtros(EquipoOtros equipo) {
        String ingreso = marcarSiIncompleto(fecha(equipo.getFechaIngreso()), equipo.calcularEstado());
        return itemsPorGrupo(equipo.getId(), equipo.getMateriales(), MaterialOtros::getDescripcion, ingreso);
    }

    /**
     * Agrupa por {@code clave} las filas {@code ESTERILIZADO}, en el orden en que aparece cada
     * clave. Cada grupo es un ítem con <b>todas</b> sus filas: pueden ser varias si la misma clave
     * vino de lotes distintos.
     */
    private static <M extends MaterialRegistrableInterface> List<MaterialEntregaItem> itemsPorGrupo(
            int equipoId, List<M> materiales, Function<M, Object> clave, String ingreso) {
        Map<Object, List<M>> grupos = new LinkedHashMap<>();
        for (M material : materiales) {
            if (material.getEstado() != EstadoEquipo.ESTERILIZADO) continue;
            grupos.computeIfAbsent(clave.apply(material), k -> new ArrayList<>()).add(material);
        }

        List<MaterialEntregaItem> items = new ArrayList<>();
        for (List<M> grupo : grupos.values()) {
            List<FilaAEntregar> filas = grupo.stream().map(m -> fila(equipoId, m)).toList();
            items.add(new MaterialEntregaItem(ingreso, grupo.get(0).getDescripcion(),
                sumar(filas), filas, List.of()));
        }
        return items;
    }

    /**
     * REMITO: un solo ítem, y sólo si está todo {@code ESTERILIZADO}. Sin filas reales viaja como
     * {@link RemitoAEntregar} con la cantidad del remito; con filas, lleva todas las esterilizadas
     * (las ya entregadas no participan, igual que en los demás equipos).
     */
    private List<MaterialEntregaItem> remito(EquipoOtros equipo) {
        if (equipo.calcularEstado() != EstadoEquipo.ESTERILIZADO) return List.of();
        String ingreso = String.format(Constantes.Textos.INGRESO_REMITO,
            equipo.getRemitoId() != null ? equipo.getRemitoId() : Constantes.Textos.SIN_DATO);

        List<MaterialOtros> materiales = equipo.getMateriales();
        if (materiales.isEmpty()) {
            Integer cantidad = equipo.getRemitoCantidad();
            if (cantidad == null || cantidad <= 0) return List.of();
            return List.of(new MaterialEntregaItem(ingreso, Constantes.Textos.MATERIAL_REMITO, cantidad,
                List.of(), List.of(new RemitoAEntregar(equipo.getId()))));
        }

        List<FilaAEntregar> filas = materiales.stream()
            .filter(m -> m.getEstado() == EstadoEquipo.ESTERILIZADO)
            .map(m -> fila(equipo.getId(), m))
            .toList();
        if (filas.isEmpty()) return List.of();
        return List.of(new MaterialEntregaItem(ingreso, Constantes.Textos.MATERIAL_REMITO, sumar(filas),
            filas, List.of()));
    }

    private static FilaAEntregar fila(int equipoId, MaterialRegistrableInterface material) {
        return new FilaAEntregar(equipoId, material.getId(), material.getCantidad());
    }

    private static int sumar(List<FilaAEntregar> filas) {
        return filas.stream().mapToInt(FilaAEntregar::cantidadVista).sum();
    }

    private static String ingresoOrtopedia(Equipo equipo) {
        String paciente = equipo.getPacienteNombre();
        String fecha = fecha(equipo.getFechaIngreso());
        return (paciente == null || paciente.isBlank())
            ? fecha
            : String.format(Constantes.Textos.INGRESO_PACIENTE_FECHA, paciente, fecha);
    }

    /** Un equipo con materiales todavía en proceso entrega sólo una parte: se marca. */
    private String marcarSiIncompleto(String ingreso, EstadoEquipo estadoEquipo) {
        return estadoValidator.esEntregable(estadoEquipo)
            ? ingreso
            : ingreso + Constantes.Textos.INGRESO_INCOMPLETO;
    }

    private static String fecha(LocalDateTime fecha) {
        return fecha != null ? fecha.format(FORMATO_FECHA) : Constantes.Textos.SIN_DATO;
    }

    private static String nombreInstitucion(Equipo equipo) {
        String nombre = equipo.getInstitucionNombre();
        return (nombre == null || nombre.isBlank()) ? Constantes.Textos.SIN_INSTITUCION : nombre;
    }

    private static String nombreCliente(EquipoOtros equipo) {
        return equipo.getClienteNombre() != null ? equipo.getClienteNombre() : Constantes.Textos.SIN_CLIENTE;
    }
}
