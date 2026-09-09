package com.example.features.lavadero.controller.helpers;

import com.example.features.lavadero.model.CicloLavadero;
import com.example.features.lavadero.model.ElementoCicloItem;
import com.example.features.lavadero.model.Lavarropas;
import com.example.features.lavadero.view.helpers.LavarropasItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Arma qué muestra la pantalla de Ciclos combinando lo leído de la base
 * ({@link DatosCiclos}) con el staging en memoria.
 *
 * <p>Existe para que esa combinación —el descuento de lo staged sobre los disponibles, el
 * mapeo de lavarropas y la decisión activo/staging de cada card— se pueda testear sin EDT
 * ni base, igual que {@code AgrupadorEntregas} y {@code ConstructorMaterialesDisponibles}.
 *
 * <p><b>Corre en el hilo de la interfaz</b>, no en el de fondo: toca {@link StagingCiclos},
 * que es el estado mutable que también leen el arrastre y los diálogos de subdivisión. Lo
 * único que se hace en fondo es traer los {@link DatosCiclos}.
 */
public final class ConstructorVistaCiclos {

    /**
     * Qué muestra una card: los elementos del ciclo en curso, o los pendientes que el
     * operador cargó en el staging.
     *
     * @param cicloActivoId id del ciclo en curso, o {@code null} si el lavarropas está libre
     * @param fracciones    denominador de la columna {@code Fracción} ({@code 1/3}); vacío en
     *                      las cards activas, cuyos ítems vienen de la base y no del staging
     */
    public record VistaCard(int lavarropasNumero,
                            Integer cicloActivoId,
                            List<ElementoCicloItem> items,
                            Map<Integer, Integer> fracciones) {

        public boolean esActivo() {
            return cicloActivoId != null;
        }
    }

    /**
     * La pantalla entera lista para volcar.
     *
     * @param hayPendientes       habilita "Lanzar todos" y "Descartar todos"
     * @param hayActivos          habilita "Finalizar todos"
     * @param stagingDescartadoDe lavarropas que perdieron su staging porque quedaron ocupados, y
     *                            los que lo perdieron de arrastre por compartir un equipo
     *                            repartido con ellos. Vacío en el caso normal; cuando no lo está
     *                            hay que <b>avisarlo</b>, porque es trabajo del operador que
     *                            desaparece de la pantalla sin que él haya hecho nada (ver
     *                            {@link #descartarStagingDeLavarropasOcupados})
     */
    public record VistaCiclos(List<ElementoCicloItem> disponibles,
                              List<LavarropasItem> lavarropas,
                              List<VistaCard> cards,
                              boolean hayPendientes,
                              boolean hayActivos,
                              List<Integer> stagingDescartadoDe) {
    }

    private ConstructorVistaCiclos() {
    }

    /**
     * @param numerosDeCard lavarropas que la pantalla tiene dibujados, en el orden en que
     *                      se van a pintar
     */
    public static VistaCiclos construir(DatosCiclos datos,
                                        Collection<Integer> numerosDeCard,
                                        StagingCiclos staging) {
        List<Integer> descartados = descartarStagingDeLavarropasOcupados(datos, staging);

        // Muta los ítems recibidos y devuelve los que todavía tienen unidades libres
        // (ver la nota de clase de StagingCiclos).
        List<ElementoCicloItem> disponibles = staging.aplicarSobreDisponibles(datos.disponibles());

        return new VistaCiclos(
            disponibles,
            mapearLavarropas(datos),
            armarCards(datos, numerosDeCard, staging),
            staging.hayPendientes(),
            !datos.ciclosActivos().isEmpty(),
            descartados
        );
    }

    /**
     * Descarta el staging de los lavarropas que volvieron de la base con ciclo activo.
     *
     * <p>Un lavarropas se puede ocupar entre que el operador le arrastró ropa y el refresco
     * siguiente: otro operador lanzó su ciclo. La card ya deja de mostrar ese staging
     * ({@link #armarCards} pinta los ítems del ciclo activo), pero el staging seguía vivo, y
     * "Lanzar todos" lo mandaba igual — un segundo ciclo ACTIVO en el mismo lavarropas, que la
     * pantalla ni siquiera puede mostrar (ver la guarda de
     * {@code CicloLavaderoDAO.SQL_CICLO_ACTIVO_DE_LAVARROPAS}, que ahora lo rechaza en la base).</p>
     *
     * <p>Va <b>antes</b> de descontar los disponibles: así esa ropa deja de estar descontada y
     * vuelve a aparecer en la tabla, que es donde el operador la puede volver a repartir. No se
     * pierde nada — lo único que se descarta es la asignación a un lavarropas que ya no está
     * libre.</p>
     *
     * <p>Si lo que había ahí era la fracción de un equipo repartido se deshace la subdivisión
     * <b>entera</b>, en todos los lavarropas — incluidos los que siguen libres. Es la misma regla
     * que {@code StagingCiclos.quitar} aplica a la devolución manual, y por el mismo motivo: dejar
     * 2 de 3 fracciones cambia en silencio el reparto que el operador armó. Justamente porque el
     * daño se propaga más allá del lavarropas ocupado, esta baja <b>no puede ser silenciosa</b>:
     * se devuelve la lista de lavarropas afectados para que {@code CiclosController} la avise.</p>
     *
     * @return los lavarropas que efectivamente perdieron staging, en orden ascendente; vacío si no
     *         hubo nada que descartar, que es el caso normal
     */
    private static List<Integer> descartarStagingDeLavarropasOcupados(DatosCiclos datos,
                                                                      StagingCiclos staging) {
        Set<Integer> afectados = new TreeSet<>();
        for (int ocupado : datos.ciclosActivos().keySet()) {
            List<ElementoCicloItem> pendientes = staging.pendientesDe(ocupado);
            if (pendientes.isEmpty()) continue;
            afectados.add(ocupado);
            for (ElementoCicloItem item : pendientes) {
                if (item.isEquipo() && item.getInstanciaId() != null) {
                    afectados.addAll(staging.lavarropasConFraccionesDe(item.getInstanciaId()));
                    staging.quitarInstanciaEquipo(item.getInstanciaId());
                }
            }
            staging.limpiarLavarropas(ocupado);
        }
        return List.copyOf(afectados);
    }

    private static List<LavarropasItem> mapearLavarropas(DatosCiclos datos) {
        List<LavarropasItem> items = new ArrayList<>();
        for (Lavarropas lavarropas : datos.lavarropas()) {
            CicloLavadero activo = datos.ciclosActivos().get(lavarropas.getNumero());
            items.add(new LavarropasItem(lavarropas.getNumero(), lavarropas.getCapacidadLitros(),
                                         activo != null, activo != null ? activo.getId() : null));
        }
        return items;
    }

    private static List<VistaCard> armarCards(DatosCiclos datos,
                                              Collection<Integer> numerosDeCard,
                                              StagingCiclos staging) {
        Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
        List<VistaCard> cards = new ArrayList<>();
        for (int numero : numerosDeCard) {
            CicloLavadero activo = datos.ciclosActivos().get(numero);
            if (activo != null) {
                cards.add(new VistaCard(numero, activo.getId(),
                    datos.itemsPorLavarropasActivo().getOrDefault(numero, List.of()), Map.of()));
            } else {
                cards.add(new VistaCard(numero, null, staging.pendientesDe(numero), fracciones));
            }
        }
        return cards;
    }
}
