package com.example.common.eliminacion;

import com.example.features.lavadero.model.EstadoIngresoLavadero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * El resumen previo de un ingreso de Lavadero.
 *
 * <p><b>El token de guarda son dos cosas, no una</b>: {@link #estado()} y
 * {@link #versionesDerivados()}. {@code ingresos_lavadero} no tiene {@code version} (y no se le
 * agrega: su guarda natural es la máquina de estados persistida). Pero el estado solo no alcanza:
 * una derivación parcial crea un {@code equipo_otros} nuevo sin mover el estado del ingreso —sigue
 * {@code LAVADO} hasta que sale todo—, y el operador confirmaría un borrado que se lleva un ingreso
 * del CDE que no vio. Y de cada derivado viaja además su {@code version}, con el mismo criterio que
 * el borrado desde Ver Equipos: si otro puesto lo avanzó, lo entregó o lo corrigió después del
 * resumen, se borraría en un estado que el operador no confirmó. Si cualquier parte del token cambió
 * desde el resumen, el borrado es conflicto.</p>
 *
 * @param ingreso       módulo {@code LAVADERO}
 * @param clienteNombre nullable
 * @param fechaIngreso  nullable
 * @param estado        el estado que vio el operador; <b>viaja como guarda</b>
 * @param pesoTotalKg   el peso declarado al ingresar
 * @param elementos     lo que se clasificó; vacío si el ingreso todavía está {@code PENDIENTE}
 * @param derivados     los ingresos del CDE creados con ropa de este ingreso, que se borran con él;
 *                      <b>sus ids y sus versiones viajan como guarda</b>
 * @param bloqueos      {@link Bloqueo.CicloEnCurso}, {@link Bloqueo.DerivadoCompartido} y
 *                      {@link Bloqueo.DerivadoEnLoteEnCurso}
 */
public record ResumenIngresoLavadero(
    IngresoAEliminar ingreso,
    String clienteNombre,
    LocalDateTime fechaIngreso,
    EstadoIngresoLavadero estado,
    BigDecimal pesoTotalKg,
    List<LineaElemento> elementos,
    List<DerivadoCde> derivados,
    List<Bloqueo> bloqueos
) implements ResumenEliminacion {

    public ResumenIngresoLavadero {
        Objects.requireNonNull(ingreso, "ingreso");
        Objects.requireNonNull(estado, "estado");
        if (ingreso.modulo() != ModuloIngreso.LAVADERO) {
            throw new IllegalArgumentException("Un ingreso del CDE se resume con ResumenEquipo");
        }
        elementos = List.copyOf(elementos);
        derivados = List.copyOf(derivados);
        bloqueos = List.copyOf(bloqueos);
    }

    /**
     * La otra mitad del token de guarda: los {@code equipo_otros} que el operador vio, cada uno con
     * la {@code version} que tenía en el resumen.
     */
    public Map<Integer, Integer> versionesDerivados() {
        return derivados.stream()
            .collect(Collectors.toUnmodifiableMap(DerivadoCde::equipoOtrosId, DerivadoCde::version));
    }

    /** Una línea de la clasificación: el elemento y cuánto se clasificó. */
    public record LineaElemento(String nombre, int cantidad) {}

    /**
     * Un ingreso del CDE ({@code equipo_otros}) que se creó al derivar ropa de este ingreso.
     *
     * @param estado   el nombre del {@code EstadoEquipo} de su cabecera
     * @param unidades la suma de las cantidades de sus materiales
     * @param version  la {@code version} de su cabecera; <b>viaja como guarda</b>
     */
    public record DerivadoCde(int equipoOtrosId, String estado, int unidades, int version) {}
}
