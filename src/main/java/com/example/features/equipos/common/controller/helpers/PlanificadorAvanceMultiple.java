package com.example.features.equipos.common.controller.helpers;

import com.example.common.constants.Constantes;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.MovimientoMaterial;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Decide si los materiales seleccionados en Registrar Estado se pueden avanzar juntos, qué muestra
 * el botón Avanzar y qué movimientos se encolan. Es la regla que antes vivía repartida entre
 * {@code actualizarTextoAvanzar} y {@code avanzarMaterialSeleccionado} del controller, para un solo
 * material; acá es la misma para 1 y para N, y el controller sólo la consulta.
 *
 * <p><b>Por qué "mismo estado" alcanza.</b> La tabla muestra materiales de <b>un</b> equipo, y
 * {@link EquipoRegistrableInterface#getSiguienteEstado} depende sólo del estado y de los flags de
 * lavado/empaque de ese equipo: materiales en el mismo estado tienen siempre el mismo siguiente.
 *
 * <p><b>Por qué "tocada" se compara contra el equipo original.</b> El buffer no alcanza para saber
 * qué filas cambió un preview. Uno parcial le suma cantidad a otra fila persistida que ya estaba en
 * el destino, y {@code unificarEnMemoria} puede quedarse con esa otra fila y no con la del buffer
 * (ver {@code Equipo.aplicarMovimientoPreview}). Esa fila queda persistida, fuera del buffer y con la
 * cantidad inflada: avanzarla completa manda una cantidad que la base no tiene, y
 * {@code aplicarMovimientos} tira el equipo entero con "Cantidad inválida". Comparar estado y
 * cantidad contra la fila con el mismo id del equipo original —el del snapshot, que los previews no
 * tocan— la detecta. Mientras un equipo no tiene cambios, original y visible son el mismo objeto y
 * esa comparación nunca da distinto.
 *
 * <p><b>Por qué los movimientos se arman antes de aplicar ningún preview.</b> Cada preview muta el
 * equipo visible: parte filas, agrega filas sin id y saca filas al unificar. Si el preview del
 * primer material se aplicara antes de armar el movimiento del segundo, éste leería una cantidad o
 * un estado que ya no son los que el operador vio. Por eso {@link #movimientosCompletos} y
 * {@link #movimientosConCantidades} devuelven <b>todos</b> los movimientos juntos, y el llamador
 * aplica los previews recién después.
 *
 * <p>Sin Swing: se testea en aislamiento, como {@link AplicadorMovimientosPendientes}.
 */
public final class PlanificadorAvanceMultiple {

    /**
     * Lo que el planificador necesita saber de la pantalla, leído en el EDT.
     *
     * @param original         equipo tal como vino en el snapshot; nunca tiene previews aplicados
     * @param visible          equipo que muestra la tabla ({@code null} = ninguno seleccionado).
     *                         Es {@code original} mismo cuando el equipo no tiene cambios pendientes
     * @param seleccion        materiales seleccionados, en el orden de la tabla, tomados de {@code visible}
     * @param idsEnBuffer      ids de material de este equipo que ya tienen un movimiento en el buffer
     * @param escrituraEnCurso {@code true} mientras Confirmar está guardando
     */
    public record EntradaAvance(EquipoRegistrableInterface original,
                                EquipoRegistrableInterface visible,
                                List<MaterialRegistrableInterface> seleccion,
                                Set<Integer> idsEnBuffer,
                                boolean escrituraEnCurso) {
        public EntradaAvance {
            seleccion = List.copyOf(Objects.requireNonNull(seleccion, "seleccion"));
            idsEnBuffer = Set.copyOf(Objects.requireNonNull(idsEnBuffer, "idsEnBuffer"));
            if (visible != null) {
                Objects.requireNonNull(original, "original (es el mismo visible si no hay cambios)");
            }
        }
    }

    /**
     * Resultado de evaluar la selección. Cada caso dice qué hace el botón Avanzar, para que el
     * controller no calcule nada: lo oculta ({@link SinSeleccion}, {@link NoAvanzable}), lo muestra
     * deshabilitado con el motivo ({@link Bloqueado}) o lo habilita ({@link Avanzable}).
     *
     * <p>Las dos formas de "no se puede" son distintas a propósito: se <b>oculta</b> cuando no hay
     * nada que el operador pueda cambiar en la selección (el estado se avanza desde Lotes, o es
     * final), y se <b>deshabilita con motivo</b> cuando sí puede corregirla o sólo tiene que esperar.
     */
    public sealed interface EvaluacionAvance {

        boolean botonVisible();

        boolean botonHabilitado();

        String textoBoton();

        /** Nada seleccionado, o ningún equipo elegido. */
        record SinSeleccion() implements EvaluacionAvance {
            @Override public boolean botonVisible()    { return false; }
            @Override public boolean botonHabilitado() { return false; }
            @Override public String  textoBoton()      { return Constantes.Textos.BOTON_SELECCIONE_MATERIAL; }
        }

        /** La selección no se puede avanzar, y {@code motivo} le dice al operador por qué. */
        record Bloqueado(String motivo) implements EvaluacionAvance {
            public Bloqueado {
                Objects.requireNonNull(motivo, "motivo");
            }

            @Override public boolean botonVisible()    { return true; }
            @Override public boolean botonHabilitado() { return false; }
            @Override public String  textoBoton()      { return motivo; }
        }

        /**
         * El estado común no se avanza a mano (lo maneja Lotes) o es final. El botón está oculto;
         * el texto vuelve al genérico para que un "Pasar a" viejo no reaparezca si otro camino lo
         * mostrara sin pasar por acá.
         */
        record NoAvanzable() implements EvaluacionAvance {
            @Override public boolean botonVisible()    { return false; }
            @Override public boolean botonHabilitado() { return false; }
            @Override public String  textoBoton()      { return Constantes.Textos.BOTON_SELECCIONE_MATERIAL; }
        }

        /** Todos los {@code materiales} pasan a {@code siguiente}; vienen en el orden de la tabla. */
        record Avanzable(EstadoEquipo siguiente, List<MaterialRegistrableInterface> materiales)
                implements EvaluacionAvance {
            public Avanzable {
                Objects.requireNonNull(siguiente, "siguiente");
                materiales = List.copyOf(materiales);
                if (materiales.isEmpty()) {
                    throw new IllegalArgumentException("Un avance necesita al menos un material");
                }
            }

            @Override public boolean botonVisible()    { return true; }
            @Override public boolean botonHabilitado() { return true; }

            /** Qué diálogos hacen falta para elegir las cantidades; ver {@link ModoCantidades}. */
            public ModoCantidades modoCantidades() {
                if (materiales.size() == 1) {
                    return ModoCantidades.UN_MATERIAL;
                }
                return materiales.stream().allMatch(m -> m.getCantidad() == 1)
                    ? ModoCantidades.COMPLETOS_SIN_PREGUNTAR
                    : ModoCantidades.PREGUNTAR_SI_COMPLETOS;
            }

            @Override
            public String textoBoton() {
                return materiales.size() == 1
                    ? String.format(Constantes.Textos.BOTON_PASAR_A, siguiente.getNombre())
                    : String.format(Constantes.Textos.BOTON_PASAR_N_A, materiales.size(), siguiente.getNombre());
            }
        }
    }

    /**
     * Cómo se eligen las cantidades de un {@link EvaluacionAvance.Avanzable}.
     *
     * <ul>
     *   <li>{@link #UN_MATERIAL}: directo al diálogo de cantidad, como siempre. La pregunta "¿todos
     *       completos?" sobraría: ese diálogo ya tiene el check "Todos".</li>
     *   <li>{@link #COMPLETOS_SIN_PREGUNTAR}: varios, todos de cantidad 1. No hay nada que elegir,
     *       igual que con un material de cantidad 1 hoy; la confirmación real es Confirmar.</li>
     *   <li>{@link #PREGUNTAR_SI_COMPLETOS}: varios y alguno con más de 1. Primero "¿todos
     *       completos?" y, con No, un diálogo de cantidad por material.</li>
     * </ul>
     */
    public enum ModoCantidades { UN_MATERIAL, COMPLETOS_SIN_PREGUNTAR, PREGUNTAR_SI_COMPLETOS }

    private static final EvaluacionAvance SIN_SELECCION = new EvaluacionAvance.SinSeleccion();
    private static final EvaluacionAvance NO_AVANZABLE  = new EvaluacionAvance.NoAvanzable();

    private final IEstadoValidator estadoValidator;

    public PlanificadorAvanceMultiple(IEstadoValidator estadoValidator) {
        this.estadoValidator = Objects.requireNonNull(estadoValidator, "estadoValidator");
    }

    /**
     * Evalúa la selección. Las reglas van <b>en este orden</b>, que decide qué motivo ve el operador
     * cuando fallan varias: vacía → escritura en curso → tocada por un preview → estados distintos →
     * no avanzable → avanzable.
     *
     * <p>"Tocada" va antes que "estados distintos" porque nombra un material concreto, que es lo más
     * accionable, y porque una fila recién partida por un preview está siempre en otro estado que sus
     * hermanas: con el orden inverso el operador leería "estados distintos" en vez de "esta fila ya
     * tiene un cambio sin confirmar".
     */
    public EvaluacionAvance evaluar(EntradaAvance entrada) {
        List<MaterialRegistrableInterface> seleccion = entrada.seleccion();
        if (entrada.visible() == null || seleccion.isEmpty()) {
            return SIN_SELECCION;
        }
        if (entrada.escrituraEnCurso()) {
            return new EvaluacionAvance.Bloqueado(Constantes.Textos.AVANCE_BLOQUEADO_GUARDANDO);
        }
        for (MaterialRegistrableInterface material : seleccion) {
            if (estaTocado(material, entrada)) {
                return new EvaluacionAvance.Bloqueado(
                    String.format(Constantes.Textos.AVANCE_BLOQUEADO_TOCADO, material.getDescripcion()));
            }
        }

        EstadoEquipo actual = seleccion.get(0).getEstado();
        if (seleccion.stream().anyMatch(m -> m.getEstado() != actual)) {
            return new EvaluacionAvance.Bloqueado(Constantes.Textos.AVANCE_BLOQUEADO_ESTADOS_DISTINTOS);
        }

        EstadoEquipo siguiente = entrada.visible().getSiguienteEstado(actual);
        if (siguiente == null || !estadoValidator.esAvanzableManualmente(actual, siguiente)) {
            return NO_AVANZABLE;
        }
        return new EvaluacionAvance.Avanzable(siguiente, seleccion);
    }

    /** Un movimiento por material con su cantidad entera, en el orden de la selección. */
    public List<MovimientoMaterial> movimientosCompletos(EvaluacionAvance.Avanzable avance) {
        List<MovimientoMaterial> movimientos = new ArrayList<>();
        for (MaterialRegistrableInterface material : avance.materiales()) {
            movimientos.add(movimiento(material, material.getCantidad(), avance.siguiente()));
        }
        return List.copyOf(movimientos);
    }

    /**
     * Un movimiento por material con la cantidad que eligió el operador, en el orden de la selección.
     *
     * @throws IllegalArgumentException si falta la cantidad de algún material o alguna está fuera de
     *         {@code 1..getCantidad()}. Es un bug del llamador, no un error del operador: el spinner
     *         del diálogo ya acota el rango.
     */
    public List<MovimientoMaterial> movimientosConCantidades(EvaluacionAvance.Avanzable avance,
                                                             Map<Integer, Integer> cantidadPorMaterialId) {
        List<MovimientoMaterial> movimientos = new ArrayList<>();
        for (MaterialRegistrableInterface material : avance.materiales()) {
            Integer cantidad = cantidadPorMaterialId.get(material.getId());
            if (cantidad == null) {
                throw new IllegalArgumentException("Falta la cantidad del material id=" + material.getId());
            }
            if (cantidad < 1 || cantidad > material.getCantidad()) {
                throw new IllegalArgumentException(String.format(
                    "Cantidad %d fuera de 1..%d para el material id=%d",
                    cantidad, material.getCantidad(), material.getId()));
            }
            movimientos.add(movimiento(material, cantidad, avance.siguiente()));
        }
        return List.copyOf(movimientos);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * No persistida, con un movimiento ya en el buffer, o distinta de su fila en el original (la
     * inflada por un preview sin estar en el buffer; ver el javadoc de la clase). El material
     * sintético del REMITO sin filas tiene id 0, así que cuenta como persistido.
     */
    private static boolean estaTocado(MaterialRegistrableInterface material, EntradaAvance entrada) {
        if (!material.esPersistido() || entrada.idsEnBuffer().contains(material.getId())) {
            return true;
        }
        MaterialRegistrableInterface enOriginal = buscarPorId(entrada.original(), material.getId());
        return enOriginal == null
            || enOriginal.getEstado() != material.getEstado()
            || enOriginal.getCantidad() != material.getCantidad();
    }

    private static MaterialRegistrableInterface buscarPorId(EquipoRegistrableInterface equipo, Integer id) {
        for (MaterialRegistrableInterface material : equipo.getMaterialesRegistrables()) {
            if (id.equals(material.getId())) {
                return material;
            }
        }
        return null;
    }

    private static MovimientoMaterial movimiento(MaterialRegistrableInterface material, int cantidad,
                                                 EstadoEquipo siguiente) {
        return new MovimientoMaterial(material.getId(), cantidad, material.getEstado(), siguiente);
    }
}
